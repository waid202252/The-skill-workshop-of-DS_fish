package com.weliao.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 * <p>
 * 必须提供旧密码：这是账号被他人短期占用的最后一道防线（例如在公共电脑上忘了退出）。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码") String oldPassword,
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 32, message = "新密码长度需为 6-32 位") String newPassword) {
}
