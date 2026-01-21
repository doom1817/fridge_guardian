package com.doom.fg.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.doom.fg.common.Result;
import com.doom.fg.entity.User;
import com.doom.fg.service.UserService;
import com.doom.fg.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:11
 * @Description:  注册与登录接口 (含 BCrypt)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtil jwtUtil;

    // BCrypt 密码加密器  只需要 new 一次，也可以注入 Bean
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody User loginUser) {
        // 1. 根据用户名查库
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginUser.getUsername()));

        if (user == null) {
            return Result.error("用户不存在");
        }

        // 2. 校验密码 (matches 方法：参数1=明文，参数2=数据库里的密文)
        if (!passwordEncoder.matches(loginUser.getPassword(), user.getPassword())) {
            return Result.error("密码错误");
        }

        // 3. 密码正确，生成 Token
        String token = jwtUtil.createToken(user.getId(), user.getUsername());

        Map<String, String> map = new HashMap<>();
        map.put("token", token);
        map.put("username", user.getUsername());
        return Result.success(map);
    }

    /**
     * 注册接口
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody User user) {
        // 检查用户名是否已存在
        long count = userService.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, user.getUsername()));
        if (count > 0) {
            return Result.error("用户名已存在");
        }

        // 1. 密码加密 (不要存明文！)
        String encodePwd = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodePwd);

        // 2. 存入数据库
        boolean save = userService.save(user);
        return save ? Result.success() : Result.error("注册失败");
    }
}
