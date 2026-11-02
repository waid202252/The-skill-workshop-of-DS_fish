package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 好友关系。双向各存一行，(userId -> friendId) 唯一，查询好友列表是单表扫描。
 */
@Entity
@Table(name = "t_friend", uniqueConstraints = {
        @UniqueConstraint(name = "ux_friend_pair", columnNames = {"user_id", "friend_id"})
}, indexes = {
        @Index(name = "idx_friend_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class FriendLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long friendId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
