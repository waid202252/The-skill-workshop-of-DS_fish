package com.weliao.im.vo;

import java.time.LocalDateTime;

/**
 * 技能 README 的 AI 总结。
 *
 * @param aiGenerated true 表示真实模型生成；false 表示规则摘要/降级
 * @param model       当前模型链路，便于前端提示
 * @param notice      降级原因，正常时为 null
 */
public record SkillSummaryVO(
        Long skillId,
        String summary,
        boolean aiGenerated,
        String model,
        LocalDateTime generatedAt,
        String notice
) {
}
