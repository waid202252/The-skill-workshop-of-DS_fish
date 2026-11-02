package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * GitHub 技能仓库的本地缓存。
 * <p>
 * 为什么一定要缓存：GitHub 未认证配额只有 search 10 次/分钟、core 60 次/小时，
 * 前端每输入一个字符就查一次的话，几秒钟就会被限流。
 */
@Entity
@Table(name = "t_skill", indexes = {
        @Index(name = "idx_skill_stars", columnList = "stars"),
        @Index(name = "idx_skill_fetched", columnList = "fetched_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_skill_github_id", columnNames = {"github_id"}),
        @UniqueConstraint(name = "ux_skill_full_name", columnNames = {"full_name"})
})
@Getter
@Setter
@NoArgsConstructor
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GitHub 仓库 id */
    @Column(name = "github_id", nullable = false)
    private Long githubId;

    /** owner/repo */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(name = "owner_avatar", length = 255)
    private String ownerAvatar;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1024)
    private String description;

    @Column(nullable = false)
    private Long stars = 0L;

    @Column(nullable = false)
    private Long forks = 0L;

    @Column(length = 60)
    private String language;

    /** 逗号分隔的 topics，例如 "ai,video-generation" */
    @Column(length = 512)
    private String topics;

    @Column(name = "html_url", nullable = false, length = 512)
    private String htmlUrl;

    @Column(length = 512)
    private String homepage;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
