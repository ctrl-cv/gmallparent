package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;

import java.util.Map;

public interface PaymentService {

    void savePaymentInfo(OrderInfo orderInfo, String paymentType);
    //查询支付记录
    PaymentInfo getPaymentInfo(String outTradeNo, String name);

    void paySuccess(String outTradeNo, String name, Map<String, String> paramsMap);

    // 根据第三方交易编号，修改支付交易记录
    void updatePaymentInfo(String outTradeNo, String name,PaymentInfo paymentInfo);

    void closePayment(Long orderId);
}
