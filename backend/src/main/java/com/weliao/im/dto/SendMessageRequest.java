package com.weliao.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        String clientMsgId,
        @NotBlank(message = "消息类型不能为空") String type,
        @NotBlank(message = "消息内容不能为空") String content,
        @Size(max = 255, message = "文件名过长") String fileName,
        Long fileSize
) {
}
