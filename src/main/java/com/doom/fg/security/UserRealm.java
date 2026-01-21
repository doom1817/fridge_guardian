package com.doom.fg.security;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.doom.fg.entity.User;
import com.doom.fg.service.UserService;
import com.doom.fg.util.JwtUtil;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy; // 解决循环依赖
import org.springframework.stereotype.Component;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:08
 * @Description:  核心认证逻辑：Shiro 调用这里来验证 Token 是否合法。
 */
@Component
public class UserRealm extends AuthorizingRealm {

    @Autowired
    private JwtUtil jwtUtil;

     @Autowired
     @Lazy // 必须加 Lazy，防止 Shiro 和 Service 循环依赖导致启动报错
     private UserService userService;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    // 授权：软著演示如果不涉及复杂权限（如管理员/普通用户），直接返回空对象即可
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        return new SimpleAuthorizationInfo();
    }

    // 认证：校验 Token
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken auth) throws AuthenticationException {
        // 1. 获取 Token 字符串
        String token = (String) auth.getCredentials();

        // 2. 校验 Token 签名 (验证是否被篡改、是否过期)
        DecodedJWT jwt = jwtUtil.verify(token);
        if (jwt == null) {
            throw new AuthenticationException("Token 无效或已过期");
        }

        // 3. 从 Token 中提取 userId
        Long userId = jwt.getClaim("userId").asLong();

        // 4. [关键] 查询数据库，确保用户真实存在 (防止 Token 虽然未过期，但用户已被管理员删除的情况)
        User user = userService.getById(userId);
        if (user == null) {
            throw new UnknownAccountException("用户不存在");
        }

        // 5. 认证成功！
        // 第一个参数 (Principal) 传入查出来的 user 对象，
        // 这样在 Controller 里就能通过 SecurityUtils.getSubject().getPrincipal() 拿到它
        return new SimpleAuthenticationInfo(user, token, getName());
    }
}
