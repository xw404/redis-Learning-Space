package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 处理缓存的工具类
 */
@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringredisTemplate;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.stringredisTemplate = redisTemplate;
    }
    //
    public void set(String key , Object value, Long time, TimeUnit unit){
        stringredisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    //逻辑过期
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringredisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id , Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        String key =keyPrefix+ id;
        //1.先尝试从redis中查询商铺缓存
        String json = stringredisTemplate.opsForValue().get(key);
        //2，判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3，存在，返回
            return JSONUtil.toBean(json,type);
        }
        //判断命中的是否是空值
        if(json!=null){
            //空字符串
            //返回错误
            return null;
        }
        //4不存在 ，根据id查询数据库
        R r= dbFallback.apply(id);
        //5.数据库中不存在  返回错误
        if(r == null){
            //设置空值null写入Redis
            stringredisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.数据库中存在，先写入Redis，
        this.set(key,r,time,unit);
        //7返回
        return r;
    }
}
