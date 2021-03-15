package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

/**
 * @author mqx
 * @date 2021-2-26 09:42:46
 */
public interface CartService {
    //  定义添加购物车方法
    void addToCart(Long skuId, String userId, Integer skuNum);

    //  查询购物车列表
    List<CartInfo> getCartInfoList(String userId, String userTempId);

    //  根据用户Id查询数据 包含登录的，也包含未登录
    List<CartInfo> getCartInfoList(String userId);

    /**
     * 选中状态变更
     * @param userId
     * @param skuId
     * @param isChecked
     */
    void checkCart(String userId, Long skuId, Integer isChecked);

    //  删除购物车
    void deleteCart(String userId, Long skuId);

    //  根据用户Id 查询购物车选中的商品列表
    List<CartInfo> getCartCheckedList(String userId);

    List<CartInfo> loadCartCache(String userId);

}
