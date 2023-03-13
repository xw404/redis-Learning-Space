package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全局拦截器
 */
public class LoginTnterceptor implements HandlerInterceptor {

    /**
     * 线程使用前
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)  {
//        //1获取请求头中的token
//        String token = request.getHeader("authorization");
//        if(StrUtil.isBlank(token)){
//            //不存在直接拦截  返回401
//            response.setStatus(401);
//            return false;
//        }
//        //2获取Redis中的用户
//        String key = RedisConstants.LOGIN_USER_KEY+ token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key );
//        //3判断用户是否存在
//        if(userMap.isEmpty()){
//            //不存在直接拦截  返回401
//            response.setStatus(401);
//            return false;
//        }
//        //5将查询到的hash数据转换为userDTO对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
//        //6存在，保存信息在ThreadLocal
//        UserHolder.saveUser(userDTO);
//
//        //7放行并刷新TOKEN有效期
//        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//        return true;
        //判断是否需要去拦截（ThreadLocal中是否有用户）

        if(UserHolder.getUser()==null){
            //没有。拦截
            response.setStatus(401);
            //拦截
            return false;
        }
        //用用户，放行
        return true;
    }
}
