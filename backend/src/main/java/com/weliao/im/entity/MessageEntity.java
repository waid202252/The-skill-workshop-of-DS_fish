package com.weliao.im.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 消息。(conversationId, seq) 唯一，seq 由会话锁保证递增。
 */
@Entity
@Table(name = "t_message", uniqueConstraints = {
        @UniqueConstraint(name = "ux_msg_conv_seq", columnNames = {"conversation_id", "seq"})
}, indexes = {
        @Index(name = "idx_msg_conv_id", columnList = "conversation_id,id")
})
@Getter
@Setter
@NoArgsConstructor
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private Long seq;

    @Column(nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 255)
    private String fileName;

    private Long fileSize;

    /** 客户端生成的幂等 id，用于把服务端消息对应回本地气泡。 */
    @Column(length = 64)
    private String clientMsgId;

    /** 是否已撤回。撤回后 content 被清空，但行保留、seq 不变。 */
    @Column(nullable = false)
    private boolean recalled = false;

    private LocalDateTime recalledAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
