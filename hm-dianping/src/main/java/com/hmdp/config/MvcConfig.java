package com.hmdp.config;

import com.hmdp.utils.LoginTnterceptor;
import com.hmdp.utils.RefreshTokenTnterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;


@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 添加拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginTnterceptor())
                .excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/upload/**",
                "/shop-type/**",
                "/voucher/**"
        ).order(1);
        //刷新拦截器
        registry.addInterceptor(new RefreshTokenTnterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
    }
}
