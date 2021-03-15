package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.CouponInfoMapper;
import com.atguigu.gmall.activity.mapper.CouponRangeMapper;
import com.atguigu.gmall.activity.mapper.CouponUseMapper;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponRange;
import com.atguigu.gmall.model.activity.CouponRuleVo;
import com.atguigu.gmall.model.activity.CouponUse;
import com.atguigu.gmall.model.enums.CouponRangeType;
import com.atguigu.gmall.model.enums.CouponStatus;
import com.atguigu.gmall.model.enums.CouponType;
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
        优惠券couponInfo 与 couponRange 要一起操作：
        先删除couponRange ，更新couponInfo ，再新增couponRange ！
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
        //获取普通优惠卷
        List<CouponInfo> couponInfoList = couponInfoMapper.selectCouponInfoList(skuInfo.getSpuId(),skuInfo.getTmId(),skuInfo.getCategory3Id(),userId);

        //获取活动优惠卷
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
}
