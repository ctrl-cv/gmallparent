package com.atguigu.gmall.activity.client.impl;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderDetailVo;
import com.atguigu.gmall.model.order.OrderTradeVo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ActivityDegradeFeignClient implements ActivityFeignClient {
    @Override
    public Result findAll() {
        return null;
    }

    @Override
    public Result getSeckillGoods(Long skuId) {
        return null;
    }

    @Override
    public Result trade() {
        return null;
    }

    @Override
    public List<CarInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId) {
        ArrayList<CarInfoVo> carInfoVos = new ArrayList<>();
        CarInfoVo carInfoVo = new CarInfoVo();
        carInfoVo.setCartInfoList(cartInfoList);
        carInfoVo.setActivityRuleList(null);
        carInfoVos.add(carInfoVo);
        return carInfoVos;
    }

    @Override
    public OrderTradeVo findTradeActivityAndCoupon(List<OrderDetail> orderDetailList, Long userId) {
        List<OrderDetailVo> orderDetailVoList = new ArrayList<>();
        OrderDetailVo orderDetailVo = new OrderDetailVo();
        orderDetailVo.setActivityRule(null);
        orderDetailVo.setOrderDetailList(orderDetailList);
        orderDetailVoList.add(orderDetailVo);

        OrderTradeVo orderTradeVo = new OrderTradeVo();
        orderTradeVo.setActivityReduceAmount(new BigDecimal(0));
        orderTradeVo.setCouponInfoList(null);
        orderTradeVo.setOrderDetailVoList(orderDetailVoList);
        return orderTradeVo;
    }
}
