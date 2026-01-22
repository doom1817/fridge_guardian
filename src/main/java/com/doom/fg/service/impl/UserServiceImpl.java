package com.doom.fg.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.doom.fg.entity.User;
import com.doom.fg.mapper.UserMapper;
import com.doom.fg.service.UserService;
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
    // 缓存单个用户，key 是用户 ID
    // 这里的 key = "#id" 表示取参数 id 的值作为缓存 key
    @Override
    @Cacheable(value = "users", key = "#id")
    public User getById(Serializable id) {
        System.out.println(">>> 查数据库获取用户: " + id); // 方便演示时看控制台
        return super.getById(id);
    }
}
