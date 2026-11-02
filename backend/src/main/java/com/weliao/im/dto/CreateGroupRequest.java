package com.weliao.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(
        @NotBlank(message = "群名称不能为空")
        @Size(max = 64, message = "群名称最多 64 个字符")
        String name,

        @NotEmpty(message = "请至少选择一位成员")
        List<Long> memberIds
) {
}
