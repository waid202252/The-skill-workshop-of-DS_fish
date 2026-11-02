package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会话（单聊或群聊）。
 * <p>
 * maxSeq 是会话内的消息序号水位，发消息时对会话行加悲观锁后自增，
 * 保证同一会话内 seq 严格递增，从而支持「按 seq 游标分页」和「未读数」。
 */
@Entity
@Table(name = "t_conversation", indexes = {
        @Index(name = "idx_conv_updated", columnList = "updated_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationType type;

    @Column(length = 64)
    private String name;

    @Column(length = 255)
    private String avatar;

    private Long ownerId;

    /** 当前会话最大消息序号，初始 0。 */
    @Column(nullable = false)
    private Long maxSeq = 0L;

    private Long lastMessageId;

    /**
     * 单聊去重键："小userId:大userId"。群聊为 null（唯一索引允许多个 null）。
     * 用于防止并发打开同一对用户的单聊时建出两个会话，见 V4 迁移。
     */
    @Column(name = "single_key", length = 64)
    private String singleKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
