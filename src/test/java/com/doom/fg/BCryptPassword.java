package com.doom.fg;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/22:17
 * @Description:
 */
public class BCryptPassword {
    public static void main(String[] args) {
        // 创建BCryptPasswordEncoder实例
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // 原始密码
        String rawPassword = "123456";

        // 加密密码
        String encodedPassword = passwordEncoder.encode(rawPassword);

        System.out.println("原始密码: " + rawPassword);
        System.out.println("加密后的密码: " + encodedPassword);

        // 验证密码是否匹配
        boolean isMatch = passwordEncoder.matches(rawPassword, encodedPassword);
        System.out.println("密码验证结果: " + isMatch);
    }
}
