package com.atguigu.gmall.user.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/passport")
public class PassportApiController {
    @Resource
    UserService userService;

    @Resource
    RedisTemplate redisTemplate;


    /**
     * 登录
     * @param userInfo
     * @param request
     * @return
     */
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo, HttpServletRequest request){

        UserInfo info = userService.login(userInfo);
        if (info != null){
            String token = UUID.randomUUID().toString();
            Map<String, Object> map = new HashMap<>();
            map.put("nickName",info.getNickName());
            map.put("token",token);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId",info.getId().toString());
            jsonObject.put("ip", IpUtil.getIpAddress(request));

            redisTemplate.opsForValue().set(RedisConst.USER_LOGIN_KEY_PREFIX + token,jsonObject.toJSONString(),RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);
            return Result.ok(map);
        }else {
            return Result.fail().message("登录失败!");
        }
    }
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        String token = request.getHeader("token");
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX + token);
        return Result.ok();
    }
}
