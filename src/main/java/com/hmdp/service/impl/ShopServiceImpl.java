package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    /*
     用逻辑过期来处理缓存击穿
     */

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(  10);

    private Shop queryWithjichuan(Long id)
    {
        String key = "cache:shop:";
        //去redis中查询是否有

        String shopStr = stringRedisTemplate.opsForValue().get(key+id);
        //没有就直接返回
        if (StrUtil.isBlank(shopStr))
        {
            return null;
        }

        //检查缓存是否逻辑过期 这里需要注意一下
        RedisData redisData = JSONUtil.toBean(shopStr,RedisData.class);
        LocalDateTime expiretime =   redisData.getExpireTime();
        Shop shop  = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        if (expiretime.isAfter(LocalDateTime.now()))
        {
            return shop;
        }
        //看是否能够获取锁
        boolean s =  getlock(LOCK_SHOP_KEY+id);
        //获取不到就直接返回旧的值
        if (!s){
            return shop;
        }
        //获取的到就拿到锁重新开个线程刷新缓存然后直接返回

        CACHE_REBUILD_EXECUTOR.submit( ()->{

            try{
                //重建缓存
                // 这里还需要再次确定是否缓存过期
                this.saveShop2redis(id,20L);
            }catch (Exception e){
                throw new RuntimeException(e);
            }finally {
                deleteLock(LOCK_SHOP_KEY+id);
            }
        });

        return shop;



    }

    public void saveShop2redis(Long id,Long expireSeconds)
    {
        Shop shop = getById(id);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

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
