package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Api(tags = "SPU的数据接口")
@RestController
@RequestMapping("admin/product")
public class SpuManageController {
    @Resource
    ManageService manageService;

    @GetMapping("{page}/{limit}")
    public Result getSpuInfoPage(@PathVariable Long page,
                                 @PathVariable Long limit,
                                 SpuInfo spuInfo){
        Page<SpuInfo> spuInfoPage = new Page<>(page,limit);
        return Result.ok(manageService.getSpuInfoPage(spuInfoPage,spuInfo));
    }
    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList(){

        return Result.ok(manageService.getBaseSaleAttrList());
    }

    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){
        manageService.saveSpuInfo(spuInfo);
        return Result.ok();
    }

    @GetMapping("spuImageList/{spuId}")
    public Result spuImageList(@PathVariable Long spuId){
        return Result.ok(manageService.getSpuImageList(spuId));
    }

    @GetMapping("spuSaleAttrList/{spuId}")
    public Result spuSaleAttrList(@PathVariable Long spuId){
        return Result.ok(manageService.getSpuSaleAttrList(spuId));
    }

    /**
     * 根据关键字获取spu列表，活动使用
     * @param keyword
     * @return
     */
    @GetMapping("findSpuInfoByKeyword/{keyword}")
    public Result findSpuInfoByKeyword(@PathVariable String keyword){
        return Result.ok(manageService.findSpuInfoByKeyword(keyword));
    }
}
