package com.atguigu.gmall.mq.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/mq")
public class MqController {
    @Resource
    RabbitService rabbitService;

    @Resource
    RabbitTemplate rabbitTemplate;

    @GetMapping("sendConfirm")
    public Result sendConfirm(){
        //
        rabbitService.sendMessage("exchange.confirm", "routing.confirm666","男宾三位");
        return Result.ok();
    }
    @GetMapping("sendDeadLettle")
    public Result sendDeadLettle(){

        rabbitService.sendMessage(DeadLetterMqConfig.exchange_dead,DeadLetterMqConfig.routing_dead_1,"来人了，快跑！");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.err.println(simpleDateFormat.format(new Date()));
        return Result.ok();
    }
    @GetMapping("sendDelay")
    public Result sendDelay(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitTemplate.convertAndSend(DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay, "基于插件...",new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                message.getMessageProperties().setDelay(10000);
                System.out.println("发送消息的时间：\t"+sdf.format(new Date()));
                return message;
            }
        });
        return Result.ok();
    }

}
