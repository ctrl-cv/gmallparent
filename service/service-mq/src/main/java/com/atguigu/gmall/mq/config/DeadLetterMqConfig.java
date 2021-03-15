package com.atguigu.gmall.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.util.HashMap;

@Configuration
public class DeadLetterMqConfig {
    // 声明一些变量

    public static final String exchange_dead = "exchange.dead";
    public static final String routing_dead_1 = "routing.dead.1";
    public static final String routing_dead_2 = "routing.dead.2";
    public static final String queue_dead_1 = "queue.dead.1";
    public static final String queue_dead_2 = "queue.dead.2";

    // 定义交换机
    @Bean
    public DirectExchange exchange(){
        return new DirectExchange(exchange_dead,true,false);
    }

    //创建队列
    @Bean
    public Queue queue1(){
        HashMap<String, Object> map = new HashMap<>();
        map.put("x-message-ttl",10000);
        // 参数绑定 此处的key 固定值，不能随意写
        map.put("x-dead-letter-exchange",exchange_dead);
        map.put("x-dead-letter-routing-key",routing_dead_2);

        //第三个参数表示是否排外   true只在本次链接中访问
        return new Queue(queue_dead_1,true,false,false,map);
    }
    @Bean
    public Binding binding(){
        return BindingBuilder.bind(queue1()).to(exchange()).with(routing_dead_1);
    }

    //声明一个队列二
    @Bean
    public Queue queue2(){
        return new Queue(queue_dead_2,true,false,false);
    }

    @Bean
    public Binding binding2(){
        return BindingBuilder.bind(queue2()).to(exchange()).with(routing_dead_2);
    }
}
