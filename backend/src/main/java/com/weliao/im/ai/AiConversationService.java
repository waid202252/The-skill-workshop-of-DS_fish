package com.weliao.im.ai;

import com.weliao.im.common.BizException;
import com.weliao.im.config.AppProperties;
import com.weliao.im.dto.SendMessageRequest;
import com.weliao.im.entity.User;
import com.weliao.im.repository.UserRepository;
import com.weliao.im.service.ConversationService;
import com.weliao.im.service.MessageService;
import com.weliao.im.vo.ConversationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 伙伴会话的开通：把「和 AI 聊天」做成一个普通单聊会话。
 */
@Service
public class AiConversationService {

    private static final Logger log = LoggerFactory.getLogger(AiConversationService.class);

    private static final String WELCOME = "你好，我是大肥鱼 🐋\n"
            + "我不是真人，是这个应用里内置的 AI 伙伴。\n"
            + "直接跟我说话就行——你发的消息和发给好友一样，会落库、会有历史、换设备也能看到。";

    private final AppProperties props;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final MessageService messageService;

    public AiConversationService(AppProperties props, UserRepository userRepository,
                                 ConversationService conversationService, MessageService messageService) {
        this.props = props;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /** AI 账号 id，未启用或未初始化时返回 null。 */
    public Long botId() {
        if (!props.getAi().isEnabled()) {
            return null;
        }
        return userRepository.findByUsername(props.getAi().getBotUsername())
                .map(User::getId)
                .orElse(null);
    }

    /**
     * 打开（必要时创建）与 AI 的会话；首次进入时发一条欢迎语。
     *
     * @return 会话 id
     */
    @Transactional
    public Long openBotConversation(Long userId) {
        Long botId = botId();
        if (botId == null) {
            throw BizException.notFound("AI 伙伴未启用");
        }
        if (botId.equals(userId)) {
            throw BizException.badRequest("不能和自己聊天");
        }
        ConversationVO conv = conversationService.openSingle(userId, botId);
        if (conv.maxSeq() == 0L) {
            messageService.send(botId, conv.id(), new SendMessageRequest(null, "TEXT", WELCOME, null, null));
            log.debug("已向用户 {} 的 AI 会话发送欢迎语", userId);
        }
        return conv.id();
    }
}
