package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponRuleVo;
import com.atguigu.gmall.model.enums.CouponType;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sun.org.apache.regexp.internal.RE;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/admin/activity/couponInfo")
public class CouponInfoController {

    @Resource
    CouponInfoService couponInfoService;

    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,@PathVariable Long limit){
        Page<CouponInfo> couponInfoPage = new Page<>(page,limit);
        return Result.ok(couponInfoService.selectPage(couponInfoPage));
    }

    @GetMapping("get/{id}")
    public Result getCouponinfo(@PathVariable Long id){
        CouponInfo couponInfo = couponInfoService.getById(id);
        couponInfo.setCouponTypeString(CouponType.getNameByType(couponInfo.getCouponType()));
        return Result.ok(couponInfo);
    }

    @PostMapping("save")
    public Result save(@RequestBody CouponInfo couponInfo){

        couponInfoService.save(couponInfo);
        return Result.ok();
    }

    @PutMapping("update")
    public Result update(@RequestBody CouponInfo couponInfo){
        return Result.ok(couponInfoService.updateById(couponInfo));
    }

    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        couponInfoService.removeById(id);
        return Result.ok();
    }

    @DeleteMapping("batchRemove")
    public Result batchRemove(@RequestBody List<Long> idList){
        couponInfoService.removeByIds(idList);
        return Result.ok();
    }
    @ApiOperation(value = "新增优惠券规则")
    @PostMapping("saveCouponRule")
    public Result saveCouponRule(@RequestBody CouponRuleVo couponRuleVo){
        couponInfoService.saveCouponRule(couponRuleVo);
        return Result.ok();
    }

    @GetMapping("findCouponRuleList/{id}")
    public Result findCouponRuleList(@PathVariable Long id){
        return Result.ok(couponInfoService.findCouponRuleList(id));
    }

    /**
     * 根据关键字获取优惠券列表，活动使用
     * @param keyword
     * @return
     */
    @GetMapping("findCouponByKeyword/{keyword}")
    public Result findCouponByKeyword(@PathVariable String keyword){
        return Result.ok(couponInfoService.findCouponByKeyword(keyword));
    }

}
