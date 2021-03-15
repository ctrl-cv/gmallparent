package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.activity.service.ActivityService;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ActivityServiceImpl implements ActivityService {
    @Resource
    ActivityInfoService activityInfoService;

    @Resource
    CouponInfoService couponInfoService;


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
}
