package com.weliao.im.service;

import com.weliao.im.entity.ConversationMember;
import com.weliao.im.entity.MessageEntity;
import com.weliao.im.entity.User;
import com.weliao.im.repository.UserRepository;
import com.weliao.im.vo.MemberVO;
import com.weliao.im.vo.MessageVO;
import com.weliao.im.vo.UserVO;
import com.weliao.im.ws.WsHub;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实体 -> VO 的转换。集中处理在线状态与发送者信息，避免各处重复拼装。
 */
@Component
public class VoMapper {

    private final WsHub hub;
    private final UserRepository userRepository;

    public VoMapper(WsHub hub, UserRepository userRepository) {
        this.hub = hub;
        this.userRepository = userRepository;
    }

    public UserVO toUserVO(User u) {
        if (u == null) {
            return null;
        }
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getAvatar(),
                u.getSignature(), isOnline(u),
                u.getType() == null ? "HUMAN" : u.getType().name());
    }

    public List<UserVO> toUserVOs(Collection<User> users) {
        return users.stream().map(this::toUserVO).toList();
    }

    public MemberVO toMemberVO(ConversationMember m, User u) {
        String nickname = u != null ? u.getNickname() : "已注销用户";
        String avatar = u != null ? u.getAvatar() : null;
        return new MemberVO(m.getUserId(), nickname, avatar, m.getRole().name(),
                u != null ? isOnline(u) : hub.isOnline(m.getUserId()));
    }

    /**
     * 展示层在线判定。
     * <p>
     * 真人看连接事实（PresenceStore），BOT 视为始终在线——它没有长连接，
     * 但用户随时能收到回复，挂着「离线」只会让人以为是坏了。
     */
    public boolean isOnline(User u) {
        return u.isAlwaysOnline() || hub.isOnline(u.getId());
    }

    public MessageVO toMessageVO(MessageEntity m, User sender) {
        String nickname = sender != null ? sender.getNickname() : "系统";
        String avatar = sender != null ? sender.getAvatar() : null;
        return new MessageVO(
                m.getId(), m.getConversationId(), m.getSeq(), m.getSenderId(),
                nickname, avatar, m.getType().name(),
                // 撤回后不把原文下发给任何客户端，前端只看得到占位
                m.isRecalled() ? "" : m.getContent(),
                m.getFileName(), m.getFileSize(), m.getCreatedAt(), m.getClientMsgId(),
                m.isRecalled());
    }

    /**
     * 批量转换消息，一次查出所有发送者，避免 N+1。
     */
    public List<MessageVO> toMessageVOs(List<MessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Set<Long> senderIds = messages.stream()
                .map(MessageEntity::getSenderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, User> senders = new HashMap<>();
        if (!senderIds.isEmpty()) {
            for (User u : userRepository.findAllById(senderIds)) {
                senders.put(u.getId(), u);
            }
        }
        List<MessageVO> result = new ArrayList<>(messages.size());
        for (MessageEntity m : messages) {
            result.add(toMessageVO(m, senders.get(m.getSenderId())));
        }
        return result;
    }
}
