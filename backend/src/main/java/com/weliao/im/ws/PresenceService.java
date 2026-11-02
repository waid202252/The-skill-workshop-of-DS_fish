package com.weliao.im.ws;

import com.weliao.im.repository.ConversationMemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 在线状态广播：只推给和该用户有共同会话的人，避免全站广播。
 */
@Service
public class PresenceService {

    private final WsHub hub;
    private final ConversationMemberRepository memberRepository;

    public PresenceService(WsHub hub, ConversationMemberRepository memberRepository) {
        this.hub = hub;
        this.memberRepository = memberRepository;
    }

    public void broadcast(Long userId, boolean online) {
        List<Long> peers;
        try {
            peers = memberRepository.findPeerUserIds(userId);
        } catch (Exception e) {
            // 在线状态是尽力而为的能力，查询失败不能影响主流程
            return;
        }
        if (peers.isEmpty()) {
            return;
        }
        hub.sendToAll(peers, WsEnvelope.of("presence", Map.of("userId", userId, "online", online)));
    }
}
