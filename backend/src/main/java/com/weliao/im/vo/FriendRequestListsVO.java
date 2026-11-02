package com.weliao.im.vo;

import java.util.List;

/**
 * 好友申请列表：收到的 + 我发出的。
 */
public record FriendRequestListsVO(List<FriendRequestVO> incoming, List<FriendRequestVO> outgoing) {
}
