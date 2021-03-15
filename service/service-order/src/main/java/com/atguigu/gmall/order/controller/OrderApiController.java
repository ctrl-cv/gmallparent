package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("api/order")
public class OrderApiController {
    @Resource
    CartFeignClient cartFeignClient;

    @Resource
    UserFeignClient userFeignClient;

    @Resource
    OrderService orderService;

    @Resource
    ProductFeignClient productFeignClient;

    @Resource
    ThreadPoolExecutor threadPoolExecutor;


    @Resource
    RabbitService rabbitService;

    /**
     * 确认订单
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        List<CartInfo> cartInfoList = cartFeignClient.getCartCheckedList(userId);

        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);

        ArrayList<OrderDetail> detailArrayList = new ArrayList<>();

        for (CartInfo cartInfo : cartInfoList) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());
            orderDetail.setCreateTime(new Date());
            detailArrayList.add(orderDetail);
        }
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        orderInfo.sumTotalAmount();

        Map<String, Object> result  = new HashMap<>();
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", detailArrayList);

        // 保存总金额
        result.put("totalNum", detailArrayList.size());
        result.put("totalAmount", orderInfo.getTotalAmount());
        String tradeNo = orderService.getTradeNo(userId);
        result.put("tradeNo",tradeNo);
        return Result.ok(result);
    }

    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));
        String tradeNo = request.getParameter("tradeNo");
        boolean flag = orderService.checkTradeCode(userId, tradeNo);
        if (!flag){
            return Result.fail().message("不能重复提交订单！");
        }


        List<CompletableFuture> futureList = new ArrayList<>();
        List<String> errorList = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (!CollectionUtils.isEmpty(orderDetailList)) {
            for (OrderDetail orderDetail : orderDetailList) {
                CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                    boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                    if (!result) {
                        errorList.add(orderDetail.getSkuName() + "库存不足！");
                        //return Result.fail().message(orderDetail.getSkuName() + "库存不足！");
                    }
                }, threadPoolExecutor);

                futureList.add(checkStockCompletableFuture);
                CompletableFuture<Void> checkPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                    BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                    if (skuPrice.compareTo(orderDetail.getOrderPrice()) != 0) {
                        cartFeignClient.loadCartCache(userId);
                        //return Result.fail().message(orderDetail.getSkuName() + "价格有变动！");
                        errorList.add(orderDetail.getSkuName() + "价格有变动！");
                    }
                }, threadPoolExecutor);
                futureList.add(checkPriceCompletableFuture);
            }
        }
       CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();
        if (errorList.size() > 0){
            return Result.fail().message(StringUtils.join(errorList,","));
        }
        orderService.deleteTradeNo(userId);
        Long orderId = orderService.saveOrderInfo(orderInfo);
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,MqConst.ROUTING_ORDER_CANCEL,orderId,MqConst.DELAY_TIME);
        return Result.ok(orderId);
    }
    /**
     * 内部调用获取订单
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable Long orderId){
       return orderService.getOrderInfo(orderId);
    }
    //
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        String wareSkuMap = request.getParameter("wareSkuMap");

        List<OrderInfo> subOrderInfoList = orderService.orderSplit(orderId, wareSkuMap);
        List<Map> maps = new ArrayList<>();
        if (!CollectionUtils.isEmpty(subOrderInfoList)){
            for (OrderInfo orderInfo : subOrderInfoList) {
                Map map = orderService.initWareOrder(orderInfo);
                maps.add(map);
            }
        }
        return JSON.toJSONString(maps);
    }

    /**
     * 秒杀提交订单，秒杀订单不需要做前置判断，直接下单
     * @param orderInfo
     * @return
     */
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo){
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return orderId;
    }
}
