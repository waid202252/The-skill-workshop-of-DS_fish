package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户向 AI 助教提出的问题与首答。
 * <p>
 * 试验期不直接公开成帖子，先收集真实问题；被标记“没帮助”的进入人工补充队列。
 */
@Entity
@Table(name = "t_community_question", indexes = {
        @Index(name = "idx_community_question_user", columnList = "user_id"),
        @Index(name = "idx_community_question_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class CommunityQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** AI_ANSWERED / NEEDS_HUMAN / RESOLVED */
    @Column(nullable = false, length = 20)
    private String status = "AI_ANSWERED";

    private Boolean helpful;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
