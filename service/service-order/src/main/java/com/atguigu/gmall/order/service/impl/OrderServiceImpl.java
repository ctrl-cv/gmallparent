package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.*;
import com.atguigu.gmall.order.mapper.*;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {
    @Resource
    OrderInfoMapper orderInfoMapper;

    @Resource
    OrderDetailMapper orderDetailMapper;

    @Resource
    RedisTemplate redisTemplate;

    @Resource
    RabbitService rabbitService;

    @Resource
    OrderDetailActivityMapper orderDetailActivityMapper;

    @Resource
    OrderDetailCouponMapper orderDetailCouponMapper;

    @Resource
    OrderStatusLogMapper orderStatusLogMapper;

    @Resource
    ActivityFeignClient activityFeignClient;


    @Value("${ware.url}")
    String WARE_URL;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrderInfo(OrderInfo orderInfo) {
        orderInfo.sumTotalAmount();
        orderInfo.setFeightFee(new BigDecimal(0));
        orderInfo.setCreateTime(new Date());
        orderInfo.setOperateTime(orderInfo.getCreateTime());
        //促销优惠总金额
        BigDecimal activityReduceAmount = orderInfo.getActivityReduceAmount(orderInfo);
        orderInfo.setActivityReduceAmount(activityReduceAmount);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());
        orderInfo.setOutTradeNo("ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000));
        orderInfo.setTradeBody("过年了，买点年货！");
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        orderInfoMapper.insert(orderInfo);
        // 计算购物项分摊的优惠减少金额，按比例分摊，退款时按实际支付金额退款

        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (!CollectionUtils.isEmpty(orderDetailList)) {
            for (OrderDetail orderDetail : orderDetailList) {
                orderDetail.setOrderId(orderInfo.getId());
                orderDetailMapper.insert(orderDetail);
            }
        }
        return orderInfo.getId();
    }


    /**
     * 记录订单与促销活动和优惠券的关联信息
     * @param orderInfo
     * @param skuIdToOrderDetailIdMap
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveActivityAndCouponRecord(OrderInfo orderInfo, Map<Long, Long> skuIdToOrderDetailIdMap) {
        //记录促销活动
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        if(!CollectionUtils.isEmpty(orderDetailVoList)) {
            for(OrderDetailVo orderDetailVo : orderDetailVoList) {
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                if(null != activityRule) {
                    for(Long skuId : activityRule.getSkuIdList()) {
                        OrderDetailActivity orderDetailActivity = new OrderDetailActivity();
                        orderDetailActivity.setOrderId(orderInfo.getId());
                        orderDetailActivity.setOrderDetailId(skuIdToOrderDetailIdMap.get(skuId));
                        orderDetailActivity.setActivityId(activityRule.getActivityId());
                        orderDetailActivity.setActivityRule(activityRule.getId());
                        orderDetailActivity.setSkuId(skuId);
                        orderDetailActivity.setCreateTime(new Date());
                        orderDetailActivityMapper.insert(orderDetailActivity);
                    }
                }
            }
        }

        // 记录优惠券
        // 是否更新优惠券状态
        Boolean isUpdateCouponStatus = false;
        CouponInfo couponInfo = orderInfo.getCouponInfo();
        if (couponInfo != null){
            List<Long> skuIdList = couponInfo.getSkuIdList();
            for (Long skuId : skuIdList) {
                OrderDetailCoupon orderDetailCoupon = new OrderDetailCoupon();
                orderDetailCoupon.setOrderId(orderInfo.getId());
                orderDetailCoupon.setOrderDetailId(skuIdToOrderDetailIdMap.get(skuId));
                orderDetailCoupon.setCouponId(couponInfo.getId());
                orderDetailCoupon.setSkuId(skuId);
                orderDetailCoupon.setCreateTime(new Date());
                orderDetailCouponMapper.insert(orderDetailCoupon);

                // 更新优惠券使用状态
                if (!isUpdateCouponStatus){
                    activityFeignClient.updateCouponInfoUseStatus(couponInfo.getId(),orderInfo.getUserId(),orderInfo.getId());
                }
                isUpdateCouponStatus = true;
            }
        }
    }

    @Override
    public String getTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String tradeNo = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(tradeNoKey,tradeNo);
        return tradeNo;
    }

    @Override
    public boolean checkTradeCode(String userId, String tradeNo) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String redisTradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);
        return tradeNo.equals(redisTradeNo);
    }

    @Override
    public void deleteTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";

        redisTemplate.delete(tradeNoKey);
    }

    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        // 远程调用http://localhost:9001/hasStock?skuId=10221&num=2
        String result = HttpClientUtil.doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(result);
    }

    //处理过期订单
    @Override
    public void execExpiredOrder(Long orderId) {
        updateOrderStatus(orderId,ProcessStatus.CLOSED);

        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
    }

    @Override
    public void execExpiredOrder(Long orderId, String flag) {
        updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if ("2".equals(flag)){
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        }
    }

    @Override
    public void updateOrderStatus(Long orderId,ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }

    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        QueryWrapper<OrderDetail> orderInfoQueryWrapper = new QueryWrapper<>();
        orderInfoQueryWrapper.eq("order_id", orderId);
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo != null) {
            List<OrderDetail> orderDetails = orderDetailMapper.selectList(orderInfoQueryWrapper);
            orderInfo.setOrderDetailList(orderDetails);
        }
        return orderInfo;
    }

    @Override
    public void sendOrderStatus(Long orderId) {
        this.updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);

        String wareJson = this.initWareOrder(orderId);

        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK,MqConst.ROUTING_WARE_STOCK,wareJson);
    }

    private String initWareOrder(Long orderId) {
        OrderInfo orderInfo = this.getOrderInfo(orderId);
        Map map = this.initWareOrder(orderInfo);
        return JSON.toJSONString(map);
    }

    @Override
    public Map initWareOrder(OrderInfo orderInfo) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId",orderInfo.getId());
        map.put("consignee",orderInfo.getConsignee());
        map.put("consigneeTel",orderInfo.getConsigneeTel());
        map.put("orderComment",orderInfo.getOrderComment());
        map.put("orderBody",orderInfo.getTradeBody());
        map.put("deliveryAddress",orderInfo.getDeliveryAddress());
        map.put("paymentWay","2");
        map.put("wareId",orderInfo.getWareId());
        List<Map> maps = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (!CollectionUtils.isEmpty(orderDetailList)){
            for (OrderDetail orderDetail : orderDetailList) {
                Map<Object, Object> hashMap = new HashMap<>();
                hashMap.put("skuId",orderDetail.getSkuId());
                hashMap.put("skuNum",orderDetail.getSkuNum());
                hashMap.put("skuName",orderDetail.getSkuName());
                maps.add(hashMap);
            }
        }
        map.put("details",maps);
        return map;
    }

    @Override
    public List<OrderInfo> orderSplit(String orderId, String wareSkuMap) {
        ArrayList<OrderInfo> orderInfoArrayList = new ArrayList<>();
        /*
    1.  先获取到原始订单 107
    2.  将wareSkuMap 转换为我们能操作的对象 [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        方案一：class Param{
                    private String wareId;
                    private List<String> skuIds;
                }
        方案二：看做一个Map mpa.put("wareId",value); map.put("skuIds",value)

    3.  创建一个新的子订单 108 109 。。。
    4.  给子订单赋值
    5.  保存子订单到数据库
    6.  修改原始订单的状态
    7.  测试
     */
        OrderInfo orderInfoOrigin = this.getOrderInfo(Long.parseLong(orderId));
        List<Map>  maps = JSON.parseArray(wareSkuMap,Map.class);
        if (maps != null){
            for (Map map : maps) {
                String wareId = (String) map.get("wareId");
                List<String> skuIds = (List<String>) map.get("skuIds");
                OrderInfo subOrderInfo = new OrderInfo();
                // 属性拷贝
                BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);
                // 防止主键冲突
                subOrderInfo.setId(null);

                subOrderInfo.setParentOrderId(Long.parseLong(orderId));
                // 赋值仓库Id
                subOrderInfo.setWareId(wareId);

                // 子订单号
                String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
                subOrderInfo.setOutTradeNo(outTradeNo);

                // 计算子订单的金额: 必须有订单明细
                // 获取到子订单明细
                // 声明一个集合来存储子订单明细
                List<OrderDetail> subOrderDetailList = new ArrayList<>();
                // 表示主主订单明细中获取到子订单的明细
                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();

                if (!CollectionUtils.isEmpty(orderDetailList)){
                    for (OrderDetail orderDetail : orderDetailList) {
                        for (String skuId : skuIds) {
                            if (Long.parseLong(skuId) == orderDetail.getSkuId().longValue()){
                                OrderDetail subOrderDetail = new OrderDetail();
                                BeanUtils.copyProperties(orderDetail,subOrderDetail);
                                subOrderDetail.setId(null);
                                // 将订单明细添加到集合
                                subOrderDetailList.add(subOrderDetail);
                            }
                        }
                    }
                }
                subOrderInfo.setOrderDetailList(subOrderDetailList);
                // 重新计算子订单和订单明细金额
                BigDecimal totalAmount = new BigDecimal("0");
                BigDecimal originalTotalAmount = new BigDecimal("0");
                BigDecimal couponAmount = new BigDecimal("0");
                BigDecimal activityReduceAmount = new BigDecimal("0");
                for (OrderDetail subOrderDetail : subOrderDetailList) {
                    BigDecimal skuTotalAmount = subOrderDetail.getOrderPrice().multiply(new BigDecimal(subOrderDetail.getSkuNum()));
                   originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                    totalAmount = totalAmount.add(skuTotalAmount).subtract(subOrderDetail.getSplitCouponAmount()).subtract(subOrderDetail.getSplitActivityAmount());
                    couponAmount = couponAmount.add(subOrderDetail.getSplitCouponAmount());
                    activityReduceAmount = activityReduceAmount.add(subOrderDetail.getSplitActivityAmount());
                }

               // subOrderInfo.sumTotalAmount();
                subOrderInfo.setOriginalTotalAmount(originalTotalAmount);
                subOrderInfo.setCouponAmount(couponAmount);
                subOrderInfo.setActivityReduceAmount(activityReduceAmount);
                subOrderInfo.setFeightFee(new BigDecimal(0));
                orderInfoMapper.insert(subOrderInfo);
//                this.saveOrderInfo(subOrderInfo);
//                orderInfoArrayList.add(subOrderInfo);
                //保存子订单明细
                for (OrderDetail subOrderDetail : subOrderDetailList) {
                    subOrderDetail.setOrderId(subOrderInfo.getId());
                    orderDetailMapper.insert(subOrderDetail);
                }

               // 保存订单状态记录
                List<OrderStatusLog> orderStatusLogList = orderStatusLogMapper.selectList(new QueryWrapper<OrderStatusLog>().eq("order_id", orderId));
                for (OrderStatusLog orderStatusLog : orderStatusLogList) {
                    OrderStatusLog subOrderStatusLog  = new OrderStatusLog();
                    BeanUtils.copyProperties(orderStatusLog, subOrderStatusLog);
                    subOrderStatusLog.setId(null);
                    subOrderStatusLog.setOrderId(subOrderInfo.getId());
                    orderStatusLogMapper.insert(subOrderStatusLog);
                }
                // 将子订单添加到集合中！
                orderInfoArrayList.add(subOrderInfo);
            }
        }
        this.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.SPLIT);
        return orderInfoArrayList;
    }

    @Override
    public void saveOrderStatusLog(Long orderId, String orderStatus) {
        // 记录订单状态
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOperateTime(new Date());
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(orderStatus);
        orderStatusLogMapper.insert(orderStatusLog);
    }

}
