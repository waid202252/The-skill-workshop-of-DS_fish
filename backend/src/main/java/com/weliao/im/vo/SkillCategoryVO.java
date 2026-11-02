package com.weliao.im.vo;

/**
 * 技能分类（推荐维度的核心）。
 * <p>
 * 每个分类映射到一个 GitHub 搜索查询，前端点一下就是一个预设搜索。
 * 用固定映射而不是让用户自己编查询词，因为"推荐"的价值就在于帮用户想好搜什么。
 *
 * @param key   前端用的稳定标识
 * @param label 展示名
 * @param query 对应的 GitHub 查询语句
 */
public record SkillCategoryVO(String key, String label, String query) {
}
