package com.weliao.im.event;

import com.weliao.im.entity.UserType;

/**
 * 消息已落库且事务已提交。用于解耦「消息产生」与「消息之后要做的事」。
 * <p>
 * 为什么用应用内事件而不是让 MessageService 直接调 AI：
 * 否则 MessageService → AiService → MessageService 形成循环依赖。
 * 事件把这个环拆开，将来要加「敏感词审核」「消息推送第三方」也只是再加一个监听器。
 */
public record MessageCreatedEvent(
        Long messageId,
        Long conversationId,
        Long senderId,
        Long seq,
        String type,
        String content,
        UserType senderType
) {
}
