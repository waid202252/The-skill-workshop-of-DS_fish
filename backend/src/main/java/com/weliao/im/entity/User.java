package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户。表名加 t_ 前缀，避免和 SQL 保留字 user 冲突。
 */
@Entity
@Table(name = "t_user", indexes = {
        @Index(name = "ux_user_username", columnList = "username", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String username;

    /** BCrypt 哈希，永不返回给前端。 */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 32)
    private String nickname;

    /**
     * 账号类型。BOT 是内置 AI 伙伴，在会话系统里与真人完全对等。
     * 数据库层面允许为空（兼容历史数据），代码里按 HUMAN 处理。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private UserType type = UserType.HUMAN;

    @Column(length = 255)
    private String avatar;

    @Column(length = 128)
    private String signature;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;

    /**
     * 内置 AI 伙伴是否「始终在线」。
     * <p>
     * BOT 从来不会建立 WebSocket 长连接，所以在 PresenceStore 里永远查不到它，
     * 界面就会一直显示「离线」——一个你随时能@的 AI 伙伴挂着离线标记，观感很差。
     * <p>
     * 这里的判断是<b>展示规则</b>，不是连接事实：真人仍然以 {@code PresenceStore} 为准，
     * 只有 BOT 因为「没有连接也能秒回」这个特性，在展示上视为在线。
     * 放在实体上而不是塞进 WsHub，是为了让「什么算在线」这条规则只有一处。
     */
    public boolean isAlwaysOnline() {
        return type == UserType.BOT;
    }
}
