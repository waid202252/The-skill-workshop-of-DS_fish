package com.weliao.im.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weliao.im.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 跨节点能力的唯一决策点：启动时决定「这次是单机跑还是多实例跑」，然后提供对应的
 * {@link PresenceStore} 和 {@link ClusterBus} 实现。
 * <p>
 * 为什么不直接用 {@code @ConditionalOnProperty}：模式是 {@code auto} 时，用哪个实现不取决于配置，
 * 而取决于「此刻 Redis 到底通不通」。条件注解在 bean 定义期求值，那时还没建立任何连接，
 * 判断不了。所以改成一个运行时探测 + 工厂式装配，逻辑集中在一处，也方便在启动日志里说清结果。
 * <p>
 * 三种模式的取舍：
 * <ul>
 *   <li>{@code auto}（默认）：Redis 通就跨节点，不通就退回单机。<b>本地开发零外部依赖这条底线保住了</b></li>
 *   <li>{@code local}：强制单机，连探测都不做。单机部署时最省事，也避免 Redis 抖动带来的干扰</li>
 *   <li>{@code redis}：强制 Redis，连不上直接启动失败。生产环境用它，把配置错误暴露在部署阶段，
 *       而不是等到上线后某个用户在实例 B 上收不到消息才发现</li>
 * </ul>
 */
@Component
public class ClusterRuntime {

    private static final Logger log = LoggerFactory.getLogger(ClusterRuntime.class);

    private final AppProperties props;
    private final String nodeId;
    private final PresenceStore presence;
    private final ClusterBus bus;
    private final boolean redisMode;
    /** Redis 可用时的模板，否则为 null。限流计数等「需要共享状态」的组件复用它，不再自行探测 */
    private final StringRedisTemplate redisTemplate;

    private ScheduledExecutorService sweeper;

    public ClusterRuntime(AppProperties props,
                          ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                          ObjectMapper objectMapper) {
        this.props = props;
        this.nodeId = resolveNodeId(props.getCluster().getNodeId());
        long ttlMillis = Math.max(30, props.getCluster().getPresenceTtlSeconds()) * 1000L;
        String mode = normalize(props.getCluster().getMode());

        StringRedisTemplate template = "local".equals(mode) ? null : redisTemplateProvider.getIfAvailable();
        boolean reachable = template != null && ping(template.getConnectionFactory());

        if ("redis".equals(mode) && !reachable) {
            throw new IllegalStateException(
                    "app.cluster.mode=redis，但连不上 Redis。请检查 spring.data.redis.*（或 REDIS_HOST / REDIS_PORT / REDIS_PASSWORD），"
                            + "或把 CLUSTER_MODE 改成 auto/local");
        }

        if (reachable) {
            this.presence = new RedisPresenceStore(template, ttlMillis);
            this.bus = new RedisClusterBus(template, objectMapper, nodeId);
            this.redisTemplate = template;
            this.redisMode = true;
        } else {
            this.presence = new InMemoryPresenceStore(ttlMillis);
            this.bus = new NoopClusterBus();
            this.redisTemplate = null;
            this.redisMode = false;
        }
    }

    @PostConstruct
    void logMode() {
        if (redisMode) {
            log.info("推送模式：多实例（Redis 在线）。本节点={}，在线状态 TTL={}s",
                    nodeId, props.getCluster().getPresenceTtlSeconds());
        } else {
            log.info("推送模式：单机内存。本节点={}。只有本进程内的连接能收到推送——"
                    + "要验证跨实例投递，请先启动 Redis（docker run -p 6379:6379 redis）再重启", nodeId);
        }
        startSweeper();
    }

    /**
     * 开始接收投递到本节点的消息。
     * <p>
     * 由 {@code WsHub} 在自身初始化完成后调用，回调里只允许做「推给本机连接」这一件事。
     */
    public void startDelivery(BiConsumer<Long, String> deliver) {
        if (bus instanceof RedisClusterBus redisBus) {
            try {
                redisBus.start(deliver);
            } catch (Exception e) {
                // 探测通、订阅却失败属于极端情况；降级成「只能投本地」，比让应用起不来好
                log.error("跨节点监听启动失败，本次运行只能推送本机连接: {}", e.getMessage());
            }
        }
    }

    public String nodeId() {
        return nodeId;
    }

    public PresenceStore presence() {
        return presence;
    }

    public ClusterBus bus() {
        return bus;
    }

    public boolean redisMode() {
        return redisMode;
    }

    /** Redis 可用时返回模板，否则 null。调用方必须自己判断 null。 */
    public StringRedisTemplate redisTemplate() {
        return redisTemplate;
    }

    /** 给 /api/system/info 之类的运维接口用的一句话描述 */
    public String describe() {
        return (redisMode ? "multi-node(redis)" : "single-node(memory)") + "@" + nodeId;
    }

    private void startSweeper() {
        int seconds = props.getCluster().getSweepIntervalSeconds();
        if (seconds <= 0) {
            log.info("在线状态定期清理已关闭（app.cluster.sweep-interval-seconds=0）");
            return;
        }
        sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presence-sweeper");
            t.setDaemon(true);
            return t;
        });
        sweeper.scheduleWithFixedDelay(() -> {
            try {
                presence.sweep();
            } catch (Exception e) {
                // 后台任务绝不能因为异常而停止调度
                log.debug("在线状态清理异常: {}", e.getMessage());
            }
        }, seconds, seconds, TimeUnit.SECONDS);
        log.debug("在线状态清理任务已启动，周期 {}s", seconds);
    }

    @PreDestroy
    void shutdown() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
        if (bus instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("关闭消息总线失败: {}", e.getMessage());
            }
        }
    }

    /** 启动探测：能 ping 通才算「有」。Lettuce 的连接是惰性建立的，所以必须真的发一条命令 */
    private boolean ping(RedisConnectionFactory factory) {
        if (factory == null) {
            return false;
        }
        try (RedisConnection connection = factory.getConnection()) {
            String pong = connection.ping();
            return pong != null && pong.toUpperCase().contains("PONG");
        } catch (Exception e) {
            log.debug("Redis 探测失败: {}", e.getMessage());
            return false;
        }
    }

    private static String normalize(String mode) {
        if (mode == null || mode.isBlank()) {
            return "auto";
        }
        String m = mode.trim().toLowerCase();
        return switch (m) {
            case "local", "redis", "auto" -> m;
            default -> {
                log.warn("未知的 app.cluster.mode={}，按 auto 处理", mode);
                yield "auto";
            }
        };
    }

    /**
     * 节点标识。默认「主机名-随机 8 位」：
     * 随机后缀保证同一台机器起两个实例（本地验证多实例时最常见）不会互相覆盖在线记录。
     */
    private static String resolveNodeId(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "node";
        }
        host = host.replaceAll("[^A-Za-z0-9._-]", "-");
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
