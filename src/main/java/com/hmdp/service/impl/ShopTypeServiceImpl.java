package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryTypeList() {
        //先从redis中看是否有缓存
        String typeJson =  stringRedisTemplate.opsForValue().get("cache:shoptype");
        //有缓存的话就直接返回
        if (StrUtil.isNotBlank(typeJson))
        {
            return JSONUtil.toList(typeJson,ShopType.class);
        }
        //没有就查数据库
        List<ShopType> typeList =  query().orderByAsc("sort").list();
        if (    typeList ==null)
        {
            return null;
        }
        //添加缓存
        stringRedisTemplate.opsForValue().set("cache:shoptype",JSONUtil.toJsonStr(typeList));
        //返回
        return typeList;
    }
}
