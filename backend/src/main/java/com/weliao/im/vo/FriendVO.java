package com.weliao.im.vo;

/**
 * 好友视图。conversationId 是与该好友的单聊会话，可能尚未创建。
 */
public record FriendVO(
        Long userId,
        String username,
        String nickname,
        String avatar,
        String signature,
        boolean online,
        Long conversationId
) {
}
