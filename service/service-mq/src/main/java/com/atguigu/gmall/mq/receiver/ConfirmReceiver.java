package com.atguigu.gmall.mq.receiver;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConfirmReceiver {

    //监听消息
    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "queue.confirm",durable = "true",autoDelete = "false"),
    exchange = @Exchange(value = "exchange.confirm"),
    key = {"routing.confirm"}))
    public void getMsg(String msg, Message message, Channel channel) throws IOException {

        //System.out.println(msg);
        byte[] body = message.getBody();
        System.out.println("接收的消息666：\t"+ new String(body));
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
