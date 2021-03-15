package com.atguigu.gmall.order.config;

import com.atguigu.gmall.common.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class OrderCanelMqConfig {

    @Resource
    RabbitTemplate rabbitTemplate;

    @Bean
    public Queue queue(){
        return new Queue(MqConst.QUEUE_ORDER_CANCEL,true);
    }

    @Bean
    public CustomExchange delayExchange(){
        Map<String, Object> map = new HashMap<>();
        map.put("x-delayed-type", "direct");
        return new CustomExchange(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,"x-delayed-message",true,false,map);
    }

    @Bean
    public Binding binding(){
        return BindingBuilder.bind(queue()).to(delayExchange()).with(MqConst.ROUTING_WARE_ORDER).noargs();
    }
}
