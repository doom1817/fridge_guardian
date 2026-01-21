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
        String token = (String) auth.getCredentials();

        // 1. 校验 Token 签名和过期时间
        DecodedJWT jwt = jwtUtil.verify(token);
        if (jwt == null) {
            throw new AuthenticationException("Token 无效或已过期");
        }

        Long userId = jwt.getClaim("userId").asLong();

        // 2. (可选) 查询数据库确保用户真实存在
        // 如果为了赶进度，且相信 Token 只要签名对就是合法的，这一步可以跳过。
        // User user = userService.getById(userId);
        // if (user == null) throw new UnknownAccountException("用户不存在");

        // 3. 构建用户信息返回给 Shiro
        // 这里为了简单，我们构造一个只有 ID 的 User 对象，或者你直接存 userId 也可以
        User principalUser = new User();
        principalUser.setId(userId);
        principalUser.setUsername(jwt.getClaim("username").asString());

        return new SimpleAuthenticationInfo(principalUser, token, getName());
    }
}
