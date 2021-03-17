package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.ActivityInfoMapper;
import com.atguigu.gmall.activity.mapper.ActivityRuleMapper;
import com.atguigu.gmall.activity.mapper.ActivitySkuMapper;
import com.atguigu.gmall.activity.mapper.CouponInfoMapper;
import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.model.activity.*;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.enums.ActivityType;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.misc.Hash;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ActivityInfoServiceImpl extends ServiceImpl<ActivityInfoMapper, ActivityInfo> implements ActivityInfoService {

    @Resource
    ActivityInfoMapper activityInfoMapper;

    @Resource
    ActivitySkuMapper activitySkuMapper;

    @Resource
    ActivityRuleMapper activityRuleMapper;

    @Resource
    ProductFeignClient productFeignClient;

    @Resource
    CouponInfoMapper couponInfoMapper;
    @Override
    public IPage<ActivityInfo> getPage(Page<ActivityInfo> pageParam) {
        QueryWrapper<ActivityInfo> activityInfoQueryWrapper = new QueryWrapper<>();
        activityInfoQueryWrapper.orderByDesc("id");
        IPage<ActivityInfo> infoPage = activityInfoMapper.selectPage(pageParam, activityInfoQueryWrapper);
        infoPage.getRecords().stream().forEach((item) -> {
            item.setActivityTypeString(ActivityType.getNameByType(item.getActivityType()));
        });
        return infoPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveActivityRule(ActivityRuleVo activityRuleVo) {
        activityRuleMapper.delete(new QueryWrapper<ActivityRule>().eq("activity_id",activityRuleVo.getActivityId()));
        activitySkuMapper.delete(new QueryWrapper<ActivitySku>().eq("activity_id",activityRuleVo.getActivityId()));

        List<ActivityRule> activityRuleList = activityRuleVo.getActivityRuleList();
        List<ActivitySku> activitySkuList = activityRuleVo.getActivitySkuList();
        List<Long> couponIdList = activityRuleVo.getCouponIdList();

        CouponInfo couponInfo = new CouponInfo();
        couponInfo.setActivityId(0L);
        QueryWrapper<CouponInfo> couponInfoQueryWrapper = new QueryWrapper<>();
        couponInfoQueryWrapper.eq("activity_id",activityRuleVo.getActivityId());
        couponInfoMapper.update(couponInfo,couponInfoQueryWrapper);
//        for (Long couponId : couponIdList) {
//            CouponInfo couponInfo = couponInfoMapper.selectById(couponId);
//            couponInfo.setActivityId(0L);
//            couponInfoMapper.updateById(couponInfo);
//        }

        for (ActivityRule activityRule : activityRuleList) {
            activityRule.setActivityId(activityRuleVo.getActivityId());
            activityRuleMapper.insert(activityRule);
        }

        for (ActivitySku activitySku : activitySkuList) {
            activitySku.setActivityId(activityRuleVo.getActivityId());
            activitySkuMapper.insert(activitySku);
        }

        if (!CollectionUtils.isEmpty(couponIdList)){
            for (Long couponId : couponIdList) {
                CouponInfo couponInfoUp = couponInfoMapper.selectById(couponId);
                couponInfoUp.setActivityId(activityRuleVo.getActivityId());
                couponInfoMapper.updateById(couponInfoUp);
            }

        }

    }

    @Override
    public List<SkuInfo> findSkuInfoByKeyword(String keyword) {
        List<SkuInfo> skuInfoList= productFeignClient.findSkuInfoByKeyword(keyword);
        List<Long> skuIdList = skuInfoList.stream().map(SkuInfo::getId).collect(Collectors.toList());

        List<Long> existSkuIdList = activityInfoMapper.selectExistSkuIdList(skuIdList);

        List<SkuInfo> skuInfos = existSkuIdList.stream().map(skuid ->
            productFeignClient.getSkuInfo(skuid))
        .collect(Collectors.toList());

        skuInfoList.removeAll(skuInfos);
        return skuInfoList;
    }

    @Override
    public Map findSkuInfoBySkuIdList(Long id) {
        HashMap<String, Object> map = new HashMap<>();
        QueryWrapper<ActivityRule> activityRuleQueryWrapper = new QueryWrapper<>();
        activityRuleQueryWrapper.eq("activity_id",id);
        List<ActivityRule> activityRules = activityRuleMapper.selectList(activityRuleQueryWrapper);
        map.put("activityRuleList",activityRules);

        QueryWrapper<ActivitySku> activitySkuQueryWrapper = new QueryWrapper<>();
        activitySkuQueryWrapper.eq("activity_id",id);
        List<ActivitySku> skuList = activitySkuMapper.selectList(activitySkuQueryWrapper);
        List<Long> skuIdList = skuList.stream().map(ActivitySku::getSkuId).collect(Collectors.toList());
        List<SkuInfo> skuInfoList = productFeignClient.findSkuInfoBySkuIdList(skuIdList);
        map.put("skuInfoList",skuInfoList);

        QueryWrapper<CouponInfo> activityInfoQueryWrapper = new QueryWrapper<>();
        activityInfoQueryWrapper.eq("activity_id",id);
        List<CouponInfo> couponInfoList = couponInfoMapper.selectList(activityInfoQueryWrapper);
        map.put("couponInfoList",couponInfoList);

        return map;
    }

    @Override
    public List<ActivityRule> findActivityRule(Long skuId) {
        return activityInfoMapper.selectActivityRuleList(skuId);
    }

    @Override
    public List<CarInfoVo> findCartActivityRuleMap(List<CartInfo> cartInfoList,Map<Long, Long> skuIdToActivityIdMap) {

        List<CarInfoVo> carInfoVoList = new ArrayList<>();
        Map<Long, CartInfo> skuIdToCartInfoMap = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            skuIdToCartInfoMap.put(cartInfo.getSkuId(),cartInfo);
        }
        //取出skuid集合
        List<Long> skuIdList = cartInfoList.stream().map(CartInfo::getSkuId).collect(Collectors.toList());
        //通过skuidlist查询活动规则
        if (CollectionUtils.isEmpty(skuIdList)) return new ArrayList<>();
        List<ActivityRule> activityRuleList = activityInfoMapper.selectCartActivityRuleList(skuIdList);
        //根据活动ID分组
        Map<Long, List<ActivityRule>> skuIdToActivityRuleListMap = activityRuleList.stream().collect(Collectors.groupingBy(activityRule -> activityRule.getSkuId()));
        //将活动根据活动id进行分组
        Map<Long, List<ActivityRule>> activityIdToActivityRuleListAllMap = activityRuleList.stream().collect(Collectors.groupingBy(activityRule -> activityRule.getActivityId()));
        // 寻找当前商品符合的活动
        Iterator<Map.Entry<Long, List<ActivityRule>>> iterator = activityIdToActivityRuleListAllMap.entrySet().iterator();

        while (iterator.hasNext()){
            Map.Entry<Long, List<ActivityRule>> next = iterator.next();
            Long activityId = next.getKey();
            List<ActivityRule> currentActivityRuleList = next.getValue();

            Set<Long> activitySkuIdSet = currentActivityRuleList.stream().map(activityRule -> activityRule.getSkuId()).collect(Collectors.toSet());

            CarInfoVo carInfoVo = new CarInfoVo();

            List<CartInfo> cartInfos = new ArrayList<>();
            for (Long skuId : activitySkuIdSet) {
                skuIdToActivityIdMap.put(skuId,activityId);
                CartInfo cartInfo = skuIdToCartInfoMap.get(skuId);
                cartInfos.add(cartInfo);
            }

            carInfoVo.setCartInfoList(cartInfos);
            carInfoVo.setActivityRuleList(skuIdToActivityRuleListMap.get(activitySkuIdSet.iterator().next()));
            carInfoVoList.add(carInfoVo);
        }
        return carInfoVoList;
    }

    @Override
    public Map<Long, ActivityRule> findTradeActivityRuleMap(List<OrderDetail> orderDetailList) {
        //购物项activityId对应的最优活动规则
        Map<Long,ActivityRule> activityIdToActivityRuleMap = new HashMap<>();
        //获取skuId对应的购物项
        Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
        //遍历循环
        for (OrderDetail orderDetail : orderDetailList) {
           skuIdToOrderDetailMap.put(orderDetail.getSkuId(),orderDetail);
        }
        //获取skuId列表
        List<Long> skuIdList = orderDetailList.stream().map(orderDetail -> orderDetail.getSkuId()).collect(Collectors.toList());
        //通过skuId列表获取活动规则
        List<ActivityRule> activityRuleList = activityInfoMapper.selectCartActivityRuleList(skuIdList);
        //根据skuId分组 获取skuid对应的活动规则
        Map<Long, List<ActivityRule>> skuIdToActivityRuleListMap = activityRuleList.stream().collect(Collectors.groupingBy(activityRule -> activityRule.getSkuId()));
        //根据活动ID分类  获取活动ID对应的sku
        Map<Long, List<ActivityRule>> activityIdToActivityRuleListMap = activityRuleList.stream().collect(Collectors.groupingBy(activityRule -> activityRule.getActivityId()));

        Iterator<Map.Entry<Long, List<ActivityRule>>> iterator = activityIdToActivityRuleListMap.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<Long, List<ActivityRule>> entry = iterator.next();
            //Long activityId = entry.getKey();
            List<ActivityRule> currentActivityRuleList = entry.getValue();
            //获取活动id下的购物车skuid列表
            Set<Long> activitySkuIdSet = currentActivityRuleList.stream().map(activityRule -> activityRule.getSkuId()).collect(Collectors.toSet());

            // 该活动的总金额 {如果是满减打折则使用activityTotalAmount}
            BigDecimal activityTotalAmount = new BigDecimal("0");

            // 该活动订单明细的个数{如果是满件打折的时候，需要判断activityTotalNum}
            Integer activityTotalNum = 0;

            for (Long skuId : activitySkuIdSet) {
                //  获取当前sku对应的活动id
                OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                //  订单明细的总金额
                BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                //  计算这个活动的总金额
                  activityTotalAmount = activityTotalAmount.add(skuTotalAmount);
                activityTotalNum += orderDetail.getSkuNum();

            }
            // 获取skuId 对应的该活动规则
            Long skuId = activitySkuIdSet.iterator().next();
            List<ActivityRule> skuActivityRuleList = skuIdToActivityRuleListMap.get(skuId);
            for (ActivityRule activityRule : skuActivityRuleList) {
                if (activityRule.getActivityType().equals(ActivityType.FULL_REDUCTION.name())){
                    if (activityTotalAmount.compareTo(activityRule.getConditionAmount()) > -1){
                        //  设置优惠后见减少的金额
                        activityRule.setReduceAmount(activityRule.getBenefitAmount());
                        //  设置好skuId 列表
                        activityRule.setSkuIdList(new ArrayList<>(activitySkuIdSet));
                        //  活动Id对应的最优规则
                        activityIdToActivityRuleMap.put(activityRule.getActivityId(),activityRule);
                        break;
                        }
                    }else {
                    //  如果订单项购买个数大于等于满减件数，则优化打折 FULL_DISCOUNT
                    if (activityTotalNum.intValue() >= activityRule.getConditionNum().intValue()){
                        //  9折  9/10 = 0.9  totalAmount *0.9
                        BigDecimal skuDiscountTotalAmount = activityTotalAmount.multiply(activityRule.getBenefitDiscount()).divide(new BigDecimal(10));
                        BigDecimal reduceAmount = activityTotalAmount.subtract(skuDiscountTotalAmount);
                        //  设置优惠后的金额reduceAmount
                        activityRule.setReduceAmount(reduceAmount);
                        activityRule.setSkuIdList(new ArrayList<>(activitySkuIdSet));
                        activityIdToActivityRuleMap.put(activityRule.getActivityId(),activityRule);
                        break;
                    }
                }
                }
            }

            return activityIdToActivityRuleMap;
    }

}
