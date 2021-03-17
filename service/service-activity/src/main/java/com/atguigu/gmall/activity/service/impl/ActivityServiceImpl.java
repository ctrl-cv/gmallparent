package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.CouponUseMapper;
import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.activity.service.ActivityService;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponUse;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;

import com.atguigu.gmall.model.enums.CouponStatus;
import com.atguigu.gmall.model.enums.CouponType;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderDetailCoupon;
import com.atguigu.gmall.model.order.OrderDetailVo;
import com.atguigu.gmall.model.order.OrderTradeVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.redisson.misc.Hash;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ActivityServiceImpl implements ActivityService {
    @Resource
    ActivityInfoService activityInfoService;

    @Resource
    CouponInfoService couponInfoService;

    @Resource
    CouponUseMapper couponUseMapper;


    /**
     * 根据skuId获取促销与优惠券信息
     * @param skuId
     * @param userId
     * @return
     */
    @Override
    public Map<String, Object> findActivityAndCoupon(Long skuId, long userId) {
        List<ActivityRule> activityRule = activityInfoService.findActivityRule(skuId);
        Long activityId = null;
        if (!CollectionUtils.isEmpty(activityRule)){
            activityId = activityRule.get(0).getActivityId();
        }

        List<CouponInfo> couponInfo = couponInfoService.findCouponInfo(skuId, activityId, userId);
        Map<String, Object> map = new HashMap<>();
        map.put("activityRuleList", activityRule);
        map.put("couponInfoList",couponInfo);
        return map;
    }

    @Override
    public List<CarInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId) {
        Map<Long, Long> skuIdToActivityIdMap = new HashMap<>();

        List<CarInfoVo> carInfoVoList = activityInfoService.findCartActivityRuleMap(cartInfoList,skuIdToActivityIdMap);

        Map<Long, List<CouponInfo>> skuIdToCouponInfoListMap = couponInfoService.findCartCouponInfo(cartInfoList, skuIdToActivityIdMap, userId);

        List<CartInfo> noJoinCartInfoList = new ArrayList<>();
        for (CartInfo cartInfo : cartInfoList) {
            boolean flag = false;
            Iterator<Map.Entry<Long, Long>> iterator = skuIdToActivityIdMap.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<Long, Long> next = iterator.next();
                Long skuId = next.getKey();
                Long activityId = next.getValue();

                if (cartInfo.getSkuId().intValue() == skuId.intValue()){
                        flag = true;
                        break;
                }
            }
            if (!flag){
                    noJoinCartInfoList.add(cartInfo);
            }
        }
        if (!CollectionUtils.isEmpty(noJoinCartInfoList)){
            CarInfoVo carInfoVo = new CarInfoVo();
            carInfoVo.setCartInfoList(noJoinCartInfoList);
            carInfoVo.setActivityRuleList(null);

            carInfoVoList.add(carInfoVo);
        }

        for (CarInfoVo carInfoVo : carInfoVoList) {
            List<CartInfo> cartInfoList1 = carInfoVo.getCartInfoList();
            for (CartInfo cartInfo : cartInfoList1) {
                cartInfo.setCouponInfoList(skuIdToCouponInfoListMap.get(cartInfo.getSkuId()));
            }
        }
        return carInfoVoList;
    }

    @Override
    public OrderTradeVo findTradeActivityAndCoupon(List<OrderDetail> orderDetailList, Long userId) {
        /**
         * 促销活动处理，获取购物项最优的对应促销活动规则（如：满减 100减10 200减30 500减70，当前购物项金额250，那么最优为：200减30）
         */
        //记录购物项activityId对应的最优促销活动规则
        Map<Long, ActivityRule> activityIdToActivityRuleMap = activityInfoService.findTradeActivityRuleMap(orderDetailList);
        Map<Long, OrderDetail> skuIdToCartInfoMap = new HashMap<>();
        for (OrderDetail orderDetail : orderDetailList) {
            skuIdToCartInfoMap.put(orderDetail.getSkuId(),orderDetail);
        }
        //记录有活动的购物项sku
        List<Long> activitySkuId = new ArrayList<>();
        List<OrderDetailVo> orderDetailVoList = new ArrayList<>();
        BigDecimal activityReduceAmount = new BigDecimal("0");
        if (!CollectionUtils.isEmpty(activityIdToActivityRuleMap)){
            Iterator<Map.Entry<Long, ActivityRule>> iterator = activityIdToActivityRuleMap.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<Long, ActivityRule> entry = iterator.next();
                ActivityRule activityRule = entry.getValue();
                List<Long> skuIdList = activityRule.getSkuIdList();
                List<OrderDetail> detailList = new ArrayList<>();
                for (Long skuId : skuIdList) {
                    detailList.add(skuIdToCartInfoMap.get(skuId));
                }
                 activityReduceAmount = activityReduceAmount.add(activityRule.getReduceAmount());

                OrderDetailVo orderDetailVo = new OrderDetailVo();
                orderDetailVo.setActivityRule(activityRule);
                orderDetailVo.setOrderDetailList(detailList);
                orderDetailVoList.add(orderDetailVo);

                activitySkuId.addAll(skuIdList);
            }
        }
        //无活动的购物项
        List<OrderDetail> detailList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            if (!activitySkuId.contains(orderDetail.getSkuId())){
                detailList.add(skuIdToCartInfoMap.get(orderDetail.getSkuId()));
            }
        }
        OrderDetailVo orderDetailVo = new OrderDetailVo();
        orderDetailVo.setActivityRule(null);
        orderDetailVo.setOrderDetailList(detailList);
        orderDetailVoList.add(orderDetailVo);

        //优惠券处理，获取购物项能使用的优惠券
        List<CouponInfo> couponInfoList = couponInfoService.findTradeCouponInfo(orderDetailList,activityIdToActivityRuleMap,userId);
        OrderTradeVo orderTradeVo = new OrderTradeVo();
        orderTradeVo.setOrderDetailVoList(orderDetailVoList);
        orderTradeVo.setCouponInfoList(couponInfoList);
        orderTradeVo.setActivityReduceAmount(activityReduceAmount);
        return orderTradeVo;
    }

    @Override
    public void updateCouponInfoUseStatus(Long userId, Long orderId, Long couponId) {
        CouponUse couponUse = new CouponUse();
        couponUse.setOrderId(orderId);
        couponUse.setCouponStatus(CouponStatus.USE_RUN.name());
        couponUse.setUsingTime(new Date());

        QueryWrapper<CouponUse> couponUseQueryWrapper = new QueryWrapper<>();
        couponUseQueryWrapper.eq("coupon_id",couponId).eq("user_id",userId);
        couponUseMapper.update(couponUse,couponUseQueryWrapper);


    }
}
