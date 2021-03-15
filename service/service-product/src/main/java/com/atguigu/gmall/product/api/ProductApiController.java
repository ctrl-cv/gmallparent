package com.atguigu.gmall.product.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/product")
public class ProductApiController {
    @Resource
    ManageService manageService;

    @GetMapping("inner/getSkuInfo/{skuId}")
    public SkuInfo getAttrValueList(@PathVariable Long skuId){
        return manageService.getSkuInfo(skuId);
    }

    @GetMapping("inner/getCategoryView/{category3Id}")
    public BaseCategoryView getCategoryView(@PathVariable Long category3Id){
        return manageService.getCategoryViewByCategory3Id(category3Id);
    }
    @GetMapping("inner/getSkuPrice/{skuId}")
    public BigDecimal getSkuPrice(@PathVariable Long skuId){
        return manageService.getSkuPrice(skuId);
    }

    @GetMapping("inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(@PathVariable Long skuId,@PathVariable Long spuId){
        return manageService.getSpuSaleAttrListCheckBySku(skuId,spuId);
    }

    @GetMapping("inner/getSkuValueIdsMap/{spuId}")
    public Map getSkuValueIdsMap(@PathVariable Long spuId){
       return manageService.getSkuValueIdsMap(spuId);
    }
    /**
     * 获取全部分类信息
     * @return
     */
        @GetMapping("getBaseCategoryList")
        public Result getBaseCategoryList(){
            List<JSONObject> list = manageService.getBaseCategoryList();
            return Result.ok(list);
        }

    /**
     * 通过品牌Id 集合来查询数据
     * @param tmId
     * @return
     */
    @GetMapping("inner/getTrademark/{tmId}")
    public BaseTrademark getTrademark(@PathVariable Long tmId){
        return manageService.getTrademarkByTmId(tmId);
    }
    /**
     * 通过skuId 集合来查询数据
     * @param skuId
     * @return
     */
    @GetMapping("inner/getAttrList/{skuId}")
    public List<BaseAttrInfo> getAttrList(@PathVariable Long skuId){
        return manageService.getAttrList(skuId);
    }

    @GetMapping("inner/findSkuInfoByKeyword/{keyword}")
    public List<SkuInfo> findSkuInfoByKeyword(@PathVariable String keyword){
        return manageService.findSkuInfoByKeyword(keyword);
    }

    /**
     * 根据skuid列表获取sku列表
     * @param skuIdList
     * @return
     */
    @PostMapping("inner/findSkuInfoBySkuIdList")
    public List<SkuInfo> findSkuInfoBySkuIdList(@RequestBody List<Long> skuIdList){
        return manageService.findSkuInfoBySkuIdList(skuIdList);
    }

    /**
     * 根据spuid列表获取spu列表，活动使用
     * @param spuIdList
     * @return
     */
    @PostMapping("inner/findSpuInfoBySpuIdList")
    public List<SpuInfo> findSpuInfoBySpuIdList(@RequestBody List<Long> spuIdList){
        return manageService.findSpuInfoBySpuIdList(spuIdList);
    }

    /**
     * 根据category3Id列表获取category3列表，活动使用
     * @param category3IdList
     * @return
     */
    @PostMapping("inner/findBaseCategory3ByCategory3IdList")
    public List<BaseCategory3> findBaseCategory3ByCategory3IdList(@RequestBody List<Long> category3IdList){
        return manageService.findBaseCategory3ByCategory3IdList(category3IdList);
    }
}
