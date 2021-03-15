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
    public List<CarInfoVo> findCartActivityRuleMap(List<CartInfo> cartInfoList) {

        ArrayList<CarInfoVo> carInfoVoList = new ArrayList<>();
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
            Long activityid = next.getKey();
            List<ActivityRule> currentActivityRuleList = next.getValue();

            Set<Long> activitySkuIdSet = currentActivityRuleList.stream().map(activityRule -> activityRule.getSkuId()).collect(Collectors.toSet());

            CarInfoVo carInfoVo = new CarInfoVo();

            ArrayList<CartInfo> cartInfos = new ArrayList<>();
            for (Long skuId : activitySkuIdSet) {
                CartInfo cartInfo = skuIdToCartInfoMap.get(skuId);
                cartInfos.add(cartInfo);
            }

            carInfoVo.setCartInfoList(cartInfos);
            carInfoVo.setActivityRuleList(skuIdToActivityRuleListMap.get(activitySkuIdSet.iterator().next()));
            carInfoVoList.add(carInfoVo);
        }
        return carInfoVoList;
    }

}
