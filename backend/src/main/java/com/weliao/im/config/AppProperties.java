package com.weliao.im.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置，对应 application.yml 里的 app.*。
 */
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();

    /** 上传文件落盘目录，相对路径以进程工作目录为准。 */
    private String uploadDir = "./uploads";

    /** 上传文件对外 URL 前缀。 */
    private String uploadUrlPrefix = "/uploads";

    /** 允许跨域的来源，逗号分隔。 */
    private String corsAllowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

    private Ai ai = new Ai();

    private Message message = new Message();

    private RateLimit rateLimit = new RateLimit();

    private GitHub github = new GitHub();

    private Community community = new Community();

    private Cluster cluster = new Cluster();

    /**
     * 跨节点推送配置（多实例部署时生效）。
     * <p>
     * 单实例时这些配置全部保持默认即可，ClusterRuntime 探测不到 Redis 会自动退回内存实现，
     * 行为和改造前完全一致。
     */
    @Getter
    @Setter
    public static class Cluster {
        /** auto：探测 Redis，通了就跨节点、没通就单机；local：强制单机；redis：强制 Redis（连不上启动失败） */
        private String mode = "auto";
        /** 节点标识，留空自动生成「主机名-随机串」 */
        private String nodeId = "";
        /** 在线状态有效期（秒）。客户端心跳周期是 25s，这里给 3 倍余量 */
        private int presenceTtlSeconds = 90;
        /** 清理过期字段的周期（秒），0 表示不清理 */
        private int sweepIntervalSeconds = 60;
    }

    /**
     * 社区配置。
     */
    @Getter
    @Setter
    public static class Community {
        /**
         * 首次启动且社区表为空时，导入 AI 整理的基础指南/配方。
         * 只在空表时执行，不会覆盖运营后续编辑的内容。
         */
        private boolean seedOnEmpty = true;
    }

    /**
     * GitHub 技能源配置。
     * <p>
     * 未认证配额很低（search 10 次/分钟、core 60 次/小时），
     * 配置 token 后提升到 30 次/分钟 / 5000 次/小时。
     */
    @Getter
    @Setter
    public static class GitHub {
        /** 只从环境变量注入，不要写进仓库 */
        private String token = "";
        /** 同一查询结果的本地缓存时长（秒），用来省 GitHub 配额 */
        private int searchCacheSeconds = 300;
        /** 默认搜索词：用户没输入时用它 */
        private String defaultQuery = "ai skill";
    }

    @Getter
    @Setter
    public static class Jwt {
        /** HS256 密钥，至少 32 字节。 */
        private String secret;
        /** Token 有效期（天）。 */
        private int expireDays = 7;
    }

    /** 消息相关策略 */
    @Getter
    @Setter
    public static class Message {
        /** 撤回时间窗口（分钟），0 表示不限制 */
        private int recallWindowMinutes = 2;
    }

    /**
     * 限流。固定窗口计数：Redis 可用时计数共享（多实例下限流真正全局生效），
     * Redis 不可用时退回本机内存计数。判据复用 ClusterRuntime，实现见
     * {@code config.RateLimitConfig} 与 {@code cluster.RedisRateLimitStore}。
     */
    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        /** 登录 / 注册：每个 IP 每分钟允许次数 */
        private int authPerMinute = 30;
        /** 发送消息：每个用户每分钟允许条数 */
        private int messagePerMinute = 60;
        /**
         * 是否信任 X-Forwarded-For。
         * <p>
         * 只有在前面确实有 nginx 之类的可信代理时才可打开——否则客户端可以伪造该头绕过限流。
         * 本地直连（dev）保持 false，容器部署（prod）置为 true。
         */
        private boolean trustForwardedFor = false;
    }

    /**
     * AI 伙伴配置。留空即回落到本地模拟实现，保证零配置可运行。
     */
    @Getter
    @Setter
    public static class Ai {
        private boolean enabled = true;
        /** OpenAI 兼容的接口根地址，例如 https://api.deepseek.com 或 https://xxx/v1 */
        private String baseUrl = "";
        /** 密钥，只从环境变量注入，不要写进仓库 */
        private String apiKey = "";
        /** 模型名，例如 deepseek-chat */
        private String model = "";
        private int timeoutSeconds = 30;
        private double temperature = 0.7;
        /** 带入上下文的历史消息条数（不是轮数），控制 token 成本 */
        private int maxHistoryMessages = 12;
        private String systemPrompt = "你是「微聊」里的 AI 伙伴，名字叫大肥鱼。回答简短、口语化、不超过 200 字。";
        /** AI 账号的用户名（唯一），用于定位这个特殊账号 */
        private String botUsername = "ai_fish";
        private String botNickname = "大肥鱼";
        private String botAvatar = "";
    }
}
