package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class SeckillController {
    @Resource
    ActivityFeignClient activityFeignClient;

    @GetMapping("seckill.html")
    public String index(HttpServletRequest request){
        Result result = activityFeignClient.findAll();
        request.setAttribute("list",result.getData());
        return "seckill/index";
    }

    @GetMapping("seckill/{skuId}.html")
    public String getItem(@PathVariable Long skuId,HttpServletRequest request){
        Result result = activityFeignClient.getSeckillGoods(skuId);
        request.setAttribute("item",result.getData());
        return "seckill/item";
    }

    @GetMapping("seckill/queue.html")
    public String queue(HttpServletRequest request){
        String skuId = request.getParameter("skuId");
        String skuIdStr = request.getParameter("skuIdStr");
        request.setAttribute("skuIdStr",skuIdStr);
        request.setAttribute("skuId",skuId);
        return "seckill/queue";
    }

    /**
     * 确认下单
     * @param model
     * @return
     */
    @GetMapping("seckill/trade.html")
    public String trade(Model model){
        Result<Map> result = activityFeignClient.trade();
        if (result.isOk()){
            model.addAllAttributes(result.getData());
            return "seckill/trade";
        }else {
            model.addAttribute("message",result.getMessage());
            return "seckill/fail";
        }

    }
}
