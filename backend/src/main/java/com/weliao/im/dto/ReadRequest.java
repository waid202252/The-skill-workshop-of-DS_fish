package com.weliao.im.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReadRequest(
        @NotNull(message = "已读位置不能为空")
        @Min(value = 0, message = "已读位置不能为负")
        Long seq
) {
}
