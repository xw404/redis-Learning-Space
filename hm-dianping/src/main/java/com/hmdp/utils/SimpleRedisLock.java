package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private static  final DefaultRedisScript<Long> UNLOCK_SCRIPTS;
    static {
        UNLOCK_SCRIPTS = new DefaultRedisScript<>();
        UNLOCK_SCRIPTS.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPTS.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程的标识ID（存入缓存时要存入线程ID）
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);
        //可能存在拆箱过程(避免success为null)
        return success.TRUE.equals(success);
    }
    //释放锁（Lua脚本方法解决事物的原子性）
    @Override
    public void unLock() {
        //调用Lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPTS,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX+Thread.currentThread().getId()
                );
    }
    //释放锁
//    @Override
//    public void unLock() {
//        //获取线程标识
//        String threadId = ID_PREFIX+Thread.currentThread().getId();
//        //判断线程标识是否一致
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (threadId==id){
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//    }
}
