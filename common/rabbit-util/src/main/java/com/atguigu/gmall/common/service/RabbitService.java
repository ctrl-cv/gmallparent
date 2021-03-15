package com.atguigu.gmall.common.service;

import com.atguigu.gmall.common.constant.MqConst;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class RabbitService {
    @Resource
    RabbitTemplate rabbitTemplate;

    public boolean sendMessage(String exchange,String routingKey,Object message){
        rabbitTemplate.convertAndSend(exchange,routingKey,message);
        return true;
    }

    public boolean sendDelayMessage(String exchange,String routingKey,Object message,int delayTime){
        rabbitTemplate.convertAndSend(exchange, routingKey, message,new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                message.getMessageProperties().setDelay(delayTime * 1000);
                return message;
            }
        });
        return true;
    }
}
