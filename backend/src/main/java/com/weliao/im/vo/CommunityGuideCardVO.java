package com.weliao.im.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区信息流卡片。
 */
public record CommunityGuideCardVO(
        Long id,
        String type,
        String title,
        String summary,
        String emoji,
        List<String> tags,
        String difficulty,
        String duration,
        String cost,
        boolean aiGenerated,
        int helpfulCount,
        LocalDateTime publishedAt,
        List<CommunitySkillVO> relatedSkills
) {
}
