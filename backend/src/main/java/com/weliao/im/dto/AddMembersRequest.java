package com.weliao.im.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddMembersRequest(
        @NotEmpty(message = "请至少选择一位成员") List<Long> userIds
) {
}
