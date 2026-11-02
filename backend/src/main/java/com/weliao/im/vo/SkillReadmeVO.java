package com.weliao.im.vo;

/**
 * 技能的 README 内容。
 *
 * @param skillId   技能 id
 * @param fileName  文件名（README.md / SKILL.md）
 * @param markdown  正文（过长会被截断）
 * @param sourceUrl 原始地址，便于前端「在 GitHub 查看」
 * @param truncated true 表示内容被截断
 */
public record SkillReadmeVO(
        Long skillId,
        String fileName,
        String markdown,
        String sourceUrl,
        boolean truncated
) {
}
