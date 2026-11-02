package com.weliao.im.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weliao.im.config.AppProperties;
import com.weliao.im.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 接口限流。
 * <p>
 * 只盯两类最容易被脚本刷的接口：
 * <ul>
 *   <li>登录 / 注册 —— 按 IP 计数，防撞库与批量注册</li>
 *   <li>发送消息 —— 按用户计数，防刷屏</li>
 * </ul>
 * 其余接口不设限制，避免给正常使用添堵。
 * <p>
 * 放在 Spring Security 过滤器链里 JwtAuthFilter 之后，这样能直接复用已解析的身份。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;
    private final AppProperties props;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, AppProperties props,
                           JwtService jwtService, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        AppProperties.RateLimit cfg = props.getRateLimit();
        if (!cfg.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
            String ip = clientIp(request);
            if (!rateLimiter.tryAcquire("auth:" + ip, cfg.getAuthPerMinute(), WINDOW)) {
                reject(response, "操作太频繁，请稍后再试");
                return;
            }
        } else if ("POST".equals(method) && path.matches("^/api/conversations/\\d+/messages$")) {
            Long userId = jwtService.parseUserId(bearer(request));
            if (userId != null && !rateLimiter.tryAcquire("msg:" + userId, cfg.getMessagePerMinute(), WINDOW)) {
                reject(response, "发送太频繁，请稍后再试");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /** 取真实客户端 IP。只有开启 trustForwardedFor 时才认 X-Forwarded-For，否则可被伪造绕过。 */
    private String clientIp(HttpServletRequest request) {
        if (props.getRateLimit().isTrustForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds(WINDOW)));
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(42900, message)));
    }
}
