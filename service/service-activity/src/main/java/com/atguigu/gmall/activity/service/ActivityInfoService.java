package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.activity.ActivityInfo;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.ActivityRuleVo;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface ActivityInfoService extends IService<ActivityInfo> {
    /**
     * 分页查询
     * @param pageParam
     * @return
     */
    IPage<ActivityInfo> getPage(Page<ActivityInfo> pageParam);

    void saveActivityRule(ActivityRuleVo activityRuleVo);

    List<SkuInfo> findSkuInfoByKeyword(String keyword);

    Map findSkuInfoBySkuIdList(Long id);

    /**
     * 根据skuId 找到活动规则
     * @param skuId
     * @return
     */
    List<ActivityRule> findActivityRule(Long skuId);

    List<CarInfoVo> findCartActivityRuleMap(List<CartInfo> cartInfoList);
}
