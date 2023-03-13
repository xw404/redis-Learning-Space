package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //基于互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //基于逻辑过期方法解决缓存击穿问题
//        Shop shop = queryWithLogicalExpire(id);


        if (shop==null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    //尝试获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //判断id
        Long id = shop.getId();
        if (id==null) {
            return Result.fail("店铺ID不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        //返回结果
        return Result.ok();
    }

    //缓存穿透方法
    public Shop queryWithPassThrough(Long id){
        String key =CACHE_SHOP_KEY+ id;
        //1.先尝试从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2，判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3，存在，返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否是空值
        if(shopJson!=null){
            //空字符串
            //返回错误
            return null;
        }
        //4不存在 ，根据id查询数据库
        Shop shop = getById(id);
        //5.数据库中不存在  返回错误
        if(shop == null){
            //设置空值null写入Redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.数据库中存在，先写入Redis，
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7返回
        return shop;
    }

    //互斥锁解决缓存击穿方法
    public Shop queryWithMutex(Long id)  {
        String key =CACHE_SHOP_KEY+ id;
        Shop shop = null;
        //1.先尝试从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2，判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3，存在，返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if(shopJson!=null){
            //空字符串
            //返回错误
            return null;
        }
        //4 ,实现缓存重建
        //4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2 判断是否获取成功
            if(!isLock){
                //4.3失败。则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //4.4 成功。根据id查询数据库
            shop= getById(id);
            Thread.sleep(200);   //模拟重建延时
            //5.数据库中不存在  返回错误
            if(shop == null){
                //设置空值null写入Redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6.数据库中存在，先写入Redis，
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            //7.释放互斥锁
            unLock(lockKey);
        }
        //8返回
       return shop;
    }

    //店铺信息逻辑过期时间
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1,查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(2000);
        //2 封装成逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

    }
    //线程池

    private static final ExecutorService CACHE_REBUILD_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期解决缓存击穿方法
    public Shop queryWithLogicalExpire(Long id){
        String key =CACHE_SHOP_KEY+ id;
        //1.先尝试从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2，判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //不存在
            return null;
        }
        //3存在 ，需要判断过期时间
        //把JSON反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回店铺信息
            return shop;
        }
        //已过期 需要缓存重建
        //尝试获取互斥锁
        String lochKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lochKey);
        //判断是否获取锁成功
        if(isLock){
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id,30L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lochKey);
                }
            });
        }
        //失败，直接返回（过期的信息）
        return shop;
    }
}
