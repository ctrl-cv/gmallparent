package com.atguigu.gmall.product.service.impl;

import com.alibaba.nacos.client.utils.StringUtils;
import com.atguigu.gmall.product.service.TestService;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {
    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public void testLock() {

        RLock lock = redissonClient.getLock("lock");

        lock.lock(1,TimeUnit.SECONDS);

        String value = redisTemplate.opsForValue().get("num");

        if (StringUtils.isEmpty(value)){
            return;
        }
        int num = Integer.parseInt(value);

        redisTemplate.opsForValue().set("num",String.valueOf(++num));
//        String uuid = UUID.randomUUID().toString();
//        Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", uuid,2, TimeUnit.SECONDS);
//
//
//        if (lock) {
//            String value = redisTemplate.opsForValue().get("num");
//            if (StringUtils.isBlank(value)) {
//                return;
//            }
//            int num = Integer.parseInt(value);
//
//            redisTemplate.opsForValue().set("num", String.valueOf(++num));
//
//            String script="if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
//            DefaultRedisScript<Long> redisScript  = new DefaultRedisScript<>();
//            redisScript.setScriptText(script);
//            redisScript.setResultType(Long.class);
////            if (uuid.equals(redisTemplate.opsForValue().get("lock"))){
////                redisTemplate.delete("lock");
////            }
//            redisTemplate.execute(redisScript, Arrays.asList("lock"),uuid);
//        } else {
//            try {
//                Thread.sleep(100);
//                testLock();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
    }
}
