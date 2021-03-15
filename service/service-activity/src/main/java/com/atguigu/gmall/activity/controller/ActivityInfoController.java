package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.ActivityInfo;
import com.atguigu.gmall.model.activity.ActivityRuleVo;
import com.atguigu.gmall.model.enums.ActivityType;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("admin/activity/activityInfo")
public class ActivityInfoController {

    @Resource
    ActivityInfoService activityInfoService;

    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long limit,@PathVariable Long page){
        Page<ActivityInfo> infoPage = new Page<>(page,limit);
        return Result.ok(activityInfoService.getPage(infoPage));
    }

    @PostMapping("save")
    public Result save(@RequestBody ActivityInfo activityInfo){
        activityInfo.setCreateTime(new Date());
        activityInfoService.save(activityInfo);
        return Result.ok();
    }

    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id){
        ActivityInfo activityInfo = activityInfoService.getById(id);
        activityInfo.setActivityTypeString(ActivityType.getNameByType(activityInfo.getActivityType()));
        return Result.ok(activityInfo);
    }

    @PutMapping("update")
    public Result update(@RequestBody ActivityInfo activityInfo){
        activityInfoService.updateById(activityInfo);
        return Result.ok();
    }

    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        activityInfoService.removeById(id);
        return Result.ok();
    }

    @DeleteMapping("batchRemove")
    public Result batchRemove(@RequestBody List<Long> idList){
        activityInfoService.removeByIds(idList);
        return Result.ok();
    }

    @PostMapping("saveActivityRule")
    public Result saveActivityRule(@RequestBody ActivityRuleVo activityRuleVo){
        activityInfoService.saveActivityRule(activityRuleVo);
        return Result.ok();
    }

    @GetMapping("findSkuInfoByKeyword/{keyword}")
    public Result findSkuInfoByKeyword(@PathVariable String keyword){
        return Result.ok(activityInfoService.findSkuInfoByKeyword(keyword));
    }

    @GetMapping("findActivityRuleList/{id}")
    public Result findSkuInfoBySkuIdList(@PathVariable Long id){
        return Result.ok(activityInfoService.findSkuInfoBySkuIdList(id));
    }
}
