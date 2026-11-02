package com.weliao.im.service;

import com.weliao.im.ai.AiException;
import com.weliao.im.ai.AiProvider;
import com.weliao.im.ai.AiTurn;
import com.weliao.im.ai.PersonaPrompts;
import com.weliao.im.common.BizException;
import com.weliao.im.common.TtlCache;
import com.weliao.im.entity.Skill;
import com.weliao.im.repository.SkillRepository;
import com.weliao.im.vo.SkillReadmeVO;
import com.weliao.im.vo.SkillSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用当前选中技能的 README 生成面向浅度用户的中文总结。
 * <p>
 * 关键约束：
 * 1. 只把当前技能的 README 作为资料，避免模型拿别的仓库内容回答；
 * 2. 真实模型不可用时退回规则摘要，并明确标记，不假装是 AI 输出；
 * 3. 结果缓存 30 分钟，同一技能重复打开不重复跑模型。
 */
@Service
public class SkillSummaryService {

    private static final Logger log = LoggerFactory.getLogger(SkillSummaryService.class);
    private static final int MAX_README_CHARS = 2600;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    private final SkillRepository skillRepository;
    private final SkillService skillService;
    private final AiProvider provider;
    private final PersonaPrompts personaPrompts;
    private final TtlCache<Long, SkillSummaryVO> cache = new TtlCache<>(100);

    public SkillSummaryService(SkillRepository skillRepository, SkillService skillService,
                               AiProvider provider, PersonaPrompts personaPrompts) {
        this.skillRepository = skillRepository;
        this.skillService = skillService;
        this.provider = provider;
        this.personaPrompts = personaPrompts;
    }

    public SkillSummaryVO summarize(Long skillId, boolean refresh) {
        if (!refresh) {
            SkillSummaryVO cached = cache.get(skillId);
            if (cached != null) {
                return cached;
            }
        }
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> BizException.notFound("技能不存在"));

        String readme = "";
        try {
            SkillReadmeVO vo = skillService.readme(skillId);
            readme = vo == null ? "" : vo.markdown();
        } catch (Exception e) {
            log.warn("读取 README 失败 skillId={}: {}", skillId, e.getMessage());
        }

