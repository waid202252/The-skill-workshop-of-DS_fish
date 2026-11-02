package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会话成员。lastReadSeq 记录该成员已读到的消息序号，用于计算未读数。
 */
@Entity
@Table(name = "t_conversation_member", uniqueConstraints = {
        @UniqueConstraint(name = "ux_member_conv_user", columnNames = {"conversation_id", "user_id"})
}, indexes = {
        @Index(name = "idx_member_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemberRole role = MemberRole.MEMBER;

    /** 已读位置，默认 0 表示一条没读。 */
    @Column(nullable = false)
    private Long lastReadSeq = 0L;

    @Column(nullable = false)
    private LocalDateTime joinedAt;
}
