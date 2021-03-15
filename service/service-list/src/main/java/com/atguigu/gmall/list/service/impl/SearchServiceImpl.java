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
        //编写DSL语句
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
     * 数据封装
     * @param response
     * @return
     */
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        /*
        private List<SearchResponseTmVo> trademarkList;
        private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
        private List<Goods> goodsList = new ArrayList<>();
        private Long total;//总记录数
         */
//        SearchResponseVo responseVo = new SearchResponseVo();
//
//        //设置商品集合  List<Goods> goodsList = new ArrayList<>();
//        ArrayList<Goods> goodsArrayList = new ArrayList<>();
//        SearchHit[] hits = response.getHits().getHits();
//        for (SearchHit hit : hits) {
//            String sourceAsString = hit.getSourceAsString();
//            Goods goods = JSON.parseObject(sourceAsString, Goods.class);
//            //获取高亮字段
//            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
//            if (highlightFields.get("title") != null){
//                Text title = highlightFields.get("title").getFragments()[0];
//                goods.setTitle(title.toString());
//            }
//            goodsArrayList.add(goods);
//        }
//        responseVo.setGoodsList(goodsArrayList);
//        //设置商品集合  List<SearchResponseTmVo> trademarkList
//        ParsedLongTerms tmIdAgg = (ParsedLongTerms) response.getAggregations().asMap().get("tmIdAgg");
//
//        List<SearchResponseTmVo> trademark = tmIdAgg.getBuckets().stream().map(bucket -> {
//            //声明品牌对象
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
//        //总记录数
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
        private Long total;//总记录数
         */
        SearchHits hits = response.getHits();
        //  赋值品牌集合 需要从聚合中获取
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        //  通过map 来获取到对应的数据 Aggregation ---> ParsedLongTerms
        //  为什么需要转换主要是想获取到buckets
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        //  Function 有参数，有返回值
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map((bucket) -> {
            //  什么一个品牌对象
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            //  获取到了品牌Id
            String keyAsString = ((Terms.Bucket) bucket).getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(keyAsString));

            //  赋值品牌Name 是在另外一个桶中
            ParsedStringTerms tmNameAgg = ((Terms.Bucket) bucket).getAggregations().get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);
            //  赋值品牌的LogoUrl
            ParsedStringTerms tmLogoUrlAgg = ((Terms.Bucket) bucket).getAggregations().get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
            return searchResponseTmVo;
        }).collect(Collectors.toList());

        //  添加品牌的
        searchResponseVo.setTrademarkList(trademarkList);

        //  添加平台属性 attrAgg 属于nested 类型
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        //  在转完之后在获取attrIdAgg
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        //  获取对应的平台属性数据
        List<SearchResponseAttrVo> attrsList = attrIdAgg.getBuckets().stream().map((bucket) -> {
            //  什么一个平台属性对象
            SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
            //  获取到平台属性Id
            Number keyAsNumber = ((Terms.Bucket) bucket).getKeyAsNumber();
            searchResponseAttrVo.setAttrId(keyAsNumber.longValue());
            //  获取到平台属性名称
            ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseAttrVo.setAttrName(attrName);
            //  获取平台属性值的名称
            ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
            //  平台属性值名称对应有多个数据 ,需要循环遍历获取到里面的每个key 所对应的数据
            List<? extends Terms.Bucket> buckets = attrValueAgg.getBuckets();

            //  方式一：
            //            List<String> strings = new ArrayList<>();
            //            for (Terms.Bucket bucket1 : buckets) {
            //                //  通过key 来获取对应的数据
            //                String keyAsString = bucket1.getKeyAsString();
            //                strings.add(keyAsString);
            //            }
            //            searchResponseAttrVo.setAttrValueList(strings);
            //  方式二：
            //  表示 通过 Terms.Bucket::getKeyAsString 来获取 key
            List<String> vlaues = buckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());

            searchResponseAttrVo.setAttrValueList(vlaues);
            return searchResponseAttrVo;
        }).collect(Collectors.toList());

        searchResponseVo.setAttrsList(attrsList);
        // 商品集合 goodsList
        SearchHit[] subHits = hits.getHits();
        //  声明一个集合来存储Goods
        List<Goods> goodsList = new ArrayList<>();
        //  循环遍历
        for (SearchHit subHit : subHits) {
            //  是一个Goods.class 组成的json 字符串
            String sourceAsString = subHit.getSourceAsString();
            //  将sourceAsString 变为Goods的对象
            Goods goods = JSON.parseObject(sourceAsString, Goods.class);
            //  细节： 如果通过关键词检索，获取到高亮字段
            if(subHit.getHighlightFields().get("title")!=null){
                //  说明你是通过关键词检索的
                Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                //  覆盖原来的title
                goods.setTitle(title.toString());
            }
            goodsList.add(goods);
        }
        //  赋值商品集合对象
        searchResponseVo.setGoodsList(goodsList);
        //  赋值total
        searchResponseVo.setTotal(hits.totalHits);
        return searchResponseVo;
    }

    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 构建查询器
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 构建boolQueryBuilder
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 构建分类过滤 用户在点击的时候，只能点击一个值，所以此处使用term
        if (searchParam.getCategory1Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id",searchParam.getCategory1Id()));
        }
        if (searchParam.getCategory2Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id",searchParam.getCategory2Id()));
        }
        if (searchParam.getCategory3Id() != null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id",searchParam.getCategory3Id()));
        }
        // 构建品牌查询
        // trademark=2:华为
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)){
            String[] split = trademark.split(":");
            if (split != null && split.length == 2) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId", split[0]));
            }
        }

        //构建属性值查询
        String[] props = searchParam.getProps();
        if (props != null && props.length > 0){

            for (String prop : props) {
                String[] split = prop.split(":");
                if (split != null && split.length == 3){
                // 构建嵌套查询
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                // 嵌套查询子查询
                BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();

                subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));
                boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery,ScoreMode.None));

                boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        //构建关键字查询
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            boolQueryBuilder.must(QueryBuilders.matchQuery("title",searchParam.getKeyword()).operator(Operator.AND));
        }
        searchSourceBuilder.query(boolQueryBuilder);
        // 构建分页
        searchSourceBuilder.from((searchParam.getPageNo()-1)*searchParam.getPageSize());
        searchSourceBuilder.size(searchParam.getPageSize());
        //构建高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlighter(highlightBuilder);
        //构建sort
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
            //  设置品牌聚合
        searchSourceBuilder.aggregation(AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl")));
        //  设置平台属性聚合
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));
        // 结果集过滤
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        System.out.println("dsl:"+searchSourceBuilder.toString());
        return searchRequest;
    }
}