        SkillSummaryVO result = generate(skill, readme);
        cache.put(skillId, result, CACHE_TTL_MS);
        return result;
    }

    private SkillSummaryVO generate(Skill skill, String rawReadme) {
        String readme = cleanReadme(rawReadme);
        if (isMock()) {
            return fallback(skill, readme,
                    "当前未接入真实模型，已使用 README 规则摘要。配置 Ollama 或 OpenAI 兼容模型后会自动切换。");
        }
        if (!StringUtils.hasText(readme)) {
            return fallback(skill, "",
                    "没有读取到 README 正文，无法生成 AI 总结。");
        }

        String system = personaPrompts.summaryPrompt("""
                当前任务：面向 AI 初学者和浅度用户总结 GitHub 技能。
                只能依据用户提供的 README 和仓库信息，不得编造 README 没有写的安装命令、版本号、链接或功能。
                如果 README 没有说明，就明确写“README 未说明”。
                输出中文 Markdown，严格使用以下结构，总字数控制在 450 字以内：
                ## 大肥鱼点评
                2 到 4 句，必须结合这个 README 的具体能力、门槛和适合场景，用大肥鱼口吻写。
                至少自然出现一次自称（本鲸鱼娘 / 本小姐 / 大肥鱼），但不要固定开场，也不要复用同一句套话。
                禁止使用“看起来挺高级”“门槛有点高”“本鲸鱼娘都心动了”“小玩意儿”“摆烂”这类通用句；
                必须写出至少一个 README 里的具体功能或限制，并给出一个具体适合场景。
                可以傲娇、吐槽门槛，但不要贬低项目、不要编造事实；不超过 120 字。
                ## 一句话
                直接给结论，不要复述本节提示。
                ## 它能做什么
                - 2 到 4 条具体能力
                ## 适合谁
                一句话说明适合的人群，以及不适合谁。
                ## 怎么开始
                1. 2 到 4 步最短上手路径；没有写命令就不要编。
                ## 注意
                - 1 到 3 条 README 里提到的限制、依赖或风险。
                """);
        String user = "技能名称：" + skill.getName() + "\n"
                + "仓库：" + skill.getFullName() + "\n"
                + "官方描述：" + (StringUtils.hasText(skill.getDescription()) ? skill.getDescription() : "未提供") + "\n"
                + "主要语言：" + (StringUtils.hasText(skill.getLanguage()) ? skill.getLanguage() : "未标注") + "\n"
                + "本次点评角度：" + commentaryAngle(skill) + "\n\n"
                + "README（可能被截断）：\n" + readme;

        try {
            String reply = provider.reply(List.of(AiTurn.system(system), AiTurn.user(user)));
            if (!StringUtils.hasText(reply)) {
                throw new AiException("模型返回为空", true);
            }
            String body = sanitize(reply);
            // 不再固定开场：点评由模型结合当前 README 生成，避免每条总结都长一样。
            return new SkillSummaryVO(skill.getId(), body, true, provider.name(),
                    LocalDateTime.now(), null);
        } catch (Exception e) {
            log.warn("AI 总结失败 skillId={} model={}: {}", skill.getId(), provider.name(), e.getMessage());
            return fallback(skill, readme, "AI 模型暂时不可用，已退回规则摘要：" + shortMessage(e.getMessage()));
        }
    }

    private boolean isMock() {
        return "mock".equals(provider.name());
    }

    /** 给点评一个差异化切入角度，避免每个技能都用同一套“高级/门槛高”话术。 */
    private String commentaryAngle(Skill skill) {
        String hay = ((skill.getName() == null ? "" : skill.getName()) + " "
                + (skill.getDescription() == null ? "" : skill.getDescription()) + " "
                + (skill.getTopics() == null ? "" : skill.getTopics())).toLowerCase();
        if (hay.contains("agent") || hay.contains("workflow") || hay.contains("编排")) {
            return "从工作流编排、部署门槛和适合的团队角色切入";
        }
        if (hay.contains("rag") || hay.contains("retrieval") || hay.contains("知识库") || hay.contains("文档")) {
            return "从文档质量、检索效果和知识库维护成本切入";
        }
        if (hay.contains("image") || hay.contains("video") || hay.contains("audio")
                || hay.contains("diffusion") || hay.contains("语音") || hay.contains("图像")) {
            return "从生成效果、硬件成本和素材版权切入";
        }
        if (hay.contains("python")) {
            return "从 Python 依赖、学习曲线和二次开发成本切入";
        }
        return "从它解决的具体问题、维护状态和替代方案切入";
    }

    /** 清理模型偶尔抄回来的任务提示和旧固定开场 */
    private String sanitize(String text) {
        return text.replaceAll("(?m)^\\s*用一句话说明.*$", "")
                .replaceAll("(?m)^\\s*直接给结论.*$", "")
                .replace("哼，本小姐替你看过了。", "")
                .replace("本小姐替你看过了。", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private SkillSummaryVO fallback(Skill skill, String readme, String notice) {
        List<String> points = extractPoints(readme);
        StringBuilder sb = new StringBuilder();
        sb.append("## 一句话\n")
                .append(StringUtils.hasText(skill.getDescription())
                        ? skill.getDescription()
                        : "README 没有提供一句话简介，建议直接看原文。")
                .append("\n\n## README 要点\n");
        if (points.isEmpty()) {
            sb.append("- 暂无可提取的要点。\n");
        } else {
            for (String point : points) {
                sb.append("- ").append(point).append("\n");
            }
        }
        sb.append("\n## 适合谁\n")
                .append("需要先确认它解决的问题和你的场景是否一致；README 未说明的部分不要自行假设。\n")
                .append("\n## 怎么开始\n")
                .append("1. 在 README 里找 Quick Start / Install。\n")
                .append("2. 核对 Requirements 里的版本要求。\n")
                .append("3. 先跑通示例，再改配置。\n")
                .append("\n## 注意\n")
                .append("- 这是规则摘要，不是模型生成；请以 README 原文为准。\n");
        return new SkillSummaryVO(skill.getId(), sb.toString(), false, provider.name(),
                LocalDateTime.now(), notice);
    }

    private List<String> extractPoints(String readme) {
        List<String> points = new ArrayList<>();
        if (!StringUtils.hasText(readme)) {
            return points;
        }
        for (String rawLine : readme.split("\\R")) {
            String line = rawLine.trim()
                    .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("^[-*+]\\s+", "")
                    .trim();
            if (line.length() < 16 || line.length() > 160) {
                continue;
            }
            if (line.startsWith("#") || line.startsWith(">") || line.startsWith("```")
                    || line.startsWith("|") || line.startsWith("[!") || line.startsWith("http")) {
                continue;
            }
            points.add(line);
            if (points.size() >= 4) {
                break;
            }
        }
        return points;
    }

    private String cleanReadme(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String text = raw
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return text.length() > MAX_README_CHARS ? text.substring(0, MAX_README_CHARS) + "\n…" : text;
    }

    private String shortMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() > 120 ? message.substring(0, 120) + "…" : message;
    }
}
