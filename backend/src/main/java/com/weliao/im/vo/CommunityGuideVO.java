package com.weliao.im.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区指南/配方详情。
 */
public record CommunityGuideVO(
        Long id,
        String type,
        String title,
        String summary,
        String content,
        String promptText,
        String emoji,
        List<String> tags,
        String difficulty,
        String duration,
        String cost,
        boolean aiGenerated,
        String reviewStatus,
        String reviewedBy,
        int helpfulCount,
        int notHelpfulCount,
        int viewCount,
        LocalDateTime publishedAt,
        List<CommunitySkillVO> relatedSkills,
        Boolean myFeedback
) {
}
