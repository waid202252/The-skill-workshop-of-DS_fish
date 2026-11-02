package com.weliao.im.service;

import com.weliao.im.common.BizException;
import com.weliao.im.dto.SendMessageRequest;
import com.weliao.im.entity.Conversation;
import com.weliao.im.entity.ConversationMember;
import com.weliao.im.entity.MessageEntity;
import com.weliao.im.entity.MessageType;
import com.weliao.im.entity.User;
import com.weliao.im.entity.UserType;
import com.weliao.im.event.MessageCreatedEvent;
import com.weliao.im.repository.ConversationMemberRepository;
import com.weliao.im.repository.ConversationRepository;
import com.weliao.im.repository.MessageRepository;
import com.weliao.im.repository.UserRepository;
import com.weliao.im.vo.MessagePageVO;
import com.weliao.im.vo.MessageVO;
import com.weliao.im.ws.WsEnvelope;
import com.weliao.im.ws.WsHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 消息读写与实时分发。
 * <p>
 * 关键点：发消息时对会话行加悲观写锁，使 (conversationId, seq) 严格递增不重号，
 * 从而支持「seq 游标分页」与「未读数 = 未读条数」。
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private static final int MAX_TEXT_LENGTH = 5000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final VoMapper voMapper;
    private final WsHub hub;
    private final ApplicationEventPublisher eventPublisher;
    private final com.weliao.im.config.AppProperties props;

    public MessageService(ConversationRepository conversationRepository,
                          ConversationMemberRepository memberRepository,
                          MessageRepository messageRepository,
                          UserRepository userRepository,
                          VoMapper voMapper,
                          WsHub hub,
                          ApplicationEventPublisher eventPublisher,
                          com.weliao.im.config.AppProperties props) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.voMapper = voMapper;
        this.hub = hub;
        this.eventPublisher = eventPublisher;
        this.props = props;
    }

    /**
     * 发送消息。事务提交后再推送给所有成员，保证客户端收到推送时能立刻查到历史。
     */
    @Transactional
    public MessageVO send(Long senderId, Long conversationId, SendMessageRequest req) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw BizException.forbidden("你不在该会话中");
        }
        MessageType type = parseType(req.type());
        String content = normalizeContent(type, req.content());

        MessageEntity entity = persist(senderId, conversationId, type, content,
                req.fileName(), req.fileSize(), req.clientMsgId());

        User sender = userRepository.findById(senderId).orElse(null);
        MessageVO vo = voMapper.toMessageVO(entity, sender);
        UserType senderType = (sender != null && sender.getType() != null) ? sender.getType() : UserType.HUMAN;

        // 先推送、再发事件。事件监听方（AI 回复等）拿到的是已提交的数据。
        afterCommit(() -> {
            fanout(vo);
            eventPublisher.publishEvent(new MessageCreatedEvent(
                    vo.id(), vo.conversationId(), vo.senderId(), vo.seq(),
                    vo.type(), vo.content(), senderType));
        });
        return vo;
    }

    /**
     * 追加系统消息（建群、加人、退群等），不校验成员身份，由调用方保证。
     */
    @Transactional
    public void appendSystem(Long conversationId, String text) {
        persist(0L, conversationId, MessageType.SYSTEM, text, null, null, null);
        // 系统消息也推一下，让在线成员立刻看到
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv != null && conv.getLastMessageId() != null) {
            messageRepository.findById(conv.getLastMessageId()).ifPresent(m -> {
                MessageVO vo = voMapper.toMessageVO(m, null);
                afterCommit(() -> fanout(vo));
            });
        }
    }

    /** 落库并推进会话水位，调用方必须在事务内。 */
    private MessageEntity persist(Long senderId, Long conversationId, MessageType type,
                                  String content, String fileName, Long fileSize, String clientMsgId) {
        Conversation conv = conversationRepository.findWithLockById(conversationId)
                .orElseThrow(() -> BizException.notFound("会话不存在"));

        long seq = conv.getMaxSeq() + 1;
        LocalDateTime now = LocalDateTime.now();

        MessageEntity m = new MessageEntity();
        m.setConversationId(conversationId);
        m.setSeq(seq);
        m.setSenderId(senderId);
        m.setType(type);
        m.setContent(content);
        m.setFileName(fileName);
        m.setFileSize(fileSize);
        m.setClientMsgId(clientMsgId);
        m.setCreatedAt(now);
        messageRepository.save(m);

        conv.setMaxSeq(seq);
        conv.setLastMessageId(m.getId());
        conv.setUpdatedAt(now);
        conversationRepository.save(conv);
        return m;
    }

    /**
     * 历史消息，游标分页。两个方向二选一：
     * <ul>
     *   <li>{@code beforeSeq}：往前翻更早的消息（上拉加载历史），返回按 seq 升序的一页</li>
     *   <li>{@code afterSeq}：往后增量补齐（断线重连追上积压消息），返回按 seq 升序的一页</li>
     * </ul>
     *
     * @param beforeSeq 取 seq 小于该值的消息；不传则取最新的一页
     * @param afterSeq  取 seq 大于该值的消息；传入时优先于 beforeSeq
     * @return 始终按 seq 升序排列
     */
    public MessagePageVO history(Long userId, Long conversationId, Long beforeSeq, Long afterSeq, int limit) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw BizException.forbidden("你不在该会话中");
        }
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BizException.notFound("会话不存在"));

        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));

        // 增量补齐：往后取，多拿一条判断是否还有更新的
        if (afterSeq != null) {
            List<MessageEntity> asc = messageRepository
                    .findByConversationIdAndSeqGreaterThanOrderBySeqAsc(conversationId, afterSeq, PageRequest.of(0, size + 1));
            boolean hasMore = asc.size() > size;
            if (hasMore) {
                asc = new ArrayList<>(asc.subList(0, size));
            }
            return new MessagePageVO(voMapper.toMessageVOs(asc), hasMore);
        }

        long cursor = beforeSeq != null ? beforeSeq : conv.getMaxSeq() + 1;

        // 往前翻：倒序多取一条用来判断是否还有更早的消息
        List<MessageEntity> desc = messageRepository
                .findByConversationIdAndSeqLessThanOrderBySeqDesc(conversationId, cursor, PageRequest.of(0, size + 1));
        boolean hasMore = desc.size() > size;
        if (hasMore) {
            desc = new ArrayList<>(desc.subList(0, size));
        }
        Collections.reverse(desc);
        return new MessagePageVO(voMapper.toMessageVOs(desc), hasMore);
    }

    /**
     * 撤回消息。
     * <p>
     * 不物理删除：保留行、保留 seq，只清空内容并打标记。这样分页游标不会出现空洞，
     * 未读数也不受影响（你确实收到过一条消息，只是内容不可见了）。
     */
    @Transactional
    public MessageVO recall(Long userId, Long conversationId, Long messageId) {
        MessageEntity m = messageRepository.findById(messageId)
                .orElseThrow(() -> BizException.notFound("消息不存在"));
        if (!m.getConversationId().equals(conversationId)) {
            throw BizException.badRequest("消息不属于该会话");
        }
        if (!m.getSenderId().equals(userId)) {
            throw BizException.forbidden("只能撤回自己发送的消息");
        }
        if (m.getType() == MessageType.SYSTEM) {
            throw BizException.badRequest("系统消息不能撤回");
        }
        User sender = userRepository.findById(userId).orElse(null);

        // 已撤回视为成功（幂等），重复点击不报错
        if (m.isRecalled()) {
            return voMapper.toMessageVO(m, sender);
        }

        int window = Math.max(0, props.getMessage().getRecallWindowMinutes());
        if (window > 0) {
            long elapsedSeconds = java.time.Duration.between(m.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (elapsedSeconds > window * 60L) {
                throw BizException.conflict("超过 " + window + " 分钟，无法撤回");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        m.setRecalled(true);
        m.setRecalledAt(now);
        m.setContent("");
        // 文件名也一并清掉，否则「已撤回」旁边还挂着原始文件名
        m.setFileName(null);
        m.setFileSize(null);
        messageRepository.save(m);

        MessageVO vo = voMapper.toMessageVO(m, sender);
        afterCommit(() -> fanoutRecall(vo));
        return vo;
    }

    private void fanoutRecall(MessageVO vo) {
        try {
            List<Long> memberIds = memberRepository.findByConversationId(vo.conversationId())
                    .stream()
                    .map(ConversationMember::getUserId)
                    .toList();
            hub.sendToAll(memberIds, WsEnvelope.of("message_recalled", Map.of("message", vo)));
        } catch (Exception e) {
            log.warn("撤回推送失败 conversationId={}: {}", vo.conversationId(), e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 内部

    private MessageType parseType(String raw) {
        try {
            return MessageType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw BizException.badRequest("不支持的消息类型");
        }
    }

    private String normalizeContent(MessageType type, String content) {
        if (content == null || content.isBlank()) {
            throw BizException.badRequest("消息内容不能为空");
        }
        String c = content;
        if (type == MessageType.TEXT) {
            c = c.trim();
            if (c.length() > MAX_TEXT_LENGTH) {
                throw BizException.badRequest("消息太长，最多 " + MAX_TEXT_LENGTH + " 个字符");
            }
        }
        return c;
    }

    private void fanout(MessageVO vo) {
        try {
            List<Long> memberIds = memberRepository.findByConversationId(vo.conversationId())
                    .stream()
                    .map(ConversationMember::getUserId)
                    .toList();
            hub.sendToAll(memberIds, WsEnvelope.of("message", Map.of("message", vo)));
        } catch (Exception e) {
            // 推送失败不应影响已经落库的消息，客户端重连后会拉到
            log.warn("消息推送失败 conversationId={}: {}", vo.conversationId(), e.getMessage());
        }
    }

    /** 事务提交后再执行，避免「推送比数据库早」导致客户端拉不到消息。 */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
