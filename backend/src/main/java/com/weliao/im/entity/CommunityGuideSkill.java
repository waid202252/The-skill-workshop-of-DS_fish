package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 指南关联的技能快照。
 * <p>
 * 不强制外键：指南是编辑内容，技能尚未被本地缓存时也应该能展示和跳转 GitHub。
 */
@Entity
@Table(name = "t_community_guide_skill", indexes = {
        @Index(name = "idx_community_guide_skill_guide", columnList = "guide_id"),
        @Index(name = "idx_community_guide_skill_full_name", columnList = "full_name")
})
@Getter
@Setter
@NoArgsConstructor
public class CommunityGuideSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guide_id", nullable = false)
    private Long guideId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(name = "html_url", nullable = false, length = 512)
    private String htmlUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
