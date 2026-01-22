package com.doom.fg.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.doom.fg.entity.Category;
import com.doom.fg.mapper.CategoryMapper;
import com.doom.fg.service.CategoryService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
    @Override
    @Cacheable(value = "categories", key = "'all'")
    public List<Category> list() {
        // 当 Redis 里没有时，会执行这行代码查数据库，并自动存入 Redis
        // 当 Redis 里有时，直接返回，不走数据库
        return super.list();
    }
}
