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

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value ,Long time, TimeUnit unit){
        //封装一个逻辑时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        //放入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public  <R,ID>R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallBack,Long time, TimeUnit unit){
        String key = keyPrefix+id;
        //1.从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在，直接返回
            R r = JSONUtil.toBean(json,type);
            return r;
        }

        //判断在缓存中是否为空值
        if(json != null ){
            //是空值，则返回
            return null;
        }

        //4.不存在，根据id查询数据库
        R r = dbFallBack.apply(id);

        //5.不存在，返回错误
        if (r == null) {
            //将空值控制写入缓存
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //6.存在，写入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),time, unit);

        return r;
    }

    public <R,ID>R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallBack,Long time, TimeUnit unit){
        String key = keyPrefix+id;
        //1.从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否命中
        if(StrUtil.isBlank(shopJson)){
            //3.未命中，直接返回空
            return null;
        }

        //4.命中，需要将缓存数据反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1未过期，返回商铺信息
            return r;
        }
        //5.2过期,需要缓存重建

        //6缓存重建
        //6.1获取互斥锁，判断是否获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //6.2重建缓存
                    //查询数据库
                    R newR =dbFallBack.apply(id);
                    //调用使用逻辑过期的方法
                    this.setWithLogicalExpire(key,newR,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.3返回商铺信息
        return r;

    }


    public boolean tryLock(String key){
        //执行setnx操作
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    };

    public void unLock(String key){
        //执行dellte操作
        stringRedisTemplate.delete(key);
    }
}
