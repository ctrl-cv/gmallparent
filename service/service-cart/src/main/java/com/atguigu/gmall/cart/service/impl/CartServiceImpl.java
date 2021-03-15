package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author mqx
 * @date 2021-2-26 09:44:30
 */
@Service
public class CartServiceImpl implements CartService {

    @Resource
    private CartInfoMapper cartInfoMapper;

    @Resource
    private ProductFeignClient productFeignClient;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private CartAsyncService cartAsyncService;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        /*
            1.  先判断当前购物车中是否有该商品
                zs  1001  1 + 1
                ls  1001  1 + 1
                select * from cart_info where sku_id=skuId and user_id=userId
                true: 则商品数量相加
                false: 直接添加到数据库
            2.  还需要存储在缓存中
         */
        //  操作缓存，必须了解使用的数据类型，以及key！ 使用hash 存储
        //  hset(key,field,value); hget(key,field);key=user:userId:cart  field = skuId  value=cartInfo;
        //  定义购物车key
        String cartKey = this.getCartKey(userId);
        //  判断缓存中是否有购物车的key
        if (!redisTemplate.hasKey(cartKey)){
            //  从数据查询到数据，并放入缓存
            this.loadCartCache(userId);
        }
        //  代码在走到这个位置的时候：缓存中一定有数据！
        //        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        //        queryWrapper.eq("sku_id",skuId);
        //        queryWrapper.eq("user_id",userId);
        //        CartInfo cartInfoExist = cartInfoMapper.selectOne(queryWrapper);
        //  可以直接查询缓存！
        //  hget(key,field);
        CartInfo cartInfoExist = (CartInfo)redisTemplate.opsForHash().get(cartKey,skuId.toString());

