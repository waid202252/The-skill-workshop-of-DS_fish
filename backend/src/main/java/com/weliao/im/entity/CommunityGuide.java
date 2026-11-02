package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 社区内容：指南（GUIDE）或配方（RECIPE）。
 * <p>
 * 面向浅度用户，配方是主内容：效果、步骤、提示词、相关技能都在这里。
 */
@Entity
@Table(name = "t_community_guide", indexes = {
        @Index(name = "idx_community_guide_type", columnList = "type"),
        @Index(name = "idx_community_guide_published", columnList = "published_at"),
        @Index(name = "idx_community_guide_helpful", columnList = "helpful_count")
})
@Getter
@Setter
@NoArgsConstructor
public class CommunityGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GUIDE / RECIPE */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 配方可一键复制的内容：提示词、命令或配置 */
    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Column(length = 16)
    private String emoji;

    /** 逗号分隔，和 t_skill.topics 保持一致，避免数据库方言差异 */
    @Column(length = 512)
    private String tags;

    @Column(length = 20)
    private String difficulty;

    @Column(length = 50)
    private String duration;

    @Column(length = 100)
    private String cost;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = false;

    /** PUBLISHED / VERIFIED / DRAFT */
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus = "PUBLISHED";

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount = 0;

    @Column(name = "not_helpful_count", nullable = false)
    private int notHelpfulCount = 0;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
