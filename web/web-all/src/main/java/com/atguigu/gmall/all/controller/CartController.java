package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author mqx
 * @date 2021-2-27 11:45:49
 */
@Controller
public class CartController {

    @Resource
    private CartFeignClient cartFeignClient;

    @Resource
    private ProductFeignClient productFeignClient;

    //  http://cart.gmall.com/addCart.html?skuId=40&skuNum=1
    @GetMapping("addCart.html")
    public String addCart(HttpServletRequest request){

        String skuId = request.getParameter("skuId");
        String skuNum = request.getParameter("skuNum");
        //  页面需要后台存储一个skuInfo 对象
        SkuInfo skuInfo = productFeignClient.getSkuInfo(Long.parseLong(skuId));

        cartFeignClient.addToCart(Long.parseLong(skuId),Integer.parseInt(skuNum));

        request.setAttribute("skuInfo",skuInfo);
        request.setAttribute("skuNum",skuNum);

        //  返回页面
        return "cart/addCart";
    }

    //  查看购物车列表：
    @GetMapping("cart.html")
    public String cartList(){
        //  返回这个页面之后，异步直接加载 /api/cart/cartList
        //  直接返回页面
        return "cart/index";
    }
}
