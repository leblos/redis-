package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate   stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
        //缓存穿透
         //Shop shop =  queryWithpen(id);

        //缓存击穿
        Shop shop =  queryWithMutex(id);
        if (shop == null) {
            return Result.fail("你查的店铺信息不存在");
        }
        return Result.ok(shop);
    }
    private Shop queryWithMutex(Long id)
    {
        String key = "cache:shop:";
        //去redis中查询是否有

        String shopStr = stringRedisTemplate.opsForValue().get(key+id);
        //有就直接返回
        if (StrUtil.isNotBlank(shopStr))
        {
            return JSONUtil.toBean(shopStr,Shop.class);
        }
        if (shopStr!=null)
        {
            return null;
        }
        String lockkey = null;
        Shop shop = null;
        try {
            lockkey = "lock:shop:"+id;
            boolean b =  getlock(lockkey);
            if (b==false)
            {
                Thread.sleep(10);
               return queryWithMutex(id);
            }

            //没有就去数据库中查
            shop = getById(id);
            //没查到就直接返回
            if (shop==null)
            {
                stringRedisTemplate.opsForValue().set(key+id,"",CACHE_NULL_TTL, TimeUnit.MINUTES );
                return null;
            }
            //查到就添加缓存并且返回
            stringRedisTemplate.opsForValue().set(key+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            deleteLock(lockkey);
        }


        return shop;

    }

    private Shop queryWithpen(Long id)
    {
        String key = "cache:shop:";
        //去redis中查询是否有

        String shopStr = stringRedisTemplate.opsForValue().get(key+id);
        //有就直接返回
        if (StrUtil.isNotBlank(shopStr))
        {
            return JSONUtil.toBean(shopStr,Shop.class);
        }
        if (shopStr!=null)
        {
            return null;
        }

        //没有就去数据库中查
        Shop shop = getById(id);
        //没查到就直接返回
        if (shop==null)
        {
            stringRedisTemplate.opsForValue().set(key+id,"",CACHE_NULL_TTL, TimeUnit.MINUTES );
            return null;
        }
        //查到就添加缓存并且返回
        stringRedisTemplate.opsForValue().set(key+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES );
        return shop;
    }


    private boolean getlock(String key)
    {
     Boolean b =    stringRedisTemplate.opsForValue().setIfAbsent(key,"erq",10,TimeUnit.MILLISECONDS);
     return BooleanUtil.isTrue(b);
    }

    private void deleteLock(String key)
    {
        stringRedisTemplate.delete(key);
    }


    @Override
    @Transactional
    public void updateShop(Shop shop) {
        if (shop.getId()==null)return;
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
    }
}
