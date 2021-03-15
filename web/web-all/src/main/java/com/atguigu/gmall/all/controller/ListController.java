package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ListController {
    @Resource
    ListFeignClient listFeignClient;

    // 编写控制器
    //  http://list.gmall.com/list.html?category3Id=61
    //  SearchParam searchParam 不需要使用@RequestBody
    @GetMapping("list.html")
    public String list(SearchParam searchParam, Model model){
        Result<Map> result = listFeignClient.list(searchParam);

        //  后台需要存储数据给前台使用： 例如 trademarkList，

        //  ${urlParam} 存储一个主要的数据 urlParam = 记录用户通过什么条件进行检索的！
        //  http://list.atguigu.cn/list.html?category3Id=61
        //  http://list.atguigu.cn/list.html?category3Id=61&trademark=5:小米
        //  urlParam  记录 ? 后面的条件 category3Id=61&trademark=5:小米

        //  排序： ${orderMap.type}  ${orderMap.sort}  type sort 可以任务是类的属性，或者是map 的key

        Map<String,Object> orderMap = this.orderByMap(searchParam.getOrder());

        model.addAttribute("orderMap",orderMap);
        //  面包屑处理：  ${propsParamList}  ${trademarkParam}
        String trademarkParam = this.makeTradeMark(searchParam.getTrademark());

        //  平台属性：
        List<Map<String,String>> propsParamList = this.makeProps(searchParam.getProps());
        model.addAttribute("trademarkParam",trademarkParam);
        model.addAttribute("propsParamList",propsParamList);

        //  ${searchParam.keyword} 前台页面需要的数据
        String urlParam = this.makeUrlParam(searchParam);
        model.addAttribute("urlParam",urlParam);
        model.addAttribute("searchParam",searchParam);
        //  result.getData() == SearchResponseVo
        model.addAllAttributes(result.getData());
        // 返回视图名称
        return "list/index";
    }

    //  设置排序
    private Map<String, Object> orderByMap(String order) {
        Map<String, Object> map = new HashMap<>();
        //  判断 order=2:desc  order=2:asc
        if (!StringUtils.isEmpty(order)){
            String[] split = order.split(":");
            //  ${orderMap.type} ${orderMap.sort}
            if (split!=null && split.length==2){
                //  与 实现类对应关系
                map.put("type",split[0]);
                map.put("sort",split[1]);
            }
        }else {
            //  默认值
            map.put("type","1"); // 综合 hotScore
            map.put("sort","asc"); // 默认排序规则
        }
        return map;
    }

    //  制作平台属性
    private List<Map<String, String>> makeProps(String[] props) {
        List<Map<String, String>> list = new ArrayList<>();
        //  判断是否根据平台属性值进行过滤
        if (props!=null && props.length>0){
            //  循环遍历 &props=23:8G:运行内存33
            for (String prop : props) {
                String[] split = prop.split(":");
                if (split!=null && split.length==3){
                    //                    split[0];
                    //                    split[1];
                    //  根据前台页面分析
                    Map<String, String> map = new HashMap<>();
                    map.put("attrId",split[0]);
                    map.put("attrValue",split[1]);
                    map.put("attrName",split[2]);
                    list.add(map);
                }
            }
        }
        return list;
    }

    //  处理品牌数据
    private String makeTradeMark(String trademark) {
        if (!StringUtils.isEmpty(trademark)){
            //  分割数据 4:小米
            String[] split = trademark.split(":");
            if (split!=null && split.length==2){
                return "品牌："+split[1];
            }
        }
        return null;
    }


    private String makeUrlParam(SearchParam searchParam) {
        //  声明一个字符串对象
        StringBuffer urlParam = new StringBuffer();
        //  判断用户是否先通过分类Id检索
        //  http://list.atguigu.cn/list.html?category1Id=2
        if (!StringUtils.isEmpty(searchParam.getCategory1Id())){
            urlParam.append("category1Id=").append(searchParam.getCategory1Id());
        }
        //  http://list.atguigu.cn/list.html?category2Id=13
        if (!StringUtils.isEmpty(searchParam.getCategory2Id())){
            urlParam.append("category2Id=").append(searchParam.getCategory2Id());
        }

        //  http://list.atguigu.cn/list.html?category3Id=61
        if (!StringUtils.isEmpty(searchParam.getCategory3Id())){
            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
        }

        //  判断是否根据关键字检索
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            //http://list.atguigu.cn/list.html?keyword=小米手机
            urlParam.append("keyword=").append(searchParam.getKeyword());
        }

        //  判断是否又根据品牌进行了检索
        if (!StringUtils.isEmpty(searchParam.getTrademark())){
            // http://list.atguigu.cn/list.html?keyword=小米手机&trademark=2:苹果
            if (urlParam.length()>0){
                urlParam.append("&trademark=").append(searchParam.getTrademark());
            }
        }
        //  判断是否又根据平台属性值进行了检索
        // http://list.atguigu.cn/list.html?keyword=小米手机&trademark=2:苹果&props=107:苹果:二级手机&props=23:4G:运行内存33
        String[] props = searchParam.getProps();
        if (props!=null && props.length>0){
            // 循环遍历
            for (String prop : props) {
                if (urlParam.length()>0){
                    urlParam.append("&props=").append(prop);
                }
            }
        }
        return "list.html?"+urlParam.toString();
    }

