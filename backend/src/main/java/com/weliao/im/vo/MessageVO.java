package com.weliao.im.vo;

import java.time.LocalDateTime;

/**
 * 消息视图。
 */
public record MessageVO(
        Long id,
        Long conversationId,
        Long seq,
        Long senderId,
        String senderNickname,
        String senderAvatar,
        String type,
        String content,
        String fileName,
        Long fileSize,
        LocalDateTime createdAt,
        String clientMsgId,
        /** 已撤回：前端据此渲染「已撤回」占位，此时 content 为空串 */
        boolean recalled
) {
}
