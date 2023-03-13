package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全局拦截器
 */
public class RefreshTokenTnterceptor implements HandlerInterceptor {

    //只能使用构造器注入
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenTnterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
     * 线程使用前
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)  {
        //1获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        //2获取Redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY+ token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key );
        //3判断用户是否存在
        if(userMap.isEmpty()){
            return true;
        }
        //5将查询到的hash数据转换为userDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        //6存在，保存信息在ThreadLocal
        UserHolder.saveUser(userDTO);

        //7放行并刷新TOKEN有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    /**、
     * 线程使用后移出用户
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        //线程使用完毕要移除用户
        UserHolder.removeUser();
    }
}
