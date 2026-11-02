package com.weliao.im.service;

import com.weliao.im.common.BizException;
import com.weliao.im.dto.FriendRequestCreate;
import com.weliao.im.entity.*;
import com.weliao.im.repository.ConversationRepository;
import com.weliao.im.repository.FriendLinkRepository;
import com.weliao.im.repository.FriendRequestRepository;
import com.weliao.im.repository.UserRepository;
import com.weliao.im.vo.FriendRequestListsVO;
import com.weliao.im.vo.FriendRequestVO;
import com.weliao.im.vo.FriendVO;
import com.weliao.im.ws.WsEnvelope;
import com.weliao.im.ws.WsHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 好友关系与好友申请。
 */
@Service
public class FriendService {

    private final FriendLinkRepository friendLinkRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final WsHub hub;
    private final VoMapper voMapper;

    public FriendService(FriendLinkRepository friendLinkRepository,
                         FriendRequestRepository friendRequestRepository,
                         UserRepository userRepository,
                         ConversationRepository conversationRepository,
                         WsHub hub,
                         VoMapper voMapper) {
        this.friendLinkRepository = friendLinkRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.hub = hub;
        this.voMapper = voMapper;
    }

    // ---------------------------------------------------------------- 好友列表

    public List<FriendVO> listFriends(Long userId) {
        List<FriendLink> links = friendLinkRepository.findByUserId(userId);
        if (links.isEmpty()) {
            return List.of();
        }
        Set<Long> friendIds = new LinkedHashSet<>();
        links.forEach(l -> friendIds.add(l.getFriendId()));
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(friendIds).forEach(u -> users.put(u.getId(), u));

        List<FriendVO> result = new ArrayList<>(links.size());
        for (Long fid : friendIds) {
            User u = users.get(fid);
            if (u == null) {
                continue;
            }
            Long conversationId = conversationRepository
                    .findIdsBetweenUsers(ConversationType.SINGLE, userId, fid)
                    .stream().findFirst().orElse(null);
            result.add(new FriendVO(u.getId(), u.getUsername(), u.getNickname(), u.getAvatar(),
                    u.getSignature(), voMapper.isOnline(u), conversationId));
        }
        result.sort(Comparator.comparing(FriendVO::nickname, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        friendLinkRepository.deleteByUserIdAndFriendId(userId, friendId);
        friendLinkRepository.deleteByUserIdAndFriendId(friendId, userId);
    }

    // ---------------------------------------------------------------- 好友申请

    public FriendRequestListsVO listRequests(Long userId) {
        List<FriendRequest> incoming = friendRequestRepository
                .findByToUserIdAndStatusOrderByCreatedAtDesc(userId, RequestStatus.PENDING);
        List<FriendRequest> outgoing = friendRequestRepository
                .findByFromUserIdOrderByCreatedAtDesc(userId);
        // 只回最近 50 条，避免历史堆积
        if (outgoing.size() > 50) {
            outgoing = outgoing.subList(0, 50);
        }
        return new FriendRequestListsVO(toRequestVOs(incoming), toRequestVOs(outgoing));
    }

    @Transactional
    public FriendRequestVO createRequest(Long fromUserId, FriendRequestCreate req) {
        Long toUserId = req.toUserId();
        if (Objects.equals(fromUserId, toUserId)) {
            throw BizException.badRequest("不能添加自己为好友");
        }
        User target = userRepository.findById(toUserId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        if (friendLinkRepository.existsByUserIdAndFriendId(fromUserId, toUserId)) {
            throw BizException.conflict("你们已经是好友了");
        }
        if (friendRequestRepository.existsByFromUserIdAndToUserIdAndStatus(
                fromUserId, toUserId, RequestStatus.PENDING)) {
            throw BizException.conflict("已发送过申请，请等待对方处理");
        }

        FriendRequest fr = new FriendRequest();
        fr.setFromUserId(fromUserId);
        fr.setToUserId(toUserId);
        fr.setMessage(StringUtils.hasText(req.message()) ? req.message().trim() : null);
        fr.setStatus(RequestStatus.PENDING);
        fr.setCreatedAt(LocalDateTime.now());
        friendRequestRepository.save(fr);

        FriendRequestVO vo = toRequestVO(fr, userRepository.findById(fromUserId).orElse(null), target);
        hub.sendTo(toUserId, WsEnvelope.of("friend_request", vo));
        return vo;
    }

    @Transactional
    public FriendVO acceptRequest(Long userId, Long requestId) {
        FriendRequest fr = friendRequestRepository.findByIdAndToUserId(requestId, userId)
                .orElseThrow(() -> BizException.notFound("好友申请不存在"));
        if (fr.getStatus() != RequestStatus.PENDING) {
            throw BizException.conflict("该申请已处理过");
        }
        fr.setStatus(RequestStatus.ACCEPTED);
        fr.setHandledAt(LocalDateTime.now());
        friendRequestRepository.save(fr);

        linkFriends(fr.getFromUserId(), fr.getToUserId());

        User from = userRepository.findById(fr.getFromUserId()).orElse(null);
        User to = userRepository.findById(fr.getToUserId()).orElse(null);

        // 通知申请方：对方已通过
        if (to != null) {
            FriendVO toVo = new FriendVO(to.getId(), to.getUsername(), to.getNickname(), to.getAvatar(),
                    to.getSignature(), voMapper.isOnline(to),
                    conversationRepository.findIdsBetweenUsers(ConversationType.SINGLE, to.getId(), from != null ? from.getId() : to.getId())
                            .stream().findFirst().orElse(null));
            hub.sendTo(fr.getFromUserId(), WsEnvelope.of("friend_accepted", toVo));
        }

        if (from == null) {
            throw BizException.notFound("该用户已不存在");
        }
        return new FriendVO(from.getId(), from.getUsername(), from.getNickname(), from.getAvatar(),
                from.getSignature(), voMapper.isOnline(from),
                conversationRepository.findIdsBetweenUsers(ConversationType.SINGLE, userId, from.getId())
                        .stream().findFirst().orElse(null));
    }

    @Transactional
    public void rejectRequest(Long userId, Long requestId) {
        FriendRequest fr = friendRequestRepository.findByIdAndToUserId(requestId, userId)
                .orElseThrow(() -> BizException.notFound("好友申请不存在"));
        if (fr.getStatus() != RequestStatus.PENDING) {
            throw BizException.conflict("该申请已处理过");
        }
        fr.setStatus(RequestStatus.REJECTED);
        fr.setHandledAt(LocalDateTime.now());
        friendRequestRepository.save(fr);
    }

    /** 建立双向好友关系，幂等。 */
    void linkFriends(Long a, Long b) {
        if (!friendLinkRepository.existsByUserIdAndFriendId(a, b)) {
            FriendLink l1 = new FriendLink();
            l1.setUserId(a);
            l1.setFriendId(b);
            l1.setCreatedAt(LocalDateTime.now());
            friendLinkRepository.save(l1);
        }
        if (!friendLinkRepository.existsByUserIdAndFriendId(b, a)) {
            FriendLink l2 = new FriendLink();
            l2.setUserId(b);
            l2.setFriendId(a);
            l2.setCreatedAt(LocalDateTime.now());
            friendLinkRepository.save(l2);
        }
    }

    // ---------------------------------------------------------------- 转换

    private List<FriendRequestVO> toRequestVOs(List<FriendRequest> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        list.forEach(r -> {
            ids.add(r.getFromUserId());
            ids.add(r.getToUserId());
        });
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));
        List<FriendRequestVO> out = new ArrayList<>(list.size());
        for (FriendRequest r : list) {
            out.add(toRequestVO(r, users.get(r.getFromUserId()), users.get(r.getToUserId())));
        }
        return out;
    }

    private FriendRequestVO toRequestVO(FriendRequest r, User from, User to) {
        return new FriendRequestVO(
                r.getId(), r.getFromUserId(),
                from != null ? from.getNickname() : "未知用户",
                from != null ? from.getAvatar() : null,
                r.getToUserId(),
                to != null ? to.getNickname() : "未知用户",
                r.getMessage(), r.getStatus().name(), r.getCreatedAt());
    }
}
