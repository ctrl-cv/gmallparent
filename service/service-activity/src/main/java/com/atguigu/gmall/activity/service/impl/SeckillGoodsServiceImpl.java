package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {

    @Resource
    RedisTemplate redisTemplate;

    @Resource
    SeckillGoodsMapper seckillGoodsMapper;

    @Override
    public List<SeckillGoods> findAll() {
        return redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
    }

    @Override
    public SeckillGoods getSeckillGoods(Long skuId) {
        return (SeckillGoods)redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
    }

    @Override
    public void seckillOrder(String userId, Long skuId) {

        String state = (String) CacheHelper.get(skuId.toString());
        if (StringUtils.isEmpty(state) || "0".equals(state)) return;
        //判断是否下单
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(RedisConst.SECKILL_USER + userId, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        if (!flag)return;
        String redisSkuId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId.toString()).rightPop();
        if (StringUtils.isEmpty(redisSkuId)){
            redisTemplate.convertAndSend("seckillpush",skuId + ":0");
            return;
        }
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setNum(1);
        orderRecode.setOrderStr(MD5.encrypt(userId));
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(this.getSeckillGoods(skuId));

        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(userId,orderRecode);

        this.updateStock(skuId);
    }

    @Override
    public Result checkOrder(Long skuId, String userId) {
        Boolean isExist = redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
       if (isExist){
           Boolean aBoolean = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
           if (aBoolean){
               OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
               return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
           }
       }
        Boolean falg = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
       if (falg){
           String orderId = (String) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);

           return Result.build(orderId,ResultCodeEnum.SECKILL_ORDER_SUCCESS);
       }
        String state = (String) CacheHelper.get(skuId.toString());
       if ("0".equals(state) || StringUtils.isEmpty(state)){
           return Result.build(null,ResultCodeEnum.SECKILL_FAIL);
       }
       return Result.build(null,ResultCodeEnum.SECKILL_RUN);
    }

    private void updateStock(Long skuId) {
        Long stockCount = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId.toString()).size();
        if (stockCount %2 == 0){
            SeckillGoods seckillGoods = this.getSeckillGoods(skuId);
            seckillGoods.setStockCount(stockCount.intValue());
            seckillGoodsMapper.updateById(seckillGoods);

            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(),stockCount);
        }
    }
}
