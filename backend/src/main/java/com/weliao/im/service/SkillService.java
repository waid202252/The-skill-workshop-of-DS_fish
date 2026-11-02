package com.weliao.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.weliao.im.common.BizException;
import com.weliao.im.common.TtlCache;
import com.weliao.im.config.AppProperties;
import com.weliao.im.entity.Skill;
import com.weliao.im.entity.SkillInstall;
import com.weliao.im.repository.SkillInstallRepository;
import com.weliao.im.repository.SkillRepository;
import com.weliao.im.vo.SkillCategoryVO;
import com.weliao.im.vo.SkillReadmeVO;
import com.weliao.im.vo.SkillSearchVO;
import com.weliao.im.vo.SkillVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 技能市场：从 GitHub 拉取技能仓库、缓存到本地、维护用户的安装关系。
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private static final int MAX_PER_PAGE = 50;

    /** 查询结果缓存：key = query|page|size。上限 200 条，避免长尾查询词把内存撑爆 */
    private record QueryHit(List<Long> ids, long total) {
    }

    private final SkillRepository skillRepository;
    private final SkillInstallRepository installRepository;
    private final GitHubClient gitHubClient;
    private final AppProperties props;

    private final TtlCache<String, QueryHit> queryCache = new TtlCache<>(200);

    /** README 抓取缓存，单条最大 60KB，所以容量给得保守一些 */
    private record ReadmeHit(String fileName, String content, String sourceUrl) {
    }

    private static final long README_TTL_MS = 10 * 60 * 1000L;
    private static final int README_MAX_CHARS = 60_000;
    private final TtlCache<Long, ReadmeHit> readmeCache = new TtlCache<>(50);

    /** GitHub 结果落库要在一个事务里完成，避免半批数据 */
    private final TransactionTemplate transactionTemplate;

    public SkillService(SkillRepository skillRepository,
                        SkillInstallRepository installRepository,
                        GitHubClient gitHubClient,
                        AppProperties props,
                        PlatformTransactionManager transactionManager) {
        this.skillRepository = skillRepository;
        this.installRepository = installRepository;
        this.gitHubClient = gitHubClient;
        this.props = props;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- 搜索

    public SkillSearchVO search(Long userId, String rawQuery, int page, int perPage) {
        int safePage = Math.max(1, page);
        int size = Math.max(1, Math.min(perPage, MAX_PER_PAGE));
        String query = StringUtils.hasText(rawQuery)
                ? rawQuery.trim()
                : props.getGithub().getDefaultQuery();
        String key = query + "|" + safePage + "|" + size;

        QueryHit cached = queryCache.get(key);
        if (cached != null) {
            return buildResult(userId, cached.ids(), cached.total(), safePage, size, true, false, null);
        }

        try {
            JsonNode body = gitHubClient.searchRepositories(query, safePage, size);
            // 落库放进一个事务：中途失败不会留下半批技能
            List<Skill> saved = transactionTemplate.execute(status -> upsert(body.path("items")));
            if (saved == null) {
                saved = List.of();
            }
            long total = body.path("total_count").asLong(0);
            List<Long> ids = saved.stream().map(Skill::getId).toList();

            long ttlMs = Math.max(30, props.getGithub().getSearchCacheSeconds()) * 1000L;
            queryCache.put(key, new QueryHit(ids, total), ttlMs);

            return buildResult(userId, ids, total, safePage, size, false, false, null);
        } catch (GitHubException e) {
            // 降级：GitHub 不可用时，用已经缓存过的技能做本地模糊搜索
            log.warn("GitHub 搜索失败，降级到本地缓存: {}", e.getMessage());
            List<Skill> local = skillRepository.searchLocal(query, PageRequest.of(0, size));
            List<Long> ids = local.stream().map(Skill::getId).toList();
            String notice = e.isRateLimited()
                    ? "GitHub 接口限流，当前显示的是本地已缓存的技能。配置 GITHUB_TOKEN 可解除限制。"
                    : e.getMessage();
            return buildResult(userId, ids, ids.size(), 1, size, false, true, notice);
        }
    }

    // ---------------------------------------------------------------- 安装

    @Transactional
    public SkillVO install(Long userId, Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> BizException.notFound("技能不存在"));
        if (!installRepository.existsByUserIdAndSkillId(userId, skillId)) {
            SkillInstall install = new SkillInstall();
            install.setUserId(userId);
            install.setSkillId(skillId);
            install.setCreatedAt(LocalDateTime.now());
            installRepository.save(install);
        }
        return toVO(skill, true, installRepository.countBySkillId(skillId));
    }

    @Transactional
    public void uninstall(Long userId, Long skillId) {
        installRepository.deleteByUserIdAndSkillId(userId, skillId);
    }

    public List<SkillVO> listInstalled(Long userId) {
        List<SkillInstall> installs = installRepository.findByUserId(userId);
        if (installs.isEmpty()) {
            return List.of();
        }
        // 最近安装的排在前面
        installs.sort(Comparator.comparing(SkillInstall::getCreatedAt).reversed());
        Set<Long> ids = installs.stream().map(SkillInstall::getSkillId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Skill> byId = skillRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        List<SkillVO> result = new ArrayList<>();
        for (Long id : ids) {
            Skill skill = byId.get(id);
            if (skill != null) {
                result.add(toVO(skill, true, installRepository.countBySkillId(id)));
            }
        }
        return result;
    }

    /** 首页推荐：本地缓存里 star 最高的，不需要消耗 GitHub 配额 */
    public List<SkillVO> trending(Long userId) {
        List<Skill> skills = skillRepository.findTop20ByOrderByStarsDesc();
        return withInstalledFlag(userId, skills);
    }

    public SkillVO detail(Long userId, Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> BizException.notFound("技能不存在"));
        return toVO(skill, installRepository.existsByUserIdAndSkillId(userId, skillId),
                installRepository.countBySkillId(skillId));
    }

    // ---------------------------------------------------------------- 推荐分类

    /**
     * 预置分类。「推荐」的价值就是替用户想好搜什么，
     * 而不是丢一个空搜索框让他自己编关键词。
     */
    public List<SkillCategoryVO> categories() {
        return List.of(
                new SkillCategoryVO("video", "AI 视频生成", "ai video generation"),
                new SkillCategoryVO("image", "图像生成", "ai image generation"),
                new SkillCategoryVO("agent", "Agent 框架", "ai agent framework"),
                new SkillCategoryVO("prompt", "提示词工程", "prompt engineering"),
                new SkillCategoryVO("audio", "语音与音频", "text to speech ai"),
                new SkillCategoryVO("coding", "代码助手", "ai coding assistant"),
                new SkillCategoryVO("rag", "知识库 RAG", "rag llm"),
                new SkillCategoryVO("workflow", "工作流编排", "ai workflow automation"));
    }

    // ---------------------------------------------------------------- 下载

    /**
     * 取 README / SKILL.md 正文。优先 SKILL.md——AI 技能类仓库常用它作为"说明书"。
     */
    public SkillReadmeVO readme(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> BizException.notFound("技能不存在"));

        ReadmeHit cached = readmeCache.get(skillId);
        if (cached != null) {
            return toReadmeVO(skillId, cached.fileName(), cached.content(), cached.sourceUrl());
        }

        GitHubClient.ReadmeResult result = gitHubClient.fetchReadme(
                skill.getOwner(), skill.getName(), skill.getDefaultBranch());
        if (result == null) {
            throw BizException.notFound("该仓库没有找到 README.md 或 SKILL.md");
        }
        readmeCache.put(skillId,
                new ReadmeHit(result.fileName(), result.content(), result.sourceUrl()), README_TTL_MS);
        return toReadmeVO(skillId, result.fileName(), result.content(), result.sourceUrl());
    }

    private SkillReadmeVO toReadmeVO(Long skillId, String fileName, String content, String sourceUrl) {
        boolean truncated = content.length() > README_MAX_CHARS;
        String body = truncated ? content.substring(0, README_MAX_CHARS) : content;
        return new SkillReadmeVO(skillId, fileName, body, sourceUrl, truncated);
    }

    // ---------------------------------------------------------------- 内部

    /** 把 GitHub 返回的仓库列表落库（存在则更新），保持 GitHub 的排序 */
    private List<Skill> upsert(JsonNode items) {
        List<Skill> result = new ArrayList<>();
        if (items == null || !items.isArray()) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode item : items) {
            long githubId = item.path("id").asLong(0);
            String fullName = item.path("full_name").asText("");
            if (githubId <= 0 || fullName.isBlank()) {
                continue;
            }
            Skill skill = skillRepository.findByGithubId(githubId).orElseGet(Skill::new);
            skill.setGithubId(githubId);
            skill.setFullName(truncate(fullName, 200));
            skill.setOwner(truncate(item.path("owner").path("login").asText(""), 100));
            skill.setOwnerAvatar(truncate(textOrNull(item.path("owner").path("avatar_url")), 255));
            skill.setName(truncate(item.path("name").asText(""), 150));
            skill.setDescription(truncate(textOrNull(item.path("description")), 1024));
            skill.setStars(item.path("stargazers_count").asLong(0));
            skill.setForks(item.path("forks_count").asLong(0));
            skill.setLanguage(truncate(textOrNull(item.path("language")), 60));
            skill.setTopics(truncate(joinTopics(item.path("topics")), 512));
            skill.setHtmlUrl(truncate(item.path("html_url").asText(""), 512));
            skill.setHomepage(truncate(textOrNull(item.path("homepage")), 512));
            skill.setDefaultBranch(truncate(textOrNull(item.path("default_branch")), 100));
            skill.setFetchedAt(now);
            result.add(skillRepository.save(skill));
        }
        return result;
    }

    private SkillSearchVO buildResult(Long userId, List<Long> ids, long total, int page, int size,
                                      boolean cached, boolean degraded, String notice) {
        if (ids.isEmpty()) {
            return new SkillSearchVO(List.of(), total, page, size, cached, degraded, notice);
        }
        Map<Long, Skill> byId = skillRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        List<Skill> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new SkillSearchVO(withInstalledFlag(userId, ordered), total, page, size, cached, degraded, notice);
    }

    /** 批量查安装状态，避免每个卡片查一次库 */
    private List<SkillVO> withInstalledFlag(Long userId, List<Skill> skills) {
        if (skills.isEmpty()) {
            return List.of();
        }
        Set<Long> skillIds = skills.stream().map(Skill::getId).collect(Collectors.toSet());
        Set<Long> installed = installRepository.findByUserIdAndSkillIdIn(userId, skillIds).stream()
                .map(SkillInstall::getSkillId)
                .collect(Collectors.toSet());
        List<SkillVO> result = new ArrayList<>(skills.size());
        for (Skill skill : skills) {
            result.add(toVO(skill, installed.contains(skill.getId()), installRepository.countBySkillId(skill.getId())));
        }
        return result;
    }

    private SkillVO toVO(Skill skill, boolean installed, long installCount) {
        return new SkillVO(
                skill.getId(),
                skill.getGithubId(),
                skill.getFullName(),
                skill.getOwner(),
                skill.getOwnerAvatar(),
                skill.getName(),
                skill.getDescription(),
                skill.getStars(),
                skill.getForks(),
                skill.getLanguage(),
                splitTopics(skill.getTopics()),
                skill.getHtmlUrl(),
                skill.getHomepage(),
                installed,
                installCount);
    }

    private String joinTopics(JsonNode topics) {
        if (topics == null || !topics.isArray() || topics.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (JsonNode t : topics) {
            list.add(t.asText());
            if (list.size() >= 20) {
                break;
            }
        }
        return String.join(",", list);
    }

    private List<String> splitTopics(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
