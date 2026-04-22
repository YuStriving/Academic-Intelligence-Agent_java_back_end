package com.xiaoce.agent.auth.config;

import com.xiaoce.agent.auth.security.JwtAuthenticationFilter;
import com.xiaoce.agent.auth.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 安全配置类 - 安全总控中心
 * 
 * 作用：配置整个应用的安全策略，包括认证、授权、CORS跨域等
 * 
 * 核心功能：
 * 1. 配置安全过滤器链（哪些接口需要认证，哪些是公开的）
 * 2. 配置CORS跨域策略（允许哪些前端域名访问）
 * 3. 配置密码加密器（用于密码加密和验证）
 * 4. 配置JWT认证过滤器（在每个请求前检查token）
 * 
 * 工作流程：
 * 1. 请求到达 -> 2. CORS检查 -> 3. JWT过滤器检查token -> 4. 权限检查 -> 5. 执行业务逻辑
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // 依赖注入：JWT认证过滤器，用于检查每个请求的token
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    // 依赖注入：认证失败处理器，当token无效时返回401错误
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    
    // 依赖注入：CORS跨域配置属性
    private final AppCorsProperties corsProperties;
    
    // 依赖注入：认证相关配置属性
    private final AuthProperties authProperties;

    /**
     * 配置安全过滤器链 - 这是整个安全系统的核心配置
     * 
     * 作用：定义哪些请求需要认证，哪些是公开的，以及如何处理认证失败
     * 
     * 配置说明：
     * 1. csrf.disable()：关闭CSRF保护（因为使用JWT，不需要CSRF）
     * 2. cors()：启用CORS跨域支持
     * 3. sessionManagement().stateless()：设置为无状态（不保存session）
     * 4. exceptionHandling()：配置认证失败时的处理
     * 5. authorizeHttpRequests()：配置接口访问权限
     * 6. addFilterBefore()：添加JWT过滤器到过滤器链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关闭CSRF保护：因为使用JWT认证，不需要CSRF token
                .csrf(csrf -> csrf.disable())
                
                // 启用CORS跨域支持：配置允许哪些前端域名访问
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                
                // 设置为无状态会话：服务器不保存用户状态，每次请求都验证JWT
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // 配置认证失败处理：当token无效时，返回统一的401错误响应
                .exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint))
                
                // 配置接口访问权限：定义哪些接口需要认证，哪些是公开的
                .authorizeHttpRequests(auth -> auth
                        // 允许所有OPTIONS请求（CORS预检请求必须放行）
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        
                        // 公开接口：注册、登录、刷新token不需要认证
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        
                        // 其他所有接口都需要认证（需要有效的JWT token）
                        .anyRequest().authenticated())
                
                // 添加JWT认证过滤器：在每个请求到达控制器之前检查token
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }



    /**
     * 配置认证管理器
     * 
     * 作用：防止Spring Security自动配置默认的认证机制
     * 
     * 说明：因为我们使用自定义的JWT认证，不需要Spring Security的默认认证
     * 但需要提供一个AuthenticationManager bean来避免自动配置冲突
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * 配置CORS跨域策略
     * 
     * 作用：允许指定的前端域名访问后端API，解决跨域问题
     * 
     * CORS（跨域资源共享）说明：
     * 浏览器出于安全考虑，默认禁止不同域名的网站互相访问数据
     * CORS就是告诉浏览器："这个后端是安全的，允许指定的前端访问我"
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        
        // 从配置文件读取允许的源（前端域名），使用白名单机制
        List<String> origins = Arrays.stream(corsProperties.getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        
        // 设置允许访问的源（前端域名）
        cfg.setAllowedOrigins(origins);
        
        // 设置允许的HTTP方法
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // 设置允许的请求头（特别注意Authorization头，用于传递JWT token）
        cfg.setAllowedHeaders(List.of(
                "Authorization",      // JWT token头
                "Content-Type",       // 内容类型
                "Accept",             // 接受类型
                "Origin",             // 来源
                "X-Requested-With",   // 请求类型
                "Access-Control-Request-Method",   // CORS预检请求
                "Access-Control-Request-Headers"   // CORS预检请求头
        ));
        
        // 设置允许前端访问的响应头（自定义响应头）
        cfg.setExposedHeaders(List.of("X-Total-Count", "X-Page-Number", "X-Page-Size"));
        
        // 设置是否允许携带凭证（Cookie等），JWT认证不需要Cookie，设为false
        cfg.setAllowCredentials(false);
        
        // 设置预检请求缓存时间（秒），减少预检请求频率
        cfg.setMaxAge(3600L);

        // 创建基于URL的CORS配置源，对所有路径生效
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
