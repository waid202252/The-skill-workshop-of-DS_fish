package com.weliao.im.entity;

/**
 * 消息类型。
 */
public enum MessageType {
    /** 纯文本 */
    TEXT,
    /** 图片，content 存图片 URL */
    IMAGE,
    /** 文件，content 存文件 URL */
    FILE,
    /** 系统提示，如「XX 加入了群聊」 */
    SYSTEM
}
