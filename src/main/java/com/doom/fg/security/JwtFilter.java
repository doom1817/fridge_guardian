package com.doom.fg.security;
import com.doom.fg.context.UserContext;
import com.doom.fg.util.JwtUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:09
 * @Description:  拦截请求 -> 提取 Header -> 提交给 Realm。
 * 拦截 HTTP 请求，检查 Header 里有没有 Token。如果有，就提取出来扔给 Shiro 去验证。同时处理跨域问题。
 */
public class JwtFilter extends BasicHttpAuthenticationFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    /**
     * 核心修复点：重写 onAccessDenied
     * 当 isAccessAllowed 返回 false 时（即没有 Token 或认证失败），
     * 覆盖父类默认的 "弹窗" 行为，改为直接返回 JSON 错误。
     */
    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.getWriter().write("{\"code\": 401, \"msg\": \"未登录或登录过期\", \"data\": null}");
        return false;
    }

    /**
     * 检查是否允许访问
     * 如果带有 Token，则执行登录认证 (executeLogin)
     */
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        try {
            if (isLoginAttempt(request, response)) {
                return executeLogin(request, response);
            }
            // 如果没有 Token，且访问的是需要认证的接口，这里会返回 false，Shiro 会拦截
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否带有 Token
     */
    @Override
    protected boolean isLoginAttempt(ServletRequest request, ServletResponse response) {
        HttpServletRequest req = (HttpServletRequest) request;
        String authHeader = req.getHeader("Authorization");
        return authHeader != null;
    }

    /**
     * 执行登录
     */
    @Override
    protected boolean executeLogin(ServletRequest request, ServletResponse response) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String token = httpServletRequest.getHeader("Authorization");

        JwtToken jwtToken = new JwtToken(token);
        try {
            getSubject(request, response).login(jwtToken);
            
            Long userId = jwtUtil.getUserId(token);
            UserContext.setUserId(userId);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 请求完成后清理 ThreadLocal
     */
    @Override
    public void afterCompletion(ServletRequest request, ServletResponse response, Exception exception) {
        UserContext.clear();
    }

    /**
     * 对跨域提供支持
     */
    @Override
    protected boolean preHandle(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        res.setHeader("Access-Control-Allow-Origin", req.getHeader("Origin"));
        res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS,PUT,DELETE");
        res.setHeader("Access-Control-Allow-Headers", req.getHeader("Access-Control-Request-Headers"));

        // 跨域预检请求直接返回 OK
        if (req.getMethod().equals(RequestMethod.OPTIONS.name())) {
            res.setStatus(HttpStatus.OK.value());
            return false;
        }
        return super.preHandle(request, response);
    }
}
