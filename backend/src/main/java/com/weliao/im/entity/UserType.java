package com.weliao.im.entity;

/**
 * 账号类型。BOT 是 AI 伙伴，在会话系统里和真人完全对等，
 * 这样消息、历史、未读、多端同步全都不用特殊处理。
 */
public enum UserType {
    HUMAN,
    BOT
}
