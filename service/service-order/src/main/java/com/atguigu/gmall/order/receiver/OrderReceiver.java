package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;


@Component
public class OrderReceiver {

    @Resource
    OrderService orderService;

    @Resource
    PaymentFeignClient paymentFeignClient;

    @Resource
    RabbitService rabbitService;

    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel){
        try {
            if (orderId != null){
                OrderInfo orderInfo = orderService.getById(orderId);
                if (orderInfo != null && "UNPAID".equals(orderInfo.getOrderStatus()) && "UNPAID".equals(orderInfo.getProcessStatus())){
                    //  关闭订单
                    //orderService.execExpiredOrder(orderId);
                    PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                    if(paymentInfo != null && "UNPAID".equals(paymentInfo.getPaymentStatus())){
                        Boolean flag = paymentFeignClient.checkPayment(orderId);
                        if (flag){
                            Boolean result = paymentFeignClient.closePay(orderId);
                            if (result){
                                orderService.execExpiredOrder(orderId,"2");
                            }else {
                                rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,orderId);
                            }
                        }else {
                            orderService.execExpiredOrder(orderId,"2");
                        }
                    }else {
                        orderService.execExpiredOrder(orderId,"1");
                    }
                }
            }
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,true);
            e.printStackTrace();
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY,durable = "true",autoDelete = "false"),
    exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void updateOrderStatus(Long orderId,Message message,Channel channel) throws IOException {
        try {
            if (orderId != null) {
                OrderInfo orderInfo = orderService.getById(orderId);
                if (orderInfo != null && "UNPAID".equals(orderInfo.getOrderStatus()) && "UNPAID".equals(orderInfo.getProcessStatus())) {
                    orderService.updateOrderStatus(orderId, ProcessStatus.PAID);

                    orderService.sendOrderStatus(orderId);
                }
            }
        } catch (Exception e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            e.printStackTrace();
            return;
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_WARE_ORDER)
    public void updateOrder(String jsonStr,Message message,Channel channel){
        if (!StringUtils.isEmpty(jsonStr)){
            Map map = JSON.parseObject(jsonStr, Map.class);
            String status = (String) map.get("status");
            String orderId = (String) map.get("orderId");

            if ("DEDUCTED".equals(status)){
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.WAITING_DELEVER);
            }else {
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.STOCK_EXCEPTION);
            }
        }
     channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
