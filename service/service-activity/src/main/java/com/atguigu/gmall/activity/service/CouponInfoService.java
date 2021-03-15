package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponRuleVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface CouponInfoService extends IService<CouponInfo> {

    IPage selectPage(Page<CouponInfo> infoPage);

    void saveCouponRule(CouponRuleVo couponRuleVo);

    Map findCouponRuleList(Long couponId);

    List<CouponInfo> findCouponByKeyword(String keyword);

    /**
     * 获取优惠券信息
     * @param skuId
     * @param activityId
     * @param userId
     * @return
     */
    List<CouponInfo> findCouponInfo(Long skuId,Long activityId,Long userId);

    void getCouponInfo(Long couponId, long userId);

    IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam, long userId);
}
