package com.atguigu.gmall.all.controller;


import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.Resource;



@Controller
public class IndexController {
   @Resource
   ProductFeignClient productFeignClient;

   @GetMapping({"/","index.html"})
    public String index(Model model){
       Result result = productFeignClient.getBaseCategoryList();

       model.addAttribute("list",result.getData());

       return "index/index";
   }
}
