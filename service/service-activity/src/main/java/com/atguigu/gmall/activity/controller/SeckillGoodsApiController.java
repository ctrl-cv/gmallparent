package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.catalina.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsApiController {
    @Resource
    SeckillGoodsService seckillGoodsService;

    @Resource
    RabbitService rabbitService;

    @Resource
    RedisTemplate redisTemplate;

    @Resource
    UserFeignClient userFeignClient;

    @Resource
    OrderFeignClient orderFeignClient;


    @GetMapping("/findAll")
    public Result findAll(){
        return Result.ok(seckillGoodsService.findAll());
    }

    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable Long skuId){
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable Long skuId, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        String skuIdStr = request.getParameter("skuIdStr");
        if (!skuIdStr.equals(MD5.encrypt(userId))){
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        String state = (String) CacheHelper.get(skuId.toString());
        if ("0".equals(state)){
            return Result.build(null,ResultCodeEnum.SECKILL_FINISH);
        }else if (StringUtils.isEmpty(state)){
            return Result.build(null,ResultCodeEnum.SECKILL_ILLEGAL);
        }else {
            UserRecode userRecode = new UserRecode();
            userRecode.setSkuId(skuId);
            userRecode.setUserId(userId);

            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER,MqConst.ROUTING_SECKILL_USER,userRecode);
            return Result.ok();
        }
    }

    //  ????????????????????????
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable Long skuId, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        //  ??????????????????????????????????????????????????????????????????
        //  ??????skuId ??????????????????
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        if (seckillGoods != null){
            //  ??????????????????????????????
            Date date = new Date();
            //  ????????????????????????????????????????????????????????????????????????????????????
            if (DateUtil.dateCompare(seckillGoods.getStartTime(),date) &&
                    DateUtil.dateCompare(date,seckillGoods.getEndTime())){
                //  ???????????????
                String skuIdStr = MD5.encrypt(userId);
                return Result.ok(skuIdStr);
            }
        }
        return Result.fail().message("?????????????????????!");
    }

    //  ???????????????
    @GetMapping("/auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable Long skuId,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        //  ?????????????????????
        return seckillGoodsService.checkOrder(skuId,userId);
    }

    /**
     * ??????????????????
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);

        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);

        if (orderRecode == null){
            return Result.fail().message("????????????");
        }
        List<OrderDetail> orderDetails = new ArrayList<>();

        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();

        List<UserAddress> userAddressList= userFeignClient.findUserAddressListByUserId(userId);

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setCreateTime(new Date());
        orderDetail.setSkuNum(1);
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());

        orderDetails.add(orderDetail);

//        OrderInfo orderInfo = new OrderInfo();
        //orderInfo.sumTotalAmount();
//        orderInfo.setOrderDetailList(orderDetails);

        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", orderDetails);
        // ???????????????
        result.put("totalAmount", seckillGoods.getCostPrice());
        result.put("totalNum",1);
        return Result.ok(result);

    }

    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));
        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if (orderId == null){
            return Result.fail().message("??????????????????????????????");
        }
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);

        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId,orderId.toString());

        return Result.ok(orderId);
    }

}
