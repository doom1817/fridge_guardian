package com.doom.fg.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.doom.fg.entity.User;
import com.doom.fg.mapper.UserMapper;
import com.doom.fg.service.UserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:24
 * @Description:
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    // 查询时：如果没有缓存，查库并写入缓存；如果有缓存，直接返回
    @Override
    @Cacheable(value = "users", key = "#id")
    public User getById(Serializable id) {
        System.out.println(">>> 查数据库获取用户: " + id);
        return super.getById(id);
    }

    // [新增] 更新时：删除对应的缓存，保证下次查询能获取最新数据
    @Override
    @CacheEvict(value = "users", key = "#entity.id")
    public boolean updateById(User entity) {
        return super.updateById(entity);
    }
}
