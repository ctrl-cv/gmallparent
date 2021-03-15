package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/admin/product/baseTrademark")
public class BaseTrademarkController {
    @Resource
    BaseTrademarkService baseTrademarkService;

    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit){

        Page<BaseTrademark> baseTrademarkPage = new Page<>(page,limit);
       return Result.ok(baseTrademarkService.getPage(baseTrademarkPage));
    }
    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }
    @PutMapping("update")
    public Result update(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.updateById(baseTrademark);
        return  Result.ok();
    }
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        baseTrademarkService.removeById(id);
        return Result.ok();
    }

    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id){

        return Result.ok(baseTrademarkService.getById(id));
    }
    @GetMapping("getTrademarkList")
    public Result getTrademarkList(){
        return Result.ok(baseTrademarkService.list(null));
    }

    /**
     * 根据关键字获取spu列表，活动使用
     * @param keyword
     * @return
     */
    @GetMapping("findBaseTrademarkByKeyword/{keyword}")
    public Result findBaseTrademarkByKeyword(@PathVariable String keyword){
        return Result.ok(baseTrademarkService.findBaseTrademarkByKeyword(keyword));
    }

    /**
     * 根据trademarkId列表获取trademark列表，活动使用
     * @param trademarkIdList
     * @return
     */
    @PostMapping("inner/findBaseTrademarkByTrademarkIdList")
    public List<BaseTrademark> findBaseTrademarkByTrademarkIdList(@RequestBody List<Long> trademarkIdList){
        return baseTrademarkService.findBaseTrademarkByTrademarkIdList(trademarkIdList);
    }
}
