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
import static com.hmdp.utils.RedisConstants.*;

import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


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
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private  CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //Shop shop = cacheClient
         //       .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        if (shop == null ) {
            return Result.fail("店铺不存在");
        }
        //7.返回
        return Result.ok(shop);
    }



//    public Shop queryWithMutex(Long id){
//        String key = CACHE_SHOP_KEY+id;
//        //1.从redis查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //2.1存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //3.判断在缓存中是否为空值
//                if(shopJson != null ){
//                    //3.1是空值，则返回
//                    return null;
//                }
//        Shop shop = null;
//        //4.尝试获取互斥锁
//        String lockKey = "lock:shop:" + id;
//        try {
//            //4.1 判断是否获取互斥锁
//            boolean isLock = tryLock(lockKey);
//            if (!isLock) {
//                //4.2 没获取到锁，休眠一段时间
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//            //5. 缓存不存在，根据id查询数据库
//            shop = getById(id);
//                //模拟数据库查询
//                Thread.sleep(200);
//
//            //6. 数据库不存在此数据
//            if (shop == null) {
//                //将空值控制写入缓存
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//
//            //7.存在，写入缓存
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //8. 释放互斥锁
//            unLock(lockKey);
//        }
//
//        return shop;
//    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不存在");
        }
        // 1.更新数据库
        updateById(shop);

        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());

        return Result.ok();
    }
}
