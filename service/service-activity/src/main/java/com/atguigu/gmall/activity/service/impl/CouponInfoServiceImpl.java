package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.CouponInfoMapper;
import com.atguigu.gmall.activity.mapper.CouponRangeMapper;
import com.atguigu.gmall.activity.mapper.CouponUseMapper;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.model.activity.*;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.enums.CouponRangeType;
import com.atguigu.gmall.model.enums.CouponStatus;
import com.atguigu.gmall.model.enums.CouponType;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.BaseCategory3;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {
    @Resource
    CouponInfoMapper couponInfoMapper;

    @Resource
    CouponRangeMapper couponRangeMapper;

    @Resource
    ProductFeignClient productFeignClient;

    @Resource
    CouponUseMapper couponUseMapper;

    @Override
    public IPage selectPage(Page<CouponInfo> infoPage) {
        IPage<CouponInfo> couponInfoIPage = couponInfoMapper.selectPage(infoPage, null);
        couponInfoIPage.getRecords().stream().forEach(item ->{
            item.setCouponTypeString(CouponType.getNameByType(item.getCouponType()));
            item.setRangeTypeString(CouponRangeType.getNameByType(item.getRangeType()));
        });
        return couponInfoIPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCouponRule(CouponRuleVo couponRuleVo) {
        /*
        ?????????couponInfo ??? couponRange ??????????????????
        ?????????couponRange ?????????couponInfo ????????????couponRange ???
         */
        QueryWrapper<CouponRange> couponRangeQueryWrapper = new QueryWrapper<>();
        couponRangeQueryWrapper.eq("coupon_id",couponRuleVo.getCouponId());
        couponRangeMapper.delete(couponRangeQueryWrapper);

        CouponInfo couponInfo = this.getById(couponRuleVo.getCouponId());

        couponInfo.setRangeType(couponRuleVo.getRangeType().name());
        couponInfo.setConditionAmount(couponRuleVo.getConditionAmount());
        couponInfo.setConditionNum(couponRuleVo.getConditionNum());
        couponInfo.setBenefitAmount(couponRuleVo.getBenefitAmount());
        couponInfo.setBenefitDiscount(couponRuleVo.getBenefitDiscount());
        couponInfo.setRangeDesc(couponRuleVo.getRangeDesc());

        couponInfoMapper.updateById(couponInfo);
        List<CouponRange> couponRangeList = couponRuleVo.getCouponRangeList();
        for (CouponRange couponRange : couponRangeList) {
            couponRange.setCouponId(couponRuleVo.getCouponId());
            couponRangeMapper.insert(couponRange);
        }

    }

    @Override
    public Map findCouponRuleList(Long couponId) {
        Map<String, Object> map = new HashMap<>();

        QueryWrapper<CouponRange> couponRangeQueryWrapper = new QueryWrapper<>();
        couponRangeQueryWrapper.eq("coupon_id",couponId);

        List<CouponRange> activitySkuList = couponRangeMapper.selectList(couponRangeQueryWrapper);

        List<Long> rangeIdList = activitySkuList.stream().map(CouponRange::getRangeId).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(rangeIdList)){
            if ("SPU".equals(activitySkuList.get(0).getRangeType())){
                List<SpuInfo> spuInfoBySpuIdList = productFeignClient.findSpuInfoBySpuIdList(rangeIdList);
                map.put("spuInfoList",spuInfoBySpuIdList);
            }else if ("TRADEMARK".equals(activitySkuList.get(0).getRangeType())){
                List<BaseTrademark> baseTrademarkByTrademarkIdList = productFeignClient.findBaseTrademarkByTrademarkIdList(rangeIdList);
                map.put("trademarkList",baseTrademarkByTrademarkIdList);
            }else {
                List<BaseCategory3> baseCategory3ByCategory3IdList = productFeignClient.findBaseCategory3ByCategory3IdList(rangeIdList);
                map.put("category3List",baseCategory3ByCategory3IdList);
            }
        }
        return map;
    }

    @Override
    public List<CouponInfo> findCouponByKeyword(String keyword) {
        QueryWrapper<CouponInfo> couponInfoQueryWrapper = new QueryWrapper<>();
        couponInfoQueryWrapper.like("coupon_name",keyword);
        return couponInfoMapper.selectList(couponInfoQueryWrapper);
    }

    @Override
    public List<CouponInfo> findCouponInfo(Long skuId, Long activityId, Long userId) {
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        if (skuInfo == null) return new ArrayList<>();
        //?????????????????????
        List<CouponInfo> couponInfoList = couponInfoMapper.selectCouponInfoList(skuInfo.getSpuId(),skuInfo.getTmId(),skuInfo.getCategory3Id(),userId);

        //?????????????????????
        if (activityId != null){
            List<CouponInfo> couponInfoLists = couponInfoMapper.selectActivityCouponInfoList(skuInfo.getSpuId(),skuInfo.getTmId(),skuInfo.getCategory3Id(),activityId,userId);

            couponInfoList.addAll(couponInfoLists);
        }
        return couponInfoList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void getCouponInfo(Long couponId, long userId) {
        QueryWrapper<CouponUse> couponUseQueryWrapper = new QueryWrapper<>();
        couponUseQueryWrapper.eq("coupon_id",couponId).eq("user_id",userId);
        Integer count = couponUseMapper.selectCount(couponUseQueryWrapper);
        if (count > 0){
            throw new GmallException(ResultCodeEnum.COUPON_GET);
        }

        CouponInfo couponInfo = couponInfoMapper.selectById(couponId);
        if (couponInfo.getLimitNum() <= couponInfo.getTakenCount()){
            throw new GmallException(ResultCodeEnum.COUPON_LIMIT_GET);
        }

        couponInfo.setTakenCount(couponInfo.getTakenCount() + 1);
        this.updateById(couponInfo);

        CouponUse couponUse = new CouponUse();
        couponUse.setCouponId(couponId);
        couponUse.setUserId(userId);
        couponUse.setCouponStatus(CouponStatus.NOT_USED.name());
        couponUse.setGetTime(new Date());
        couponUse.setExpireTime(couponInfo.getExpireTime());
        couponUseMapper.insert(couponUse);
    }

    @Override
    public IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam, long userId) {
        return couponInfoMapper.selectPageByUserId(pageParam, userId);
    }

    @Override
    public Map<Long, List<CouponInfo>> findCartCouponInfo(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap, Long userId) {
        ArrayList<SkuInfo> skuInfoList = new ArrayList<>();
        Map<String, List<Long>> rangeToSkuIdMap = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(cartInfo.getSkuId());

            this.setRuleData(skuInfo, rangeToSkuIdMap);
        }
        for (CartInfo cartInfo : cartInfoList) {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(cartInfo.getSkuId());
            skuInfoList.add(skuInfo);
        }

        if (CollectionUtils.isEmpty(skuInfoList)) return new HashMap<>();

        List<CouponInfo> allCouponInfoList = couponInfoMapper.selectCartCouponInfoList(skuInfoList,userId);
        for (CouponInfo couponInfo : allCouponInfoList) {
            String rangeType = couponInfo.getRangeType();
            Long rangeId = couponInfo.getRangeId();

            if (couponInfo.getActivityId() != null){
                List<Long> skuIdList = new ArrayList<>();
                Iterator<Map.Entry<Long, Long>> iterator = skuIdToActivityIdMap.entrySet().iterator();
                while (iterator.hasNext()){
                    Map.Entry<Long, Long> entry = iterator.next();
                    Long skuId = entry.getKey();
                    Long activityId  = entry.getValue();

                    if (couponInfo.getActivityId().intValue() == activityId.intValue()){
                        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
                        if (couponInfo.getRangeType().equals(CouponRangeType.SPU.name())){
                            if (couponInfo.getRangeId().intValue() == skuInfo.getSpuId().intValue()){
                                skuIdList.add(skuId);
                            }
                        }
                        else if (couponInfo.getRangeType().equals(CouponRangeType.TRADEMARK.name())){
                            if (couponInfo.getRangeId().intValue() == skuInfo.getTmId().intValue()){
                                skuIdList.add(skuId);
                            }
                        }else {
                            if (couponInfo.getRangeId().intValue() == skuInfo.getCategory3Id().intValue()) {
                                skuIdList.add(skuId);
                            }
                        }
                    }
                }
                couponInfo.setSkuIdList(skuIdList);
            }else {
                if (rangeType.equals(CouponRangeType.SPU.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:1:" + rangeId));
                }else if (rangeType.equals(CouponRangeType.CATAGORY.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:2:" + rangeId));
                }else {
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:3:" + rangeId));
                }
            }
        }
        Map<Long, List<CouponInfo>> skuIdToCouponInfoListMap = new HashMap<>();
        for (CouponInfo couponInfo : allCouponInfoList) {
            List<Long> skuIdList = couponInfo.getSkuIdList();
            for (Long skuId : skuIdList) {
                if (skuIdToCouponInfoListMap.containsKey(skuId)){
                    List<CouponInfo> couponInfoList = skuIdToCouponInfoListMap.get(skuId);
                    couponInfoList.add(couponInfo);
                }else {
                    List<CouponInfo> couponInfoList = new ArrayList<>();
                    couponInfoList.add(couponInfo);
                    skuIdToCouponInfoListMap.put(skuId,couponInfoList);
                }
            }
        }

        return skuIdToCouponInfoListMap;
    }

    //??????????????????????????????
    @Override
    public List<CouponInfo> findTradeCouponInfo(List<OrderDetail> orderDetailList, Map<Long, ActivityRule> activityIdToActivityRuleMap, Long userId) {

        // ??????????????????????????????
        Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
        // ??????????????????????????????
        Map<Long, SkuInfo> skuIdToSkuInfoMap = new HashMap<>();
        // ???????????????????????????
        Map<String, List<Long>> rangeToSkuIdMap = new HashMap<>();

        ArrayList<SkuInfo> skuInfoList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(orderDetail.getSkuId());
            skuInfoList.add(skuInfo);
            skuIdToOrderDetailMap.put(orderDetail.getSkuId(),orderDetail);
            skuIdToSkuInfoMap.put(orderDetail.getSkuId(),skuInfo);
            //??????????????????
            //  this.setRuleData(skuInfo, orderDetail.getSkuId(), rangeToSkuIdMap);
            this.setRuleData(skuInfo,rangeToSkuIdMap);
        }

        /**
         * rangeType(????????????)  1:??????(spuid) 2:??????(????????????id) 3:??????
         * rangeId(??????id)
         * ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????id?????????????????????????????????????????????spuId???
         * ???????????????
         * couponId   rangeType   rangeId
         * 1             1             20
         * 1             1             30
         * 2             1             20
         */
        //  ??????????????????????????????
        List<CouponInfo> allCouponInfoList = couponInfoMapper.selectTradeCouponInfoList(skuInfoList,userId);
        //  ????????????????????????????????????skuId??????
        for (CouponInfo couponInfo : allCouponInfoList) {
            String rangeType = couponInfo.getRangeType();
            Long rangeId = couponInfo.getRangeId();
            //???????????????
            if (null != couponInfo.getActivityId()){
                Iterator<Map.Entry<Long, ActivityRule>> iterator = activityIdToActivityRuleMap.entrySet().iterator();
                while (iterator.hasNext()){
                    Map.Entry<Long, ActivityRule> entry = iterator.next();
                    Long activityId = entry.getKey();
                    ActivityRule activityRule = entry.getValue();

                    if (activityId.intValue() == couponInfo.getActivityId().intValue()){
                        List<Long> activitySkuIdList = activityRule.getSkuIdList();

                        List<Long> skuIdList = new ArrayList<>();
                        //??????skuId????????????????????????,?????????????????????skuIdList
                        for (Long skuId : activitySkuIdList) {
                            SkuInfo skuInfo = skuIdToSkuInfoMap.get(skuId);
                           if (rangeType.equals(CouponRangeType.SPU.name())){
                               if (activityId.intValue() == skuInfo.getSpuId().intValue()){
                                   skuIdList.add(skuId);
                               }
                           }
                           else if (rangeType.equals(CouponRangeType.CATAGORY.name())){
                               if (activityId.intValue() == skuInfo.getCategory3Id().intValue()){
                                   skuIdList.add(skuId);
                               }
                           }
                           else {
                               if (activityId.intValue() == skuInfo.getTmId().intValue()){
                                   skuIdList.add(skuId);
                               }
                           }
                           couponInfo.setSkuIdList(skuIdList);
                        }
                    }
                }
            }else {
                if (rangeType.equals(CouponRangeType.SPU.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:1:" + rangeId));
                } else if (rangeType.equals(CouponRangeType.CATAGORY.name())) {
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:2:" + rangeId));
                }else {
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:3:" + rangeId));
                }
            }
        }
        //  ???????????????????????????skuId????????????
        //  ???????????????????????????????????????sku??????
        ArrayList<CouponInfo> resultCouponInfoList  = new ArrayList<>();
        Map<Long, List<CouponInfo>> couponIdToListMap = allCouponInfoList.stream().collect(Collectors.groupingBy(couponInfo -> couponInfo.getId()));
        //  ??????????????????Id ???????????????????????????????????????????????????
        Iterator<Map.Entry<Long, List<CouponInfo>>> iterator = couponIdToListMap.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<Long, List<CouponInfo>> next = iterator.next();
            Long couponId = next.getKey();
            List<CouponInfo> couponInfoList = next.getValue();
            // ????????????????????????skuId
            List<Long> skuIdList = new ArrayList<>();

            for (CouponInfo couponInfo : couponInfoList) {
                skuIdList.addAll(couponInfo.getSkuIdList());
            }
            // ?????????????????????????????????????????????skuId??????
            CouponInfo couponInfo = couponInfoList.get(0);
            couponInfo.setSkuIdList(skuIdList);
            resultCouponInfoList.add(couponInfo);
        }
        //???????????????????????????????????????
        //????????????????????????
        BigDecimal checkeAmount = new BigDecimal("0");
        //?????????????????????
        CouponInfo checkeCouponInfo = null;
        for (CouponInfo couponInfo : resultCouponInfoList) {
            List<Long> skuIdList = couponInfo.getSkuIdList();
            //???????????????????????????????????????
            BigDecimal totalAmount = new BigDecimal("0");
            //???????????????????????????????????????
            int totalNum = 0;
            if (!CollectionUtils.isEmpty(skuIdList)){
                for (Long skuId : skuIdList) {
                    OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                    BigDecimal skuAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                    totalAmount = totalAmount.add(skuAmount);
                    totalNum = orderDetail.getSkuNum() + totalNum;
                }
            }
            /**
             * reduceAmount: ?????????????????????
             * isChecked:    ?????????????????????1????????????
             * isSelect:     ???????????????1????????????????????????????????????????????????????????? 100???10 200???30 500???70???????????????????????? 100???10???200???30???
             */
            //?????????????????????
            BigDecimal reduceAmount = new BigDecimal("0");
            // ??????????????? 1 ????????? 2 ????????? 3 ????????? 4 ???????????????
            if (couponInfo.getCouponType().equals(CouponType.CASH.name())){
                reduceAmount = couponInfo.getBenefitAmount();
                couponInfo.setIsChecked(1);
            }else if (couponInfo.getCouponType().equals(CouponType.DISCOUNT.name())){
                BigDecimal skuDiscountTotalAmount = totalAmount.multiply(couponInfo.getBenefitDiscount()).divide(new BigDecimal(10));
                reduceAmount = totalAmount.subtract(skuDiscountTotalAmount);
                couponInfo.setIsChecked(1);
            }else if(couponInfo.getCouponType().equals(CouponType.FULL_DISCOUNT.name())){
                if (totalAmount.compareTo(couponInfo.getConditionAmount()) > -1){
                    reduceAmount = couponInfo.getBenefitAmount();
                    couponInfo.setIsChecked(1);
                }
            }else {
                if (totalNum >= couponInfo.getConditionNum().intValue()){
                    BigDecimal skuDiscountTotalAmount1 = reduceAmount.multiply(couponInfo.getBenefitDiscount().divide(new BigDecimal(10)));
                    reduceAmount = totalAmount.subtract(skuDiscountTotalAmount1);
                    couponInfo.setIsChecked(1);
                }
            }
            //  reduceAmount ????????????????????????checkeAmount ?????????????????????
            if (reduceAmount.compareTo(checkeAmount) > 0){
                checkeAmount = reduceAmount;
                checkeCouponInfo = couponInfo;
            }
            couponInfo.setReduceAmount(reduceAmount);
        }
        // ??????????????????????????????????????????????????????
        if (null != checkeCouponInfo){
            for (CouponInfo couponInfo : resultCouponInfoList) {
                if (couponInfo.getId().longValue() == checkeCouponInfo.getId().longValue()){
                    couponInfo.setIsChecked(1);
                }
            }
        }
        return resultCouponInfoList;
    }

    private void setRuleData(SkuInfo skuInfo, Map<String, List<Long>> rangeToSkuIdMap) {
        //  ???????????????????????????????????????map?????????
        String key1 = "range:1:" + skuInfo.getSpuId();
        if (rangeToSkuIdMap.containsKey(key1)){
            List<Long> skuIdList = rangeToSkuIdMap.get(key1);
            skuIdList.add(skuInfo.getId());
        }else {
            List<Long> skuIdList = new ArrayList<>();
            skuIdList.add(skuInfo.getId());
            rangeToSkuIdMap.put(key1,skuIdList);
        }
        String key2 = "range:2:" + skuInfo.getCategory3Id();
        if(rangeToSkuIdMap.containsKey(key2)) {
            List<Long> skuIdList = rangeToSkuIdMap.get(key2);
            skuIdList.add(skuInfo.getId());
        } else {
            List<Long> skuIdList = new ArrayList<>();
            skuIdList.add(skuInfo.getId());
            rangeToSkuIdMap.put(key2, skuIdList);
        }

        String key3 = "range:3:" + skuInfo.getTmId();
        if(rangeToSkuIdMap.containsKey(key3)) {
            List<Long> skuIdList = rangeToSkuIdMap.get(key3);
            skuIdList.add(skuInfo.getId());
        } else {
            List<Long> skuIdList = new ArrayList<>();
            skuIdList.add(skuInfo.getId());
            rangeToSkuIdMap.put(key3, skuIdList);
        }
    }
}
