package com.weliao.im.vo;

import java.util.List;

/**
 * 技能卡片视图。字段和 GitHub 仓库一一对应，外加一个「我是否已安装」。
 */
public record SkillVO(
        Long id,
        Long githubId,
        String fullName,
        String owner,
        String ownerAvatar,
        String name,
        String description,
        Long stars,
        Long forks,
        String language,
        List<String> topics,
        String htmlUrl,
        String homepage,
        boolean installed,
        /** 本系统内被安装的次数 */
        long installCount
) {
}
