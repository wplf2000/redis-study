package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = "shoptype:list:";
        //缓存查询ShopType类型List
        String list = stringRedisTemplate.opsForValue().get(key);
        //判断redis缓存有无list
        if (StrUtil.isNotBlank(list)){
            // 3.有就返回缓存中的list
            List<ShopType> typeList = JSONUtil.toList(JSONUtil.parseArray(list), ShopType.class);
            return Result.ok(typeList);
        }
        // 4.没有就查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 5.写入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList));

        // 6返回数据库查询结果list
        return Result.ok(typeList);
    }
}
