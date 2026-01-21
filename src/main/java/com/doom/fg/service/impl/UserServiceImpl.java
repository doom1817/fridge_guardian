package com.doom.fg.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.doom.fg.entity.User;
import com.doom.fg.mapper.UserMapper;
import com.doom.fg.service.UserService;
import org.springframework.stereotype.Service;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:24
 * @Description:
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
