package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户安装（收藏）的技能。技能本体全局共享，安装关系是每人一份。
 */
@Entity
@Table(name = "t_skill_install", uniqueConstraints = {
        @UniqueConstraint(name = "ux_install_user_skill", columnNames = {"user_id", "skill_id"})
}, indexes = {
        @Index(name = "idx_install_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SkillInstall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
