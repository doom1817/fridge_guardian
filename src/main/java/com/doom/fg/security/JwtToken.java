package com.doom.fg.security;
import org.apache.shiro.authc.AuthenticationToken;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:08
 * @Description: Shiro 默认只认账号密码 Token，我们需要创建一个用来封装 JWT 字符串的 Token 类。
 */
public class JwtToken implements AuthenticationToken {
    private final String token;

    public JwtToken(String token) {
        this.token = token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
