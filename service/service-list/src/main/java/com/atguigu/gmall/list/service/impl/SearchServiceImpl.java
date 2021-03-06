package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.Searchservice;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;


import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements Searchservice {
    @Resource
    GoodsRepository goodsRepository;

    @Resource
    ProductFeignClient productFeignClient;

    @Resource
    RedisTemplate redisTemplate;

    @Resource
    RestHighLevelClient restHighLevelClient;


    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            //if (skuInfo == null) {
            //}
                goods.setId(skuInfo.getId());
                goods.setDefaultImg(skuInfo.getSkuDefaultImg());
                goods.setPrice(skuInfo.getPrice().doubleValue());
                goods.setCreateTime(new Date());
                goods.setTitle(skuInfo.getSkuName());


            return skuInfo;
        });

        CompletableFuture<Void> attrListFuture = CompletableFuture.runAsync(() -> {
            List<BaseAttrInfo> baseAttrInfoList = productFeignClient.getAttrList(skuId);
            //if (baseAttrInfoList != null) {
                List<SearchAttr> collect = baseAttrInfoList.stream().map(baseAttrInfo -> {
                    SearchAttr searchAttr = new SearchAttr();
                    searchAttr.setAttrId(baseAttrInfo.getId());
                    searchAttr.setAttrName(baseAttrInfo.getAttrName());
                    searchAttr.setAttrValue(baseAttrInfo.getAttrValueList().get(0).getValueName());
                    return searchAttr;
                }).collect(Collectors.toList());
                goods.setAttrs(collect);
           // }
        });


        CompletableFuture<Void> categoryViewFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            BaseCategoryView baseCategoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            //if (baseCategoryView != null) {
                goods.setCategory1Id(baseCategoryView.getCategory1Id());
                goods.setCategory2Id(baseCategoryView.getCategory2Id());
                goods.setCategory3Id(baseCategoryView.getCategory3Id());

                goods.setCategory1Name(baseCategoryView.getCategory1Name());
                goods.setCategory2Name(baseCategoryView.getCategory2Name());
                goods.setCategory3Name(baseCategoryView.getCategory3Name());
           // }
        });

        CompletableFuture<Void> trademarkByTmIdFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            BaseTrademark baseTrademark = productFeignClient.getTrademarkByTmId(skuInfo.getTmId());
            //if (baseTrademark != null) {
                goods.setTmName(baseTrademark.getTmName());
                goods.setTmLogoUrl(baseTrademark.getLogoUrl());
                goods.setTmId(baseTrademark.getId());
            //}
        });


        CompletableFuture.allOf(skuInfoCompletableFuture,trademarkByTmIdFuture,categoryViewFuture,attrListFuture).join();
        this.goodsRepository.save(goods);
    }

    @Override
    public void lowerGoods(Long skuId) {
        this.goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        String key = "hotScore";

        Double hotScore = redisTemplate.opsForZSet().incrementScore(key, "skuId:" + skuId, 1);

        if (hotScore % 10 == 0){
            Optional<Goods> optional = goodsRepository.findById(skuId);
            Goods goods = optional.get();
            goods.setHotScore(hotScore.longValue());
            goodsRepository.save(goods);
        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        //??????DSL??????
        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(response);
        SearchResponseVo responseVO = this.parseSearchResult(response);

        responseVO.setPageNo(searchParam.getPageNo());
        responseVO.setPageSize(searchParam.getPageSize());
        responseVO.setTotalPages((responseVO.getTotal() + searchParam.getPageSize() - 1)/searchParam.getPageSize());
        return responseVO;
    }

    /**
     * ????????????
     * @param response
     * @return
     */
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        /*
        private List<SearchResponseTmVo> trademarkList;
        private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
        private List<Goods> goodsList = new ArrayList<>();
        private Long total;//????????????
         */
//        SearchResponseVo responseVo = new SearchResponseVo();
//
//        //??????????????????  List<Goods> goodsList = new ArrayList<>();
//        ArrayList<Goods> goodsArrayList = new ArrayList<>();
//        SearchHit[] hits = response.getHits().getHits();
//        for (SearchHit hit : hits) {
//            String sourceAsString = hit.getSourceAsString();
//            Goods goods = JSON.parseObject(sourceAsString, Goods.class);
//            //??????????????????
//            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
//            if (highlightFields.get("title") != null){
//                Text title = highlightFields.get("title").getFragments()[0];
//                goods.setTitle(title.toString());
//            }
//            goodsArrayList.add(goods);
//        }
//        responseVo.setGoodsList(goodsArrayList);
//        //??????????????????  List<SearchResponseTmVo> trademarkList
//        ParsedLongTerms tmIdAgg = (ParsedLongTerms) response.getAggregations().asMap().get("tmIdAgg");
//
//        List<SearchResponseTmVo> trademark = tmIdAgg.getBuckets().stream().map(bucket -> {
//            //??????????????????
//            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
//            String keyAsString = ((Terms.Bucket) bucket).getKeyAsString();
//            ParsedStringTerms tmNameAgg = ((Terms.Bucket) bucket).getAggregations().get("tmNameAgg");
//            String attrName = tmNameAgg.getBuckets().get(0).getKeyAsString();
//            ParsedStringTerms tmLogoUrlAgg = ((Terms.Bucket) bucket).getAggregations().get("tmLogoUrlAgg");
//            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
//
//            searchResponseTmVo.setTmId(Long.parseLong(keyAsString));
//            searchResponseTmVo.setTmName(attrName);
//            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
//            return searchResponseTmVo;
//        }).collect(Collectors.toList());
//        responseVo.setTrademarkList(trademark);
//
//        //????????????
//        long total = response.getHits().totalHits;
//        responseVo.setTotal(total);
//
//        //List<SearchResponseAttrVo> attrsList = new ArrayList<>()
//        ParsedNested attrAgg = (ParsedNested) response.getAggregations().asMap().get("attrAgg");
//        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
//        List<SearchResponseAttrVo> attrsList = attrIdAgg.getBuckets().stream().map(bucket -> {
//            SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
//            Number keyAsNumber = ((Terms.Bucket) bucket).getKeyAsNumber();
//            ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
//            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
//            ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
//            List<? extends Terms.Bucket> subbuckets = attrValueAgg.getBuckets();
//            List<String> attrvalueList = subbuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
//
//            searchResponseAttrVo.setAttrId(keyAsNumber.longValue());
//            searchResponseAttrVo.setAttrName(attrName);
//            searchResponseAttrVo.setAttrValueList(attrvalueList);
//            return searchResponseAttrVo;
//        }).collect(Collectors.toList());
//        responseVo.setAttrsList(attrsList);
//        return responseVo;
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        /*
        private List<SearchResponseTmVo> trademarkList;
        private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
        private List<Goods> goodsList = new ArrayList<>();
        private Long total;//????????????
         */
        SearchHits hits = response.getHits();
        //  ?????????????????? ????????????????????????
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        //  ??????map ??????????????????????????? Aggregation ---> ParsedLongTerms
        //  ??????????????????????????????????????????buckets
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        //  Function ????????????????????????
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map((bucket) -> {
            //  ????????????????????????
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            //  ??????????????????Id
            String keyAsString = ((Terms.Bucket) bucket).getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(keyAsString));

            //  ????????????Name ????????????????????????
            ParsedStringTerms tmNameAgg = ((Terms.Bucket) bucket).getAggregations().get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);
            //  ???????????????LogoUrl
            ParsedStringTerms tmLogoUrlAgg = ((Terms.Bucket) bucket).getAggregations().get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
            return searchResponseTmVo;
        }).collect(Collectors.toList());

        //  ???????????????
        searchResponseVo.setTrademarkList(trademarkList);

        //  ?????????????????? attrAgg ??????nested ??????
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        //  ????????????????????????attrIdAgg
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        //  ?????????????????????????????????
        List<SearchResponseAttrVo> attrsList = attrIdAgg.getBuckets().stream().map((bucket) -> {
            //  ??????????????????????????????
            SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
            //  ?????????????????????Id
            Number keyAsNumber = ((Terms.Bucket) bucket).getKeyAsNumber();
            searchResponseAttrVo.setAttrId(keyAsNumber.longValue());
            //  ???????????????????????????
            ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseAttrVo.setAttrName(attrName);
            //  ??????????????????????????????
            ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
            //  ?????????????????????????????????????????? ,??????????????????????????????????????????key ??????????????????
            List<? extends Terms.Bucket> buckets = attrValueAgg.getBuckets();

            //  ????????????
            //            List<String> strings = new ArrayList<>();
            //            for (Terms.Bucket bucket1 : buckets) {
            //                //  ??????key ????????????????????????
            //                String keyAsString = bucket1.getKeyAsString();
            //                strings.add(keyAsString);
            //            }
            //            searchResponseAttrVo.setAttrValueList(strings);
            //  ????????????
            //  ?????? ?????? Terms.Bucket::getKeyAsString ????????? key
            List<String> vlaues = buckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());

            searchResponseAttrVo.setAttrValueList(vlaues);
            return searchResponseAttrVo;
        }).collect(Collectors.toList());

        searchResponseVo.setAttrsList(attrsList);
        // ???????????? goodsList
        SearchHit[] subHits = hits.getHits();
        //  ???????????????????????????Goods
        List<Goods> goodsList = new ArrayList<>();
        //  ????????????
        for (SearchHit subHit : subHits) {
            //  ?????????Goods.class ?????????json ?????????
            String sourceAsString = subHit.getSourceAsString();
            //  ???sourceAsString ??????Goods?????????
            Goods goods = JSON.parseObject(sourceAsString, Goods.class);
            //  ????????? ???????????????????????????????????????????????????
            if(subHit.getHighlightFields().get("title")!=null){
                //  ????????????????????????????????????
                Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                //  ???????????????title
                goods.setTitle(title.toString());
            }
            goodsList.add(goods);
        }
        //  ????????????????????????
        searchResponseVo.setGoodsList(goodsList);
        //  ??????total
        searchResponseVo.setTotal(hits.totalHits);
        return searchResponseVo;
    }

    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // ???????????????
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // ??????boolQueryBuilder
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // ?????????????????? ?????????????????????????????????????????????????????????????????????term
        if (searchParam.getCategory1Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id",searchParam.getCategory1Id()));
        }
        if (searchParam.getCategory2Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id",searchParam.getCategory2Id()));
        }
        if (searchParam.getCategory3Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id",searchParam.getCategory3Id()));
        }
        // ??????????????????
        // trademark=2:??????
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)){
            String[] split = trademark.split(":");
            if (split != null && split.length == 2) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId", split[0]));
            }
        }

        //?????????????????????
        String[] props = searchParam.getProps();
        if (props != null && props.length > 0){

            for (String prop : props) {
                String[] split = prop.split(":");
                if (split != null && split.length == 3){
                // ??????????????????
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                // ?????????????????????
                BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();

                subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));
                boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery,ScoreMode.None));

                boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        //?????????????????????
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            boolQueryBuilder.must(QueryBuilders.matchQuery("title",searchParam.getKeyword()).operator(Operator.AND));
        }
        searchSourceBuilder.query(boolQueryBuilder);
        // ????????????
        searchSourceBuilder.from((searchParam.getPageNo()-1)*searchParam.getPageSize());
        searchSourceBuilder.size(searchParam.getPageSize());
        //????????????
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlighter(highlightBuilder);
        //??????sort
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)){
            String[] split = order.split(":");
            if (split != null && split.length == 2){
                String field = "";
                switch (split[0]){
                    case "1":
                       field = "hotScore";
                       break;
                    case "2":
                        field = "price";
                        break;
                }
            searchSourceBuilder.sort(field, "asc".equals(split[1])?SortOrder.ASC:SortOrder.DESC);
            }else {
                searchSourceBuilder.sort("hotScore",SortOrder.DESC);
            }
        }
            //  ??????????????????
        searchSourceBuilder.aggregation(AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl")));
        //  ????????????????????????
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));
        // ???????????????
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        System.out.println("dsl:"+searchSourceBuilder.toString());
        return searchRequest;
    }
}
