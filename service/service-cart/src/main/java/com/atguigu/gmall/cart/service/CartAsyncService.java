package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

/**
 * @author mqx
 * @date 2021-2-26 14:05:37
 */
//  操作异步
public interface CartAsyncService {

    // 异步更新
    void updateCartInfo(CartInfo cartInfo);

    //  异步添加
    void saveCartInfo(CartInfo cartInfo);
    //  删除购物车
    void deleteCartInfo(String userTempId);

    //  更新数据库商品的状态
    void checkCart(String userId, Long skuId, Integer isChecked);

    //  删除购物车
    void deleteCart(String userId, Long skuId);
}
