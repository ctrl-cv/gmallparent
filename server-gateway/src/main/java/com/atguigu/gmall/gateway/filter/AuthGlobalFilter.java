package com.atguigu.gmall.gateway.filter;


import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import javax.annotation.Resource;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter {

    @Resource
    RedisTemplate redisTemplate;

    AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Value("${authUrls.url}")
    private String authUrl;
    /**
     *
     * @param exchange serviceWeb 对象。能够获取到请求，能够获取到响应
     * @param chain 过滤器链
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        /*
        业务逻辑在此完成！
         */
        //  1.  获取到用户在浏览器访问的url 地址
        ServerHttpRequest request = exchange.getRequest();
        //  获取到路径  /api/product/inner/getSkuInfo/40
        String path = request.getURI().getPath();
        //  判断当前的路径是否符合我们判断的标准
        //  第一个参数匹配规则，第二个参数，要验证的path
        if (antPathMatcher.match("/**/inner/**",path)){
            //  符合规则，则不允许用户通过浏览器访问！
            //  做出响应
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }

        //  获取用户Id
        String userId = this.getUserId(request);
        //  获取临时用户Id
        String userTempId = this.getUserTempId(request);

        if ("-1".equals(userId)){
            //  做出响应
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }
        //  http://localhost/api/product/auth/hello
        //  前提是用户在未登录的时候访问/api/**/auth/**这样的控制器,那么提示你需要登录
        if (antPathMatcher.match("/api/**/auth/**",path)){
            //  判断用户是否登录 {登录成功之后，在缓存中存储了一个userId}
            if (StringUtils.isEmpty(userId)){
                //  做出响应
                ServerHttpResponse response = exchange.getResponse();
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        //  判断用户是否访问了黑白名单中的控制器
        //  authUrlsUrl=trade.html,myOrder.html,list.html 需要跳转到登录页面，进行登录
        String[] split = authUrl.split(",");
        //  判断
        if (split!=null && split.length>0){
            //  循环遍历 url 分别表示 trade.html,myOrder.html,list.html
            for (String url : split) {
                //  如果用户访问的url 中包含上述的任何一个，并且用户是处于未登录状态时，则需要提示用户进行登录
                //  path = http://list.gmall.com/list.html?category3Id=61
                //  String 字符串的indexOf(); 如果包含，则会返回对应的下标位置，如果不包含，则返回-1;
                if (path.indexOf(url)!=-1 && StringUtils.isEmpty(userId)){
                    //  跳转到登录页面，让用户进行登录
                    ServerHttpResponse response = exchange.getResponse();
                    //  设置一下状态码
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    //  当跳转的时候， http://passport.gmall.com/login.html?originUrl=http://list.gmall.com/list.html?category3Id=61
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://passport.gmall.com/login.html?originUrl="+request.getURI());
                    // 重定向
                    return response.setComplete();
                }
            }
        }
        //  如果上述验证通过的话，我们需要将用户Id 传递给后端各个微服务使用！
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)){

            if (!StringUtils.isEmpty(userId)){
                //  用户Id如何传递到后台给微服务
                //  ServerHttpRequest userId
                request.mutate().header("userId", userId).build();
            }
            if (!StringUtils.isEmpty(userTempId)){
                request.mutate().header("userTempId", userTempId).build();
            }
            //  ServerWebExchange
            //  ServerWebExchange exchange
            //  将userId 传递过去了。
            return chain.filter(exchange.mutate().request(request).build());
        }
        return chain.filter(exchange);
    }

    //  获取临时用户Id
    private String getUserTempId(ServerHttpRequest request) {
        String userTempId = "";

        List<String> list = request.getHeaders().get("userTempId");
        //  判断集合是否为空
        if (!StringUtils.isEmpty(list)){
            userTempId = list.get(0);
        }else {
            //  从cookie 中获取临时用户Id
            HttpCookie cookie = request.getCookies().getFirst("userTempId");
            if (cookie!=null){
                userTempId = cookie.getValue();
            }
        }
        return userTempId;
    }

    //  输出数据到页面
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        /*
        1.  只要输入的数据是谁 resultCodeEnum
        2.  数据编程字符串JSONObject.toJSONString(result)
        3.  利用response 对象输入到页面
            response.bufferFactory().wrap(str.getBytes());
            response.writeWith(Mono.just(dataBuffer));
         4. 设置一下响应规则
         */
        //  将用户提示信息输入到页面
        Result<Object> result = Result.build(null, resultCodeEnum);
        //  输入result
        String str = JSONObject.toJSONString(result);
        //  准备输入页面的响应
        DataBuffer dataBuffer = response.bufferFactory().wrap(str.getBytes());

        //  细节： 设置一下响应头 Content-Type ,设置响应的格式 字符集
        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");

        //  Publisher Mono<Void> jdk1.8 响应式编程出现的对象。
        Mono<DataBuffer> just = Mono.just(dataBuffer);
        return response.writeWith(just);
    }

    /**
     * 获取用户Id
     * @param request
     * @return
     */
    private String getUserId(ServerHttpRequest request) {
        //  用户Id 存储在缓存中
        //  key = user:login:704c79e7-501c-4379-ae2d-acd83867224e
        //  token 放在cookie中，放在header 中
        String token = "";
        //  从header 中获取token
        List<String> list = request.getHeaders().get("token");
        if (list!=null){
            //  token 中对应的数据只有一条！
            token = list.get(0);
        }else {
            //  从cookie 中获取token
            HttpCookie cookie = request.getCookies().getFirst("token");
            if (cookie!=null){
                token = cookie.getValue();
            }
        }
        //  判断是否获取到了token
        if(!StringUtils.isEmpty(token)){
            //  获取缓存中的数据
            String userKey = "user:login:"+token;
            //  获取到字符串之后
            String userJson = (String) redisTemplate.opsForValue().get(userKey);
            //  这个字符串对应的是哪个类 {JSONObject}
            JSONObject userJsonObject = JSONObject.parseObject(userJson);
            //  获取到用户的Ip 地址 ,防止token 被盗用，用ip 地址进行校验
            String ip = userJsonObject.getString("ip");
            //  缓存中的ip 与 当前系统所在的服务器ip地址一致的话。则返回用户Id，否则返回-1
            String gatwayIp = IpUtil.getGatwayIpAddress(request);
            if (ip.equals(gatwayIp)){
                //  获取到用户Id
                String userId = userJsonObject.getString("userId");
                //  返回用户Id
                return userId;
            }else {
                return "-1";
            }
        }
        return null;
    }
}
