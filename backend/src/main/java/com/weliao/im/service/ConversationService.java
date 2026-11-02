package com.weliao.im.service;

import com.weliao.im.common.BizException;
import com.weliao.im.common.Tx;
import com.weliao.im.entity.*;
import com.weliao.im.repository.ConversationMemberRepository;
import com.weliao.im.repository.ConversationRepository;
import com.weliao.im.repository.MessageRepository;
import com.weliao.im.repository.UserRepository;
import com.weliao.im.vo.*;
import com.weliao.im.ws.WsEnvelope;
import com.weliao.im.ws.WsHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话（单聊 / 群聊）的创建、成员管理与视图组装。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final VoMapper voMapper;
    private final WsHub hub;
    /** 用于「持锁建会话」：提交必须发生在锁内，见 openSingle */
    private final TransactionTemplate transactionTemplate;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMemberRepository memberRepository,
                               MessageRepository messageRepository,
                               UserRepository userRepository,
                               MessageService messageService,
                               VoMapper voMapper,
                               WsHub hub,
                               PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.voMapper = voMapper;
        this.hub = hub;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- 查询

    public List<ConversationVO> list(Long userId) {
        return conversationRepository.findAllByMember(userId).stream()
                .map(c -> toVO(c, userId))
                .toList();
    }

    public ConversationVO get(Long userId, Long conversationId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BizException.notFound("会话不存在"));
        requireMember(conversationId, userId);
        return toVO(c, userId);
    }

    // ---------------------------------------------------------------- 创建

    /**
     * 打开与某人的单聊：已存在则直接返回，不存在则创建（幂等）。
     */
    /**
     * 打开与某人的单聊：已存在则直接返回，不存在则创建（幂等）。
     * <p>
     * 并发保护做了两层（对应 V4 迁移）：
     * <ol>
     *   <li>同键互斥锁：把"查不到就建"这段串行化，正常路径不会产生重复</li>
     *   <li>事务模板：<b>提交必须在锁内完成</b>。如果只是给方法加 @Transactional，
     *       锁会在方法返回时释放、而事务要到代理退出才提交，中间的窗口足以让另一个线程
     *       查不到刚插入的行，从而建出第二个会话</li>
     *   <li>single_key 唯一索引：跨进程/多实例时的最后一道保险</li>
     * </ol>
     */
    public ConversationVO openSingle(Long userId, Long peerId) {
        if (Objects.equals(userId, peerId)) {
            throw BizException.badRequest("不能和自己聊天");
        }
        userRepository.findById(peerId).orElseThrow(() -> BizException.notFound("用户不存在"));

        String key = singleKeyOf(userId, peerId);
        synchronized (lockFor(key)) {
            List<Long> existing = conversationRepository.findIdsBetweenUsers(
                    ConversationType.SINGLE, userId, peerId);
            if (!existing.isEmpty()) {
                Conversation c = conversationRepository.findById(existing.get(0)).orElse(null);
                if (c != null) {
                    return toVO(c, userId);
                }
            }
            ConversationVO vo = transactionTemplate.execute(status -> createSingle(userId, peerId, key));
            if (vo == null) {
                throw new IllegalStateException("创建单聊会话失败");
            }
            return vo;
        }
    }

    /** 单聊去重键：小 id 在前，保证 (a,b) 与 (b,a) 得到同一个 key */
    private static String singleKeyOf(Long a, Long b) {
        long min = Math.min(a, b);
        long max = Math.max(a, b);
        return min + ":" + max;
    }

    /** 固定数量的锁槽位，避免为每个用户对创建锁对象导致内存无限增长 */
    private static final Object[] PAIR_LOCKS = new Object[64];

    static {
        for (int i = 0; i < PAIR_LOCKS.length; i++) {
            PAIR_LOCKS[i] = new Object();
        }
    }

    private static Object lockFor(String key) {
        return PAIR_LOCKS[(key.hashCode() & 0x7fffffff) % PAIR_LOCKS.length];
    }

    /** 在事务内真正建会话（调用方必须已经持有同键锁） */
    private ConversationVO createSingle(Long userId, Long peerId, String key) {
        LocalDateTime now = LocalDateTime.now();
        Conversation c = new Conversation();
        c.setType(ConversationType.SINGLE);
        c.setMaxSeq(0L);
        c.setSingleKey(key);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        // saveAndFlush：立刻把 INSERT 发出去，让唯一索引尽早生效
        conversationRepository.saveAndFlush(c);

        addMember(c.getId(), userId, MemberRole.MEMBER);
        addMember(c.getId(), peerId, MemberRole.MEMBER);
        return toVO(c, userId);
    }

    @Transactional
    public ConversationVO createGroup(Long userId, String name, List<Long> memberIds) {
        LinkedHashSet<Long> targets = new LinkedHashSet<>(memberIds);
        targets.remove(userId);
        if (targets.isEmpty()) {
            throw BizException.badRequest("请至少选择一位成员");
        }
        List<User> users = userRepository.findAllById(targets);
        if (users.isEmpty()) {
            throw BizException.badRequest("选择的成员不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        Conversation c = new Conversation();
        c.setType(ConversationType.GROUP);
        c.setName(name.trim());
        c.setOwnerId(userId);
        c.setMaxSeq(0L);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        conversationRepository.save(c);

        addMember(c.getId(), userId, MemberRole.OWNER);
        for (User u : users) {
            addMember(c.getId(), u.getId(), MemberRole.MEMBER);
        }

        String ownerName = userRepository.findById(userId).map(User::getNickname).orElse("有人");
        messageService.appendSystem(c.getId(), ownerName + " 创建了群聊");
        broadcastUpdate(allMemberIds(c.getId()), c.getId());
        return toVO(c, userId);
    }

    // ---------------------------------------------------------------- 成员管理

    @Transactional
    public ConversationVO addMembers(Long userId, Long conversationId, List<Long> userIds) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BizException.notFound("会话不存在"));
        ConversationMember mine = requireMember(conversationId, userId);
        if (c.getType() != ConversationType.GROUP) {
            throw BizException.badRequest("单聊会话不能加人");
        }
        if (mine.getRole() == MemberRole.MEMBER) {
            throw BizException.forbidden("只有群主或管理员可以邀请成员");
        }

        Set<Long> existing = memberRepository.findByConversationId(conversationId).stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());
        List<User> toAdd = userRepository.findAllById(userIds).stream()
                .filter(u -> !existing.contains(u.getId()))
                .toList();
        if (toAdd.isEmpty()) {
            return toVO(c, userId);
        }
        for (User u : toAdd) {
            addMember(conversationId, u.getId(), MemberRole.MEMBER);
        }
        String names = toAdd.stream().map(User::getNickname).collect(Collectors.joining("、"));
        messageService.appendSystem(conversationId, names + " 加入了群聊");
        broadcastUpdate(allMemberIds(conversationId), conversationId);
        return toVO(c, userId);
    }

    @Transactional
    public void removeMember(Long userId, Long conversationId, Long targetId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BizException.notFound("会话不存在"));
        ConversationMember mine = requireMember(conversationId, userId);
        ConversationMember target = memberRepository.findByConversationIdAndUserId(conversationId, targetId)
                .orElseThrow(() -> BizException.notFound("该成员不在会话中"));

        boolean selfLeave = Objects.equals(userId, targetId);
        if (!selfLeave && mine.getRole() == MemberRole.MEMBER) {
            throw BizException.forbidden("只有群主或管理员可以移除成员");
        }

        String targetName = userRepository.findById(targetId).map(User::getNickname).orElse("某成员");
        Set<Long> before = allMemberIds(conversationId);

        memberRepository.deleteByConversationIdAndUserId(conversationId, targetId);

        List<ConversationMember> remaining = memberRepository.findByConversationId(conversationId);
        if (remaining.isEmpty()) {
            conversationRepository.delete(c);
            return;
        }
        // 群主退出：把群主转给最早加入的成员
        if (target.getRole() == MemberRole.OWNER) {
            ConversationMember next = remaining.get(0);
            next.setRole(MemberRole.OWNER);
            memberRepository.save(next);
            c.setOwnerId(next.getUserId());
            conversationRepository.save(c);
        }

        messageService.appendSystem(conversationId,
                selfLeave ? targetName + " 退出了群聊" : targetName + " 被移出了群聊");

        Set<Long> after = allMemberIds(conversationId);
        after.add(targetId); // 被移除的人也要收到刷新通知
        broadcastUpdate(after, conversationId);
        log.debug("成员移除 conversation={} target={} 剩余={}", conversationId, targetId, remaining.size());
    }

    // ---------------------------------------------------------------- 已读

    @Transactional
    public void markRead(Long userId, Long conversationId, long seq) {
        ConversationMember mine = requireMember(conversationId, userId);
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BizException.notFound("会话不存在"));
        long clamped = Math.min(Math.max(seq, 0), c.getMaxSeq());
        if (clamped <= mine.getLastReadSeq()) {
            return;
        }
        mine.setLastReadSeq(clamped);
        memberRepository.save(mine);

        List<Long> others = memberRepository.findByConversationId(conversationId).stream()
                .map(ConversationMember::getUserId)
                .filter(id -> !id.equals(userId))
                .toList();
        Tx.afterCommit(() -> hub.sendToAll(others, WsEnvelope.of("read",
                Map.of("conversationId", conversationId, "userId", userId, "seq", clamped))));
    }

    // ---------------------------------------------------------------- 内部

    private ConversationMember requireMember(Long conversationId, Long userId) {
        return memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> BizException.forbidden("你不在该会话中"));
    }

    private void addMember(Long conversationId, Long userId, MemberRole role) {
        ConversationMember m = new ConversationMember();
        m.setConversationId(conversationId);
        m.setUserId(userId);
        m.setRole(role);
        m.setLastReadSeq(0L);
        m.setJoinedAt(LocalDateTime.now());
        memberRepository.save(m);
    }

    private Set<Long> allMemberIds(Long conversationId) {
        return memberRepository.findByConversationId(conversationId).stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 通知相关客户端「这个会话的成员/信息变了，请刷新」。提交后推送。 */
    private void broadcastUpdate(Collection<Long> userIds, Long conversationId) {
        Tx.afterCommit(() -> hub.sendToAll(userIds, WsEnvelope.of("conversation_update",
                Map.of("conversationId", conversationId))));
    }

    private ConversationVO toVO(Conversation c, Long viewerId) {
        List<ConversationMember> members = memberRepository.findByConversationId(c.getId());
        ConversationMember mine = members.stream()
                .filter(m -> m.getUserId().equals(viewerId))
                .findFirst()
                .orElse(null);
        long lastReadSeq = mine != null ? mine.getLastReadSeq() : 0L;
        long unread = mine == null ? 0L
                : messageRepository.countByConversationIdAndSeqGreaterThanAndSenderIdNot(
                c.getId(), lastReadSeq, viewerId);

        MessageVO lastMessage = null;
        if (c.getLastMessageId() != null) {
            MessageEntity lm = messageRepository.findById(c.getLastMessageId()).orElse(null);
            if (lm != null) {
                User sender = lm.getSenderId() == null || lm.getSenderId() <= 0
                        ? null
                        : userRepository.findById(lm.getSenderId()).orElse(null);
                lastMessage = voMapper.toMessageVO(lm, sender);
            }
        }

        UserVO peer = null;
        String name = c.getName();
        String avatar = c.getAvatar();
        if (c.getType() == ConversationType.SINGLE) {
            Long peerId = members.stream()
                    .map(ConversationMember::getUserId)
                    .filter(id -> !id.equals(viewerId))
                    .findFirst()
                    .orElse(null);
            if (peerId != null) {
                User pu = userRepository.findById(peerId).orElse(null);
                if (pu != null) {
                    peer = voMapper.toUserVO(pu);
                    name = pu.getNickname();
                    avatar = pu.getAvatar();
                }
            }
            if (!StringUtils.hasText(name)) {
                name = "会话";
            }
        }

        List<MemberVO> memberVOs = null;
        if (c.getType() == ConversationType.GROUP) {
            Set<Long> ids = members.stream().map(ConversationMember::getUserId).collect(Collectors.toSet());
            Map<Long, User> users = new HashMap<>();
            if (!ids.isEmpty()) {
                userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));
            }
            memberVOs = members.stream()
                    .map(m -> voMapper.toMemberVO(m, users.get(m.getUserId())))
                    .toList();
        }

        return new ConversationVO(c.getId(), c.getType().name(), name, avatar, c.getOwnerId(),
                members.size(), unread, lastReadSeq, c.getMaxSeq(), lastMessage, peer, memberVOs, c.getUpdatedAt());
    }
}
