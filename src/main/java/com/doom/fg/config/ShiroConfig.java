package com.doom.fg.config;
import com.doom.fg.security.JwtFilter;
import com.doom.fg.security.UserRealm;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.Filter;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:10
 * @Description: Shiro 配置类  组装所有组件。
 */
@Configuration
public class ShiroConfig {

    // 配置 SecurityManager，关闭 Session（使用 JWT 不需要 Session）
    @Bean
    public DefaultWebSecurityManager securityManager(UserRealm userRealm) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(userRealm);

        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        DefaultSessionStorageEvaluator defaultSessionStorageEvaluator = new DefaultSessionStorageEvaluator();
        defaultSessionStorageEvaluator.setSessionStorageEnabled(false);
        subjectDAO.setSessionStorageEvaluator(defaultSessionStorageEvaluator);
        securityManager.setSubjectDAO(subjectDAO);

        return securityManager;
    }

    // 配置过滤器工厂
    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(DefaultWebSecurityManager securityManager) {
        ShiroFilterFactoryBean factoryBean = new ShiroFilterFactoryBean();
        factoryBean.setSecurityManager(securityManager);

        // 1. 添加我们自定义的 JWT 过滤器，命名为 "jwt"
        Map<String, Filter> filterMap = new HashMap<>();
        filterMap.put("jwt", new JwtFilter());
        factoryBean.setFilters(filterMap);

        // 2. 配置拦截规则（注意顺序：LinkedHashMap）
        Map<String, String> ruleMap = new LinkedHashMap<>();

        // 3.放行 Knife4j 文档
        ruleMap.put("/doc.html", "anon");
        ruleMap.put("/webjars/**", "anon");
        ruleMap.put("/v3/api-docs/**", "anon");

        // 4.放行静态资源
        ruleMap.put("/css/**", "anon");
        ruleMap.put("/js/**", "anon");
        ruleMap.put("/favicon.ico", "anon");

        // 5.放行 登录 和 注册 接口 (必须！)
        ruleMap.put("/api/auth/**", "anon");
        ruleMap.put("/login", "anon");

        // 6.放行其他页面 (因为现在页面里没数据，数据都在 API 里保护着，所以可以放行空壳页面)
        ruleMap.put("/", "anon");
        ruleMap.put("/food/**", "anon");
        ruleMap.put("/ai/**", "anon");
        // 7.拦截所有数据接口
        ruleMap.put("/api/**", "jwt");
        ruleMap.put("/**", "anon"); // 兜底放行其他请求
        factoryBean.setFilterChainDefinitionMap(ruleMap);
        return factoryBean;
    }
}
