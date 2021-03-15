package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseAttrValue;
import com.atguigu.gmall.model.product.BaseSaleAttr;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface BaseTrademarkService extends IService<BaseTrademark> {
   IPage<BaseTrademark> getPage(Page<BaseTrademark> pageParam);


    List<BaseTrademark> findBaseTrademarkByKeyword(String keyword);

    List<BaseTrademark> findBaseTrademarkByTrademarkIdList(List<Long> trademarkIdList);
}
