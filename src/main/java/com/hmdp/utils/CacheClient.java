package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time , TimeUnit timeUnit)
    {
        String json = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key,json,time,timeUnit);
    }

    //zd 这里过期时间应该让调用者传一个过期的秒数过来
    public void setWithLogicalExpire(Object data,String key, Long time, TimeUnit unit) {
            RedisData redisData = new RedisData();
            redisData.setData(data);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

            String json = JSONUtil.toJsonStr(redisData);
            stringRedisTemplate.opsForValue().set(key,json);
    }


    public <R,ID> R queryWithPassThrough(String key1, ID id, Class<R> type, Function<ID,R> dbfallback,Long time ,TimeUnit unit)
    {

        //去redis中查询是否有
        String shopStr = stringRedisTemplate.opsForValue().get(key1+id);
        //有就直接返回
        if (StrUtil.isNotBlank(shopStr))
        {
            return JSONUtil.toBean(shopStr,type);
        }
        if (shopStr!=null)
        {
            return null;
        }

        //没有就去数据库中查
        R r =dbfallback.apply(id);
        //没查到就直接返回
        if (r==null)
        {
            stringRedisTemplate.opsForValue().set(key1+id,"",time, unit );
            return null;
        }
        //查到就添加缓存并且返回
        stringRedisTemplate.opsForValue().set(key1+id,JSONUtil.toJsonStr(r),time, unit );
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public <R,ID> R queryWithMutex(ID id,String key,Class<R> tyep,Function<ID,R> dbfalback)
    {
        String fkey = key+id;
        //去redis中查询是否有

        String shopStr = stringRedisTemplate.opsForValue().get(fkey);
        //有就直接返回
        if (StrUtil.isNotBlank(shopStr))
        {
            return JSONUtil.toBean(shopStr,tyep);
        }
        if (shopStr!=null)
        {
            return null;
        }
        String lockkey = null;
        Shop shop = null;
        R r;
        try {
            lockkey = "lock:shop:"+id;
            boolean b =  tryLock(lockkey);
            if (b==false)
            {
                Thread.sleep(10);
                return queryWithMutex(id,key,tyep,dbfalback);
            }

            //没有就去数据库中查
             r = dbfalback.apply(id);
            //没查到就直接返回
            if (shop==null)
            {
                stringRedisTemplate.opsForValue().set(fkey,"",CACHE_NULL_TTL, TimeUnit.MINUTES );
                return null;
            }
            //查到就添加缓存并且返回
            stringRedisTemplate.opsForValue().set(key+id,JSONUtil.toJsonStr(r),CACHE_SHOP_TTL, TimeUnit.MINUTES );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockkey);
        }


        return r;

    }

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.存在，直接返回
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return r;
        }
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire( newR,key, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return r;
    }





}
