package com.weliao.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.weliao.im.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * GitHub REST API 客户端（只用到仓库搜索）。
 * <p>
 * 配额现实（未认证）：search 10 次/分钟、core 60 次/小时。
 * 所以调用方必须做缓存，这里也会把限流单独识别出来，让上层能降级而不是直接报错。
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    private final RestClient client;
    private final AppProperties props;

    public GitHubClient(AppProperties props) {
        this.props = props;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(15000);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "weliao-skill-client");

        // 配了 token 配额会从 10/min 提到 30/min，core 从 60/h 提到 5000/h
        if (StringUtils.hasText(props.getGithub().getToken())) {
            builder = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getGithub().getToken());
            log.info("GitHub 调用已启用 token（配额提升）");
        } else {
            log.warn("GitHub 未配置 token，搜索配额仅 10 次/分钟，建议设置环境变量 GITHUB_TOKEN");
        }

        this.client = builder.build();
    }

    /**
     * 搜索仓库。
     *
     * @param query   搜索词
     * @param page    页码（从 1 开始）
     * @param perPage 每页条数（GitHub 上限 100）
     */
    public JsonNode searchRepositories(String query, int page, int perPage) {
        try {
            return client.get()
                    .uri(uri -> uri.path("/search/repositories")
                            .queryParam("q", query)
                            .queryParam("sort", "stars")
                            .queryParam("order", "desc")
                            .queryParam("page", page)
                            .queryParam("per_page", Math.min(perPage, 100))
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String remaining = e.getResponseHeaders() == null ? null
                    : e.getResponseHeaders().getFirst("X-RateLimit-Remaining");
            if (status == 403 || status == 429) {
                log.warn("GitHub 限流: status={} remaining={}", status, remaining);
                throw new GitHubException(
                        "GitHub 接口达到限流（搜索 10 次/分钟）。配置 GITHUB_TOKEN 可提升到 30 次/分钟。", true);
            }
            if (status == 422) {
                // 查询语法不合法，重试没意义
                throw new GitHubException("搜索条件不合法，请换一个关键词", false);
            }
            log.warn("GitHub 返回异常: status={}", status);
            throw new GitHubException("GitHub 接口异常（HTTP " + status + "）", false);
        } catch (Exception e) {
            log.warn("连接 GitHub 失败: {}", e.getMessage());
            throw new GitHubException("连接 GitHub 失败，请检查网络", true);
        }
    }

    /**
     * 拉取仓库的说明文件。
     * <p>
     * 依次尝试 SKILL.md、README.md、readme.md、README_EN.md —— AI 技能类仓库常常用 SKILL.md
     * 作为「技能说明书」，优先取它更符合本产品的定位。
     * <p>
     * 两条通路（踩过的坑）：
     * <ol>
     *   <li>首选 {@code raw.githubusercontent.com}：不计配额、返回纯文本</li>
     *   <li>失败时兜底 {@code api.github.com/repos/{o}/{r}/readme}：国内网络下 raw 域名经常超时，
     *       而 api 域名一直是通的（搜索功能就用它）。代价是消耗 core 配额（未认证 60 次/小时），
     *       所以只在 raw 失败时才走</li>
     * </ol>
     * 另外区分了「文件真的不存在」和「网络失败」：原来两者都被吞成"没找到 README"，
     * 用户看到的是「该仓库没有 README」，其实只是超时，属于误导。
     *
     * @return 拿到的内容（含文件名）；文件确实不存在时返回 null
     * @throws GitHubException 网络不通且兜底也失败
     */
    public ReadmeResult fetchReadme(String owner, String repo, String branch) {
        String safeBranch = StringUtils.hasText(branch) ? branch : "main";

        // 主通路：api.github.com 的 readme 接口。
        // 为什么不再优先 raw：raw.githubusercontent.com 在国内会间歇性解析/连接失败，
        // 而 Java 的 connectTimeout 管不到 DNS 解析，实测能把请求拖过 nginx 的 60 秒上限（504）。
        // api 域名搜索功能一直在用，稳定性好得多；代价是消耗 core 配额（未认证 60 次/小时，
        // 配置 GITHUB_TOKEN 后 5000 次/小时），所以必须配合 10 分钟的 readmeCache。
        ReadmeResult viaApi = fetchReadmeViaApi(owner, repo);
        if (viaApi != null) {
            return viaApi;
        }

        // 兜底：raw 域名，逐文件名尝试。只在 API 失败（限流/异常）时才走。
        String[] candidates = {"SKILL.md", "README.md", "readme.md", "README_EN.md"};
        String networkError = null;
        // 总时长上限：单个文件超时最长可达 15 秒，4 个文件串起来会超过 nginx 的 60 秒代理超时，
        // 用户在页面上只会看到 504。这里给 raw 兜底一个 12 秒预算，用完就放弃。
        long deadline = System.currentTimeMillis() + 12_000L;
        for (String fileName : candidates) {
            if (System.currentTimeMillis() > deadline) {
                log.warn("raw 兜底超时预算用尽，放弃剩余文件");
                break;
            }
            String url = "https://raw.githubusercontent.com/" + owner + "/" + repo
                    + "/" + safeBranch + "/" + fileName;
            try {
                String body = client.get()
                        .uri(url)
                        .retrieve()
                        .body(String.class);
                if (StringUtils.hasText(body)) {
                    return new ReadmeResult(fileName, body, url);
                }
            } catch (HttpClientErrorException e) {
                if (!(e instanceof HttpClientErrorException.NotFound)) {
                    networkError = "HTTP " + e.getStatusCode().value();
                    break;
                }
            } catch (Exception e) {
                networkError = e.getMessage();
                log.warn("raw 域名兜底也失败: {}", url);
                break;
            }
        }

        if (networkError != null) {
            log.warn("拉取说明文档失败 {}/{}@{}: {}", owner, repo, safeBranch, networkError);
            throw new GitHubException("拉取说明文档超时（访问 GitHub 的网络不稳定），请稍后重试", true);
        }
        log.debug("未找到说明文件: {}/{}@{}", owner, repo, safeBranch);
        return null;
    }

    /**
     * 兜底：GitHub 官方 API 的 readme 接口，返回 base64 内容。
     * 会消耗 core 配额，只在 raw 域名不可用时使用。
     */
    private ReadmeResult fetchReadmeViaApi(String owner, String repo) {
        try {
            JsonNode node = client.get()
                    .uri(uri -> uri.path("/repos/{owner}/{repo}/readme")
                            .build(owner, repo))
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                return null;
            }
            String base64 = node.path("content").asText("");
            if (!StringUtils.hasText(base64)) {
                return null;
            }
            String content = new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
            String name = node.path("name").asText("README.md");
            String downloadUrl = node.path("download_url").asText(null);
            return new ReadmeResult(name, content, downloadUrl);
        } catch (Exception e) {
            log.warn("API 兜底也失败 {}/{}: {}", owner, repo, e.getMessage());
            return null;
        }
    }

    /** 说明文件的抓取结果 */
    public record ReadmeResult(String fileName, String content, String sourceUrl) {
    }
}
