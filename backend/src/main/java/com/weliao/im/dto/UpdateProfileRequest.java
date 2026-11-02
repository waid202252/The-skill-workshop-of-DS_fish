package com.weliao.im.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新个人资料，字段为空表示不修改。
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 20, message = "昵称长度需为 1-20 位") String nickname,
        @Size(max = 255, message = "头像地址过长") String avatar,
        @Size(max = 128, message = "签名最多 128 个字符") String signature
) {
}
