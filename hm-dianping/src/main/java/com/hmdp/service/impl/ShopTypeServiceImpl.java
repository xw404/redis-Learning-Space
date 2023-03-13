package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeLists() {
        //1 获取redis用户
        String shopType = stringRedisTemplate.opsForValue().get("cache:ShopType");
        if (StrUtil.isNotBlank(shopType)) {
            //2存在。将数据转换为ShopType对象直接返回
            List<ShopType> shopTypes = JSONUtil.toList(shopType, ShopType.class);
            return Result.ok(shopTypes);
        }
        //3,不存在，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //4，数据不存在，返回错误
        if (shopTypes==null) {
            return Result.fail("该分类不存在");
        }
        //5.数据存在，送入REDIS
        stringRedisTemplate.opsForValue().set("cache:ShopType",JSONUtil.toJsonStr(shopTypes));

        return Result.ok(shopTypes);
    }
}
