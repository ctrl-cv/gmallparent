package com.atguigu.gmall.activity.service;

import java.util.Map;

public interface ActivityService {
    Map<String, Object> findActivityAndCoupon(Long skuId, long userId);
}
