package com.weliao.im.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FriendRequestCreate(
        @NotNull(message = "目标用户不能为空") Long toUserId,
        @Size(max = 128, message = "附言最多 128 个字符") String message
) {
}