        //  说明添加的商品在购物车中存在
        if (cartInfoExist!=null){
            //  数量相加
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            //  给实时价格赋值
            cartInfoExist.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            //  修改更新时间
            cartInfoExist.setUpdateTime(new Timestamp(new Date().getTime()));
            //  如果第一次添加 默认被选中1 ，用户更改状态 0 ，此时用户第二次添加，这个使用
            cartInfoExist.setIsChecked(1);
            //  将修改之后的数据保存到数据库
            //  cartInfoMapper.updateById(cartInfoExist);
            cartAsyncService.updateCartInfo(cartInfoExist);
            //  还需要操作缓存.....
        }else {
            //  直接添加到购物车中
            CartInfo cartInfo = new CartInfo();
            //  赋值cartInfo
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            cartInfo.setCartPrice(skuInfo.getPrice());
            //  实时价格初始化赋值
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
            cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));

            //  cartInfoMapper.insert(cartInfo);
            cartAsyncService.saveCartInfo(cartInfo); // 给其他线程执行了。跳过了这个地方，id 主键自增。
            //  操作缓存！
            cartInfoExist=cartInfo;

        }

        //  将数据放入缓存
        redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoExist);
        //  设置购物车的过期时间：灵活代码！
        this.setCartKeyExpire(cartKey);
    }

    @Override
    public List<CartInfo> getCartInfoList(String userId, String userTempId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        //  判断userId 登录的用户Id
        if (StringUtils.isEmpty(userId)){
            //  应该根据临时用户Id 查询数据
            cartInfoList = this.getCartInfoList(userTempId);
        }
        //  判断
        if(!StringUtils.isEmpty(userId)){
            //  有可能发生合并购物车
            if (StringUtils.isEmpty(userTempId)){
                cartInfoList = this.getCartInfoList(userId);
            }
            List<CartInfo> cartInfoTempList = this.getCartInfoList(userTempId);
            //  判断临时购物车数据
            if (!CollectionUtils.isEmpty(cartInfoTempList)){
                //  会发生合并操作
                cartInfoList = this.mergeToCartList(cartInfoTempList,userId);
                //  删除临时购物车数据
                this.deleteCartList(userTempId);
            }
            //  后续还会有其他操作，待完善
            if (CollectionUtils.isEmpty(cartInfoTempList)){
                cartInfoList = this.getCartInfoList(userId);
            }
        }

        return cartInfoList;
    }

    /**
     * 删除购物车数据
     * @param userTempId
     */
    private void deleteCartList(String userTempId) {
        //  删除购物车
        cartAsyncService.deleteCartInfo(userTempId);
        //  获取到购物车的key
        String cartKey = this.getCartKey(userTempId);
        if (redisTemplate.hasKey(cartKey)){
            redisTemplate.delete(cartKey);
        }
    }

    /**
     * 合并购物车
     * @param cartInfoNoLoginList
     * @param userId
     * @return
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoNoLoginList, String userId) {
         /*
        demo1:
            登录：
                37 1
                38 1
            未登录：
                37 1
                38 1
                39 1
            合并之后的数据
                37 2
                38 2
                39 1
         */
         // 通过用户Id 查找登录时购物车数据：
        List<CartInfo> cartInfoLoginList = this.getCartInfoList(userId);
        //  将登录时的购物车集合进行转换
        //  map key=skuId， value=cartInfo
        Map<Long, CartInfo> longCartInfoMap = cartInfoLoginList.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));
        //  循环判断
        for (CartInfo cartInfoNoLogin : cartInfoNoLoginList) {
            //  通过这个未登录的对象，来获取到skuId
            Long skuId = cartInfoNoLogin.getSkuId();
            //  两个集合是否有合并的商品：依据条件是skuId
            //  合并的商品是skuId = 37,38
            if (longCartInfoMap.containsKey(skuId)){
                //  合并需要的操作是将skuNum 相加，同时更新一下更新时间
                CartInfo cartInfoLogin = longCartInfoMap.get(skuId);
                //  37 skuNum = 1+1  cartInfoLogin.skuNum = 2
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum()+cartInfoNoLogin.getSkuNum());
                //cartInfoLogin.setUpdateTime(new Timestamp(new Date().getTime()));

                //  合并方式是未登录向登录合并：只需要判断未登录的选中状态即可！
                if (cartInfoNoLogin.getIsChecked().intValue()==1){
                    //  只判断登录的状态为0的时候进行改变状态
                    if (cartInfoLogin.getIsChecked().intValue()==0){
                        cartInfoLogin.setIsChecked(1);
                    }
                    //  将登录的选中状态也变为1
                    //  cartInfoLogin.setIsChecked(1);
                }
                //  更新方法
                //  cartInfoMapper.update(); //  这个方法万能，没有id 有其他条件也能更新
                //  cartInfoMapper.updateById(); // 必须有Id
                //  cartAsyncService.updateCartInfo(cartInfoLogin); // 使用异步：此时会继续往后面执行！
                //  此处使用同步！
                //  按照userId，skuId 进行更新
                QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("user_id",userId);
                queryWrapper.eq("sku_id",cartInfoLogin.getSkuId());
                //  改成同步更新数据！
                cartInfoMapper.update(cartInfoLogin,queryWrapper);
            }else {
                //  处理的skuId = 39
                //  未登录的时候你的用户Id 是临时的。合并之后，就应该变成登录的userId
                cartInfoNoLogin.setUserId(userId);
                cartInfoNoLogin.setCreateTime(new Timestamp(new Date().getTime()));
                cartInfoNoLogin.setUpdateTime(new Timestamp(new Date().getTime()));
                //  cartInfoMapper.insert(cartInfoNoLogin); 同步
                //  cartAsyncService.saveCartInfo(cartInfoNoLogin); // 异步方法，此时会继续往后面执行！
                cartInfoMapper.insert(cartInfoNoLogin);
            }
        }

        //  这个方法是合并方法：最终需要获取到的数据是 37，38，39 这三条数据！
        //  最后统统查询出来并返回！
        List<CartInfo> cartInfoList = this.loadCartCache(userId);

        return cartInfoList;
    }

    @Override
    public List<CartInfo> getCartInfoList(String userId) {
        //  声明一个集合
        List<CartInfo> cartInfoList = new ArrayList<>();
        //  判断一下传递过来的参数是否为空
        if (StringUtils.isEmpty(userId)) return cartInfoList;
        //  不为空的情况下： 添加购物车的时候，同步的缓存，异步的数据库。 缓存中有数据
        //  从缓存中获取数据
        //  存储的hash 数据类型
        String cartKey = this.getCartKey(userId);
        //  hset(key,field,value) hget(key,field);只是获取到某一个数据 并发所有！
        //  hvals(key);
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        //  判断
        if (!CollectionUtils.isEmpty(cartInfoList)){
            //  是否需要对这个集合进行操作呢? 需要进行排序 按照商品的更新时间进行排序！
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    return DateUtil.truncatedCompareTo(o2.getUpdateTime(),o1.getUpdateTime(), Calendar.SECOND);
                }
            });
            return cartInfoList;
        }else {
            //  如果缓存中没有数据的时候，{有时候缓存到期了} 从数据库获取数据。
            cartInfoList = this.loadCartCache(userId);
            return cartInfoList;
        }
    }

    @Override
    public void checkCart(String userId, Long skuId, Integer isChecked) {
        //  更新数据库
        cartAsyncService.checkCart(userId,skuId,isChecked);

        //  更新缓存
        //  获取到购物车的key
        String cartKey = this.getCartKey(userId);
        //  知道具体更新的是哪个skuId
        //  hget(key,field)
        //  CartInfo cartInfoUpd = redisTemplate.opsForHash().get(cartKey, skuId.toString());
        //  cartInfoUpd.setIsChecked(isChecked);
        //  redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoUpd);

        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        Boolean flag = boundHashOperations.hasKey(skuId.toString());
        if (flag){
            //  说明要更新的skuId，在购物车列表中存在
            CartInfo cartInfoUpd = (CartInfo) boundHashOperations.get(skuId.toString());
            cartInfoUpd.setIsChecked(isChecked);

            // 将更新的cartInfo 在存然缓存
            boundHashOperations.put(skuId.toString(),cartInfoUpd);
            //  设置一下购物车key 的过期时间
            this.setCartKeyExpire(cartKey);
        }
    }

    @Override
    public void deleteCart(String userId, Long skuId) {
        //  删除数据库
        cartAsyncService.deleteCart(userId,skuId);
        //  删除缓存
        //  先获取key
        String cartKey = this.getCartKey(userId);
        //  通过cartKey 获取集合数据
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        //  继续判断是否有当前的商品
        Boolean flag = boundHashOperations.hasKey(skuId.toString());
        if (flag){
            //  删除数据
            boundHashOperations.delete(skuId.toString());
        }
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoCheckedList = new ArrayList<>();
        //  在缓存查询即可！
        //  获取购物车的key
        String cartKey = this.getCartKey(userId);
        //  获取购物车列表  hvals(key);
        //  List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        List<CartInfo> cartInfoList = redisTemplate.boundHashOps(cartKey).values();
        //  循环遍历
        //  方式一：
        //        if (!CollectionUtils.isEmpty(cartInfoList)){
        //            for (CartInfo cartInfo : cartInfoList) {
        //                //  当前的ischecked = 1
        //                if (cartInfo.getIsChecked().intValue()==1){
        //                    cartInfoCheckedList.add(cartInfo);
        //                }
        //            }
        //        }
        //  return cartInfoCheckedList;
        //  方式二：
        //  Predicate boolean test(T t)
        List<CartInfo> collect = cartInfoList.stream().filter((cartInfo -> {
            return cartInfo.getIsChecked().intValue() == 1;
        })).collect(Collectors.toList());
        //  返回数据
        return collect;
    }


    //  根据用户Id 查询数据库的购物车列表，并且放入缓存。
    public List<CartInfo> loadCartCache(String userId) {
        //  通过用户Id 查询数据库中的数据
        //  select * from cart_info where user_id = ?  异步方法：此时还没有执行，这个时候数据库的数据还是原来的！
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(new QueryWrapper<CartInfo>().eq("user_id", userId));
        //  如果是空，直接返回
        if (CollectionUtils.isEmpty(cartInfoList)) return cartInfoList;

        //  从数据库查询出来之后，同样按照更新时间进行排序
        //        cartInfoList.sort(new Comparator<CartInfo>() {
        //            @Override
        //            public int compare(CartInfo o1, CartInfo o2) {
        //                return DateUtil.truncatedCompareTo(o2.getUpdateTime(),o1.getUpdateTime(), Calendar.SECOND);
        //            }
        //        });

        //  hset(key,field,value); hmset(key,vlaue);
        //  获取购物车的key
        String cartKey = this.getCartKey(userId);
        //  声明一个map
        HashMap<String, CartInfo> cartInfoHashMap = new HashMap<>();
        //  将集合循环并放入缓存
        for (CartInfo cartInfo : cartInfoList) {
            //  细节：前提是缓存中没有数据，有可能商品价格会发生变化。
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));
            //  单个赋值
            //  redisTemplate.opsForHash().put(cartKey,cartInfo.getSkuId(),cartInfo);
            cartInfoHashMap.put(cartInfo.getSkuId().toString(),cartInfo);
        }
        //  一次性赋值全部
        redisTemplate.opsForHash().putAll(cartKey,cartInfoHashMap);
        //  重新设置一下过期时间
        this.setCartKeyExpire(cartKey);

        return cartInfoList;
    }

    //  设置购物车的过期时间
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    //  定义购物车的key
    private String getCartKey(String userId) {
       return RedisConst.USER_KEY_PREFIX+userId+RedisConst.USER_CART_KEY_SUFFIX;
    }
}
