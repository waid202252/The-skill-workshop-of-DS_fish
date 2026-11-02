package com.weliao.im.vo;

/**
 * 用户对外视图，不含密码等敏感字段。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String avatar,
        String signature,
        boolean online,
        String type
) {
}
