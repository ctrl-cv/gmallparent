package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author mqx
 * @date 2021-2-26 10:36:28
 */
@RestController
@RequestMapping("api/cart")
public class CartApiController {

    //  调用服务层方法
    @Autowired
    private CartService cartService;

    //  制作映射路径
    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable Long skuId,
                            @PathVariable Integer skuNum,
                            HttpServletRequest request){
        //  缺少userId,在网关中存储了userId -- {header}
        //  获取登录的用户Id！
        //  添加购物车的时候，是否允许未登录时，也可以添加购物车?
        String userId = AuthContextHolder.getUserId(request);
        //  京东以前： 登录，未登录都能添加购物车， 去年7-8月份的时候，改了业务逻辑，只能登录的时候添加购物车！
        //  如果只有登录才能添加购物车：网关配置：addCart.html
        //  如果未登录情况下如果处置userId
        if (StringUtils.isEmpty(userId)){
            //  获取临时用户Id ，因为我们要组成缓存的key！
            userId = AuthContextHolder.getUserTempId(request);
        }
        //  添加购物车
        cartService.addToCart(skuId,userId,skuNum);
        return Result.ok();
    }

    //  查看购物车列表：
    @GetMapping("cartList")
    public Result getCartList(HttpServletRequest request){
        //  获取用户登录Id
        String userId = AuthContextHolder.getUserId(request);
        //  获取临时用户Id
        String userTempId = AuthContextHolder.getUserTempId(request);

        List<CartInfo> cartInfoList = cartService.getCartInfoList(userId, userTempId);
        //  返回数据
        //  内部直接赋值的 【cartInfoList】
        //  以后添加了优惠券：Result.ok(carInfoVo); carInfoVo.getCartInfoList();
        return Result.ok(cartInfoList);
    }

    //  购物车选中状态变更
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable Long skuId,
                            @PathVariable Integer isChecked,
                            HttpServletRequest request){
        //  获取登录用户Id
        String userId = AuthContextHolder.getUserId(request);
        //  获取临时用户Id
        if(StringUtils.isEmpty(userId)){
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.checkCart(userId,skuId,isChecked);
        //  返回
        return Result.ok();
    }

    //  删除购物项
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable Long skuId,
                HttpServletRequest request){
            //  获取登录用户Id
            String userId = AuthContextHolder.getUserId(request);
            //  获取临时用户Id
            if(StringUtils.isEmpty(userId)){
                userId = AuthContextHolder.getUserTempId(request);
            }
            cartService.deleteCart(userId,skuId);
            return Result.ok();
    }

    //  购物车清单
    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable String userId){
        return cartService.getCartCheckedList(userId);
    }

    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable String userId){
        cartService.loadCartCache(userId);
        return Result.ok();
    }
}
