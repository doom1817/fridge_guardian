package com.doom.fg.util;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:07
 * @Description: 用于生成 Token 和从 Token 中获取用户 ID。
 */
@Component
public class JwtUtil {

    // 对应 application.yaml 中的 jwt.secret
    @Value("${jwt.secret}")
    private String secret;

    // 对应 application.yaml 中的 jwt.expire
    @Value("${jwt.expire}")
    private long expire;

    /**
     * 生成 Token
     * @param userId 用户ID
     * @param username 用户名
     * @return 加密的 Token 字符串
     */
    public String createToken(Long userId, String username) {
        Date date = new Date(System.currentTimeMillis() + expire);
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withExpiresAt(date)
                .sign(algorithm);
    }

    /**
     * 校验 Token 并返回解码后的对象
     * @param token Token 字符串
     * @return DecodedJWT 对象，如果校验失败返回 null
     */
    public DecodedJWT verify(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm).build().verify(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 中直接获取用户 ID
     */
    public Long getUserId(String token) {
        try {
            DecodedJWT jwt = verify(token);
            return jwt != null ? jwt.getClaim("userId").asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
