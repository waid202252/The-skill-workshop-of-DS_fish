package com.weliao.im.vo;

/**
 * 指南关联技能的快照。
 */
public record CommunitySkillVO(
        String fullName,
        String name,
        String owner,
        String htmlUrl
) {
}
