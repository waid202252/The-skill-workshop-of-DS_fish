package com.weliao.im.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weliao.im.common.R;
import com.weliao.im.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
 * 无状态 JWT 安全配置。
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AppProperties props, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 配置。
     * <p>
     * 踩过的坑：这里原来只放行 Vite 开发服务器的 5173，结果通过 8080 部署访问时，
     * 浏览器给 POST 请求带上了 {@code Origin: http://<host>:8080}，
     * Spring 直接返回 403 "Invalid CORS request" —— 注册、发消息、装技能全部失败。
     * 而服务端测试脚本（curl / Node fetch）不带 Origin 头，完全测不出这个问题。
     * <p>
     * 为什么现在可以放开到 {@code *}：本项目鉴权用 Authorization: Bearer token，
     * 不使用 Cookie 会话，所以"跨站请求自动带凭证"的前提不存在，限制 origin 并不能提升安全性。
     * 真正需要收紧 origin 的是基于 Cookie 的会话体系。
     * <p>
     * 如果要限制（例如将来改用 Cookie），把 application.yml 里的
     * {@code app.cors-allowed-origins} 改成逗号分隔的域名列表即可，此时必须同时把
     * allowCredentials 打开。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        String raw = props.getCorsAllowedOrigins();
        List<String> origins = Arrays.stream(raw == null ? new String[]{"*"} : raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            origins = List.of("*");
        }
        cfg.setAllowedOriginPatterns(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(List.of("*"));
        // 下载 README 时前端要读这个响应头来拿文件名
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        // 用 token 而非 Cookie，所以关闭凭证模式；这也让 "*" 成为合法配置
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/uploads/**", "/ws", "/ws/**",
                                "/h2-console/**", "/error", "/favicon.ico").permitAll()
                        // 健康检查必须免鉴权，否则容器 healthcheck 和负载均衡探活都会拿到 401
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 运行环境自检（当前连的是 H2 / MySQL / MariaDB）。
                        // 只返回库类型、版本、库名与行数，不含账号密码；公网部署建议在 nginx 屏蔽该路径。
                        // 社区指南/配方允许游客只读；提问与反馈仍需登录
                        .requestMatchers(HttpMethod.GET, "/api/community/guides/**").permitAll()
                        .requestMatchers("/api/system/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, 40100, "登录状态已失效，请重新登录"))
                        .accessDeniedHandler((req, res, e) ->
                                writeJson(res, HttpServletResponse.SC_FORBIDDEN, 40300, "没有权限执行该操作")))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJson(HttpServletResponse res, int status, int code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(objectMapper.writeValueAsString(R.fail(code, message)));
    }
}
