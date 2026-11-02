package com.weliao.im.config;

import com.weliao.im.cluster.ClusterRuntime;
import com.weliao.im.cluster.RedisRateLimitStore;
import com.weliao.im.common.InMemoryRateLimitStore;
import com.weliao.im.common.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流计数的存储选型。
 * <p>
 * 判断依据复用 {@link ClusterRuntime} 的探测结果，而不是自己再 ping 一次 Redis。
 * 原因很实际：如果两处各自探测，Redis 抖动时完全可能出现「长连接走单机、限流走 Redis」
 * 这种自相矛盾的组合，排查时非常费劲。一个判断点，全站同一个结论。
 * <p>
 * 这也意味着 {@code CLUSTER_MODE=local} 时限流同样是单机内存——语义一致：
 * 说「不碰 Redis」就真的一根命令都不发。
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    @Bean
    public RateLimitStore rateLimitStore(ClusterRuntime cluster) {
        if (cluster.redisMode() && cluster.redisTemplate() != null) {
            log.info("限流：使用 Redis 共享计数，多实例下全局限流生效");
            return new RedisRateLimitStore(cluster.redisTemplate());
        }
        log.info("限流：使用单机内存计数（多实例部署时每个实例各算各的，实际放行量会翻倍）");
        return new InMemoryRateLimitStore();
    }
}
