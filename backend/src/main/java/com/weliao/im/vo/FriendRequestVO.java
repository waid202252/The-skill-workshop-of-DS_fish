package com.weliao.im.vo;

import java.time.LocalDateTime;

/**
 * 好友申请视图。
 */
public record FriendRequestVO(
        Long id,
        Long fromUserId,
        String fromNickname,
        String fromAvatar,
        Long toUserId,
        String toNickname,
        String message,
        String status,
        LocalDateTime createdAt
) {
}
