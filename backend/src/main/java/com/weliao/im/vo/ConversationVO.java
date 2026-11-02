package com.weliao.im.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话视图。name/avatar 在单聊时已由服务端填成对方信息，前端可直接渲染。
 */
public record ConversationVO(
        Long id,
        String type,
        String name,
        String avatar,
        Long ownerId,
        int memberCount,
        long unread,
        long lastReadSeq,
        long maxSeq,
        MessageVO lastMessage,
        UserVO peer,
        List<MemberVO> members,
        LocalDateTime updatedAt
) {
}
