package com.atguigu.gmall.product.client;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.impl.ProductDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@FeignClient(value = "service-product",fallback = ProductDegradeFeignClient.class)
public interface ProductFeignClient {
    @GetMapping("api/product/inner/getSkuInfo/{skuId}")
    SkuInfo getSkuInfo(@PathVariable Long skuId);

    @GetMapping("/api/product/inner/getCategoryView/{category3Id}")
    BaseCategoryView getCategoryView(@PathVariable Long category3Id);

    @GetMapping("/api/product/inner/getSkuPrice/{skuId}")
    BigDecimal getSkuPrice(@PathVariable Long skuId);

    @GetMapping("/api/product/inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(@PathVariable Long skuId,@PathVariable Long spuId);

    @GetMapping("/api/product/inner/getSkuValueIdsMap/{spuId}")
    Map getSkuValueIdsMap(@PathVariable Long spuId);

    /**
     * 获取全部分类信息
     * @return
     */
    @GetMapping("/api/product/getBaseCategoryList")
    Result getBaseCategoryList();

    /**
     * 通过品牌Id 集合来查询数据
     * @param tmId
     * @return
     */
    @GetMapping("/api/product/inner/getTrademark/{tmId}")
    BaseTrademark getTrademarkByTmId(@PathVariable Long tmId);

    /**
     * 通过skuId 集合来查询数据
     * @param skuId
     * @return
     */
    @GetMapping("/api/product/inner/getAttrList/{skuId}")
    List<BaseAttrInfo> getAttrList(@PathVariable Long skuId);

    /**
     * 根据关键字获取sku列表，活动使用
     * @param keyword
     * @return
     */
    @GetMapping("api/product/inner/findSkuInfoByKeyword/{keyword}")
    List<SkuInfo> findSkuInfoByKeyword(@PathVariable String keyword);

    /**
     * 根据skuid列表获取sku列表，活动使用
     * @param skuIdList
     * @return
     */
    @PostMapping("/api/product/inner/findSkuInfoBySkuIdList")
    List<SkuInfo> findSkuInfoBySkuIdList(@RequestBody List<Long> skuIdList);

    /**
     * 根据spuid列表获取spu列表，活动使用
     * @param spuIdList
     * @return
     */
    @PostMapping("/api/product/inner/findSpuInfoBySpuIdList")
    List<SpuInfo> findSpuInfoBySpuIdList(@RequestBody List<Long> spuIdList);

    /**
     * 根据category3Id列表获取category3列表，活动使用
     * @param category3IdList
     * @return
     */
    @PostMapping("/api/product/inner/findBaseCategory3ByCategory3IdList")
    List<BaseCategory3> findBaseCategory3ByCategory3IdList(@RequestBody List<Long> category3IdList);

    /**
     * 根据trademarkId列表获取trademark列表，活动使用
     * @param trademarkIdList
     * @return
     */
    @PostMapping("/admin/product/baseTrademark/inner/findBaseTrademarkByTrademarkIdList")
    List<BaseTrademark> findBaseTrademarkByTrademarkIdList(@RequestBody List<Long> trademarkIdList);


}
