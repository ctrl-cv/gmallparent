package com.atguigu.gmall.activity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.atguigu.gmall")
@ComponentScan(basePackages = "com.atguigu.gmall")
@EnableDiscoveryClient
public class ServiceActivityApplication {

   public static void main(String[] args) {
      SpringApplication.run(ServiceActivityApplication.class, args);
   }

}
