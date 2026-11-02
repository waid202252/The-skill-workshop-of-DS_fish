package com.weliao.im.vo;

/**
 * 群成员视图。
 */
public record MemberVO(
        Long userId,
        String nickname,
        String avatar,
        String role,
        boolean online
) {
}
