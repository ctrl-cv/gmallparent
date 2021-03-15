package com.atguigu.gmall.payment.service;

public interface AlipayService {
    //创建订单
    String createaliPay(Long orderId);

    /**
     * 退款
     * @param orderId
     * @return
     */
    Boolean refund(Long orderId);

    /**
     * 关闭交易记录
     * @param orderId
     * @return
     */
    Boolean closePay(Long orderId);

    Boolean checkPayment(Long orderId);
}
