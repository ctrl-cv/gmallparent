package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategory1;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Api(tags = "商品基本属性接口")
@RestController
@RequestMapping("admin/product")
public class BaseManageController {
    @Resource
    ManageService manageService;

    @GetMapping("getCategory1")
    public Result<List<BaseCategory1>> getCategory1(){
        return Result.ok(manageService.getCategory1());
    }

    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable Long category1Id){
        return Result.ok(manageService.getCategory2(category1Id));
    }

    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable Long category2Id){
        return Result.ok(manageService.getCategory3(category2Id));
    }

    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result attrInfoList(@PathVariable Long category1Id,@PathVariable Long category2Id,@PathVariable Long category3Id){
        return Result.ok(manageService.getAttrInfoList(category1Id,category2Id,category3Id));
    }

    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo){
    manageService.saveAttrInfo(baseAttrInfo);
        return Result.ok();
    }

    @GetMapping("getAttrValueList/{attrId}")
    public Result getAttrValueList(@PathVariable Long attrId){

        return Result.ok(manageService.getAttrInfo(attrId).getAttrValueList());
    }

}
