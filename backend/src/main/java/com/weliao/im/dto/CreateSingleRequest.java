package com.weliao.im.dto;

import jakarta.validation.constraints.NotNull;

public record CreateSingleRequest(
        @NotNull(message = "目标用户不能为空") Long userId
) {
}
