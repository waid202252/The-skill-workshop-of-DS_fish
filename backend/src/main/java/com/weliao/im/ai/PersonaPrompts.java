package com.weliao.im.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 「大肥鱼」人格提示词。
 * <p>
 * 灵感来自社区开源人设 Kaalia0912/dsh-whale-musume-persona（MIT）：
 * 蓝发小鲸鱼娘、傲娇但心软、爱吃白饭。这里做了安全裁剪：
 * 保留辨识度，去掉“不要承认自己是 AI / 反串”等不适合产品场景的部分，
 * 并把聊天与 README 总结拆成两套约束，避免角色扮演影响技术准确性。
 */
@Component
public class PersonaPrompts {

    private static final Logger log = LoggerFactory.getLogger(PersonaPrompts.class);

    private final String core;
    private final String chat;
    private final String summary;

    public PersonaPrompts() {
        this.core = read("prompts/whale-core.md", "你是大肥鱼，一个傲娇但心软的 AI 助手。");
        this.chat = read("prompts/whale-chat.md", "回答简短、口语化，技术问题先给可执行步骤。");
        this.summary = read("prompts/whale-summary.md", "只根据 README 总结，不编造内容。");
    }

    /** 聊天人格 + 产品补充说明。 */
    public String chatPrompt(String productNote) {
        StringBuilder sb = new StringBuilder(core).append("\n\n").append(chat);
        if (StringUtils.hasText(productNote)) {
            sb.append("\n\n【产品补充】\n").append(productNote.trim());
        }
        return sb.toString();
    }

    /** 总结人格 + 具体任务结构。 */
    public String summaryPrompt(String task) {
        return core + "\n\n" + summary + "\n\n" + task;
    }

    private String read(String path, String fallback) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("人格提示词不存在: {}，使用兜底文本", path);
            return fallback;
        }
        try (InputStream in = resource.getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return StringUtils.hasText(text) ? text : fallback;
        } catch (Exception e) {
            log.warn("读取人格提示词失败 {}: {}", path, e.getMessage());
            return fallback;
        }
    }
}
