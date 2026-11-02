package com.weliao.im.service;

import com.weliao.im.ai.AiException;
import com.weliao.im.ai.AiProvider;
import com.weliao.im.ai.AiTurn;
import com.weliao.im.entity.CommunityGuide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 社区 AI 助教。
 * <p>
 * 只基于社区里已有的指南回答，避免模型凭空编造技能和命令。
 * 没有真实模型时退回“指南检索 + 摘要”的规则答案，保证零配置也能体验首答。
 */
@Service
public class CommunityAiService {

    private static final Logger log = LoggerFactory.getLogger(CommunityAiService.class);
    private static final int MAX_CONTEXT_CHARS = 4200;

    private final AiProvider provider;

    public CommunityAiService(AiProvider provider) {
        this.provider = provider;
    }

    public boolean isMock() {
        return "mock".equals(provider.name());
    }

    public String answer(String question, List<CommunityGuide> guides) {
        if (guides.isEmpty()) {
            return "我暂时没有找到足够具体的指南。\n\n"
                    + "你可以把问题说得更具体一点：想做什么、用的什么系统、卡在哪一步、报错原文是什么。"
                    + "有了这些信息，我才能给你更短的路径。";
        }

        String fallback = fallbackAnswer(guides);
        if (isMock()) {
            return fallback;
        }

        String context = buildContext(guides);
        String system = """
                你是「大肥鱼的工坊」社区 AI 助教，面向 AI 初学者和浅度用户。
                回答要求：
                1. 只使用给出的社区资料，不要编造不存在的命令、版本或链接。
                2. 先给最短可执行路径，再给必要的避坑提示。
                3. 资料不足时明确说“资料里没有”，并建议用户补充什么信息。
                4. 用中文，Markdown，控制在 400 字以内。
                """;
        String user = "用户问题：" + question + "\n\n社区资料：\n" + context;
        try {
            String reply = provider.reply(List.of(AiTurn.system(system), AiTurn.user(user)));
            return StringUtils.hasText(reply) ? reply.trim() : fallback;
        } catch (AiException e) {
            log.warn("社区 AI 首答失败 provider={}: {}", provider.name(), e.getMessage());
            return fallback;
        }
    }

    private String fallbackAnswer(List<CommunityGuide> guides) {
        StringBuilder sb = new StringBuilder("先给你一条最短路径：\n\n");
        for (int i = 0; i < guides.size(); i++) {
            CommunityGuide guide = guides.get(i);
            sb.append("**").append(guide.getTitle()).append("**\n\n")
                    .append(guide.getSummary()).append("\n\n");
            if (i == 0) {
                String excerpt = excerpt(guide.getContent());
                if (!excerpt.isBlank()) {
                    sb.append(excerpt).append("\n\n");
                }
            }
        }
        sb.append("如果还是卡住，把报错原文、系统版本和已经试过的步骤发出来，我再帮你缩小范围。");
        return sb.toString();
    }

    /** 把 Markdown 压成一段可读摘录，避免首答只给一句摘要 */
    private String excerpt(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        String text = markdown
                .replaceAll("(?m)^#{1,3}\\s*", "")
                .replace("**", "")
                .replace("`", "")
                .replaceAll("\\n{2,}", "\n")
                .trim();
        return text.length() > 420 ? text.substring(0, 420) + "…" : text;
    }

    private String buildContext(List<CommunityGuide> guides) {
        StringBuilder sb = new StringBuilder();
        for (CommunityGuide guide : guides) {
            String body = guide.getContent() == null ? "" : guide.getContent();
            if (body.length() > 900) {
                body = body.substring(0, 900) + "…";
            }
            String block = "【" + guide.getTitle() + "】\n"
                    + "摘要：" + guide.getSummary() + "\n"
                    + body + "\n\n";
            if (sb.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            sb.append(block);
        }
        return sb.toString();
    }
}
