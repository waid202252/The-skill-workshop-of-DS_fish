package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 指南反馈：有用 / 没帮助。同一用户对同一篇只保留一条。
 */
@Entity
@Table(name = "t_community_feedback", uniqueConstraints = {
        @UniqueConstraint(name = "ux_community_feedback_guide_user", columnNames = {"guide_id", "user_id"})
}, indexes = {
        @Index(name = "idx_community_feedback_guide", columnList = "guide_id")
})
@Getter
@Setter
@NoArgsConstructor
public class CommunityFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guide_id", nullable = false)
    private Long guideId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean helpful;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