//    @GetMapping("list.html")
//    public String list(SearchParam searchParam, Model model){
//        Result<Map> list = listFeignClient.list(searchParam);
//
//        // 记录拼接url；
//        String urlParam = this.makeUrlParam(searchParam);
//
//        List<Map<String, String>> propsParamList = this.makeProps(searchParam.getProps());
//        //  面包屑处理：  ${propsParamList}  ${trademarkParam}
//        String trademarkParam = this.makeTrademark(searchParam.getTrademark());
//
//        Map<String, Object> orderMap = this.dealOrder(searchParam.getOrder());
//
//        model.addAttribute("searchParam",searchParam);
//        model.addAttribute("urlParam",urlParam);
//        model.addAttribute("trademarkParam",trademarkParam);
//        model.addAttribute("propsParamList",propsParamList);
//        model.addAttribute("orderMap",orderMap);
//        model.addAllAttributes(list.getData());
//        return "list/index";
//    }
//
//
//    /**
//     * 处理品牌条件回显
//     * @param trademark
//     * @return
//     */
//    private String makeTrademark(String trademark) {
//        if (!StringUtils.isEmpty(trademark)){
//            String[] split = trademark.split(":");
//            if (split != null && split.length == 2){
//                return "品牌：" + split[1];
//            }
//        }
//        return "";
//    }
//    /**
//     * 处理平台属性条件回显
//     * @param props
//     * @return
//     */
//        // 处理平台属性
//    private List<Map<String, String>> makeProps(String[] props) {
//        List<Map<String, String>> list = new ArrayList<>();
//        if (props != null && props.length != 0){
//            for (String prop : props) {
//                String[] split = prop.split(":");
//                if (split != null && split.length == 3){
//                    Map<String, String> map = new HashMap<>();
//                    map.put("attrId",split[0]);
//                    map.put("attrValue",split[1]);
//                    map.put("attrName",split[2]);
//                    list.add(map);
//                }
//            }
//        }
//        return list;
//    }
//
//    private String makeUrlParam(SearchParam searchParam) {
//        StringBuffer urlParam = new StringBuffer();
//        if (searchParam.getCategory1Id() != null){
//            urlParam.append("category1Id=").append(searchParam.getCategory1Id());
//        }
//        if (searchParam.getCategory2Id() != null){
//            urlParam.append("category2Id=").append(searchParam.getCategory2Id());
//        }
//        if (searchParam.getCategory3Id() != null){
//            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
//        }
//        if (searchParam.getTrademark() != null){
//            if (urlParam.length() > 0) {
//                urlParam.append("&trademark=").append(searchParam.getTrademark());
//            }
//        }
//        if (searchParam.getProps() != null){
//            for (String prop : searchParam.getProps()) {
//                if (urlParam.length() > 0){
//                    urlParam.append("&props=").append(prop);
//                }
//            }
//        }
//        if (searchParam.getKeyword() != null){
//            urlParam.append("keyword=").append(searchParam.getKeyword());
//        }
//        return "list.html?" + urlParam.toString();
//    }
//
//    /**
//     * 处理排序
//     * @param order
//     * @return
//     */
//    private Map<String, Object> dealOrder(String order) {
//        Map<String, Object> orderMap = new HashMap<>();
//        if (order != null){
//            String[] split = order.split(":");
//            if (split != null && split.length == 2){
//                orderMap.put("type",split[0]);
//                orderMap.put("sort",split[1]);
//            }
//        }else {
//            orderMap.put("type","1");
//            orderMap.put("sort","asc");
//        }
//        return orderMap;
//    }
    }
