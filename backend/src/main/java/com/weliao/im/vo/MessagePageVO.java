package com.weliao.im.vo;

import java.util.List;

/**
 * 历史消息分页结果，messages 按 seq 升序。
 */
public record MessagePageVO(List<MessageVO> messages, boolean hasMore) {
}
