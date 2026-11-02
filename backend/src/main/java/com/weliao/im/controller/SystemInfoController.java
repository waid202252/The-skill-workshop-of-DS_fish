package com.weliao.im.controller;

import com.weliao.im.cluster.ClusterRuntime;
import com.weliao.im.common.R;
import com.weliao.im.common.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行环境自检：告诉运维/开发者「现在到底连的是哪个数据库」。
 * <p>
 * 为什么需要它：本项目有 dev(H2) / prod(MySQL) / xampp(MariaDB) 三套数据源，
 * 靠 {@code spring.profiles.active} 三选一。仅看配置文件无法确认"此刻在跑哪个"，
 * 而 {@code /h2-console} 又会被 nginx 的 SPA 兜底伪装成 200——非常容易误判。
 * 所以这里直接问 JDBC 元数据，答案来自真实连接，不会说谎。
 * <p>
 * 暴露的信息刻意最小化：不返回账号密码，URL 会去掉查询参数。
 * 公网部署时建议在 nginx 层屏蔽 {@code /api/system/info}。
 */
@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoController.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final ClusterRuntime cluster;
    private final RateLimiter rateLimiter;

    public SystemInfoController(DataSource dataSource, JdbcTemplate jdbcTemplate, Environment environment,
                                ClusterRuntime cluster, RateLimiter rateLimiter) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.cluster = cluster;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/info")
    public R info() {
        Map<String, Object> result = new LinkedHashMap<>();

        String[] profiles = environment.getActiveProfiles();
        result.put("profile", profiles.length == 0 ? "default" : String.join(",", profiles));
        result.put("database", databaseInfo());
        result.put("pool", poolInfo());
        result.put("schemaVersion", schemaVersion());
        result.put("counts", counts());
        result.put("cluster", clusterInfo());
        result.put("rateLimit", rateLimitInfo());
        return R.ok(result);
    }

    /**
     * 限流计数存在哪。多实例部署时这个值必须是 redis —— 否则「全局限流」只是句场面话：
     * 两个实例各放行一遍，实际配额翻倍。
     */
    private Map<String, Object> rateLimitInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("store", rateLimiter.storeKind());
        return info;
    }

    /**
     * 推送模式自检：多实例部署时，先确认「这台机器到底是哪一种模式、叫什么名字」，
     * 再去排查「为什么 A 收不到 B 的消息」。同类的坑还有：CLUSTER_NODE_ID 没配导致
     * 两个容器随机到同一个主机名——这里直接把它暴露出来。
     */
    private Map<String, Object> clusterInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", cluster.redisMode() ? "multi-node(redis)" : "single-node(memory)");
        info.put("nodeId", cluster.nodeId());
        info.put("crossNodeDelivery", cluster.bus().enabled());
        return info;
    }

    /** 直接读 JDBC 元数据，答案一定和真实连接一致 */
    private Map<String, Object> databaseInfo() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            db.put("product", md.getDatabaseProductName());
            db.put("productVersion", md.getDatabaseProductVersion());
            db.put("driver", md.getDriverName().trim());
            db.put("driverVersion", shortDriverVersion(md.getDriverVersion()));
            db.put("catalog", conn.getCatalog());
            db.put("url", sanitizeUrl(md.getURL()));
            db.put("host", hostOf(md.getURL()));
            db.put("ok", true);
        } catch (Exception e) {
            log.warn("读取数据库元数据失败: {}", e.getMessage());
            db.put("ok", false);
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> poolInfo() {
        Map<String, Object> pool = new LinkedHashMap<>();
        try {
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari
                    && hikari.getHikariPoolMXBean() != null) {
                var bean = hikari.getHikariPoolMXBean();
                pool.put("active", bean.getActiveConnections());
                pool.put("idle", bean.getIdleConnections());
                pool.put("total", bean.getTotalConnections());
            } else {
                pool.put("note", "非 Hikari 数据源");
            }
        } catch (Exception e) {
            pool.put("error", e.getMessage());
        }
        return pool;
    }

    private String schemaVersion() {
        try {
            List<String> versions = jdbcTemplate.queryForList(
                    "select version from flyway_schema_history order by installed_rank desc", String.class);
            return versions.isEmpty() ? "none" : versions.get(0);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 行数统计：新库或表未建时不能整体失败，逐表 try 一下 */
    private Map<String, Object> counts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("users", countOf("t_user"));
        counts.put("conversations", countOf("t_conversation"));
        counts.put("messages", countOf("t_message"));
        counts.put("skills", countOf("t_skill"));
        return counts;
    }

    private Object countOf(String table) {
        try {
            Long value = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
            return value == null ? -1 : value;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 精简驱动版本号。MySQL Connector/J 返回的形如
     * "mysql-connector-j-8.3.0 (Revision: 805f87...)"，括号那截对使用者没价值。
     */
    private String shortDriverVersion(String raw) {
        if (raw == null) {
            return null;
        }
        int paren = raw.indexOf('(');
        String trimmed = paren > 0 ? raw.substring(0, paren) : raw;
        return trimmed.trim();
    }

    /** 去掉 URL 里的参数（可能含密码等敏感信息），只留协议 + 主机 + 库名 */
    private String sanitizeUrl(String url) {        if (url == null) {
            return null;
        }
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    /** 从 JDBC URL 中解析 host:port，方便一眼看出连的是容器还是本机 */
    private String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            int start = url.indexOf("//");
            if (start < 0) {
                // H2 的 file: 形式，直接给路径
                return url.startsWith("jdbc:h2:") ? url.substring("jdbc:h2:".length()) : null;
            }
            String rest = url.substring(start + 2);
            int end = rest.indexOf('/');
            return end < 0 ? rest : rest.substring(0, end);
        } catch (Exception e) {
            return null;
        }
    }
}
