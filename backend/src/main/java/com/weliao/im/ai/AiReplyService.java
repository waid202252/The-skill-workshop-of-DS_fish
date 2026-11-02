package com.weliao.im.ai;

import com.weliao.im.config.AppProperties;
import com.weliao.im.config.AsyncConfig;
import com.weliao.im.dto.SendMessageRequest;
import com.weliao.im.entity.UserType;
import com.weliao.im.event.MessageCreatedEvent;
import com.weliao.im.repository.ConversationMemberRepository;
import com.weliao.im.service.MessageService;
import com.weliao.im.vo.MessagePageVO;
import com.weliao.im.vo.MessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 回复。监听「消息已提交」事件，异步生成回复并作为 AI 账号发出。
 */
@Service
public class AiReplyService {

    private static final Logger log = LoggerFactory.getLogger(AiReplyService.class);

    private final AppProperties props;
    private final AiProvider provider;
    private final AiConversationService aiConversationService;
    private final ConversationMemberRepository memberRepository;
    private final MessageService messageService;
    private final PersonaPrompts personaPrompts;

    public AiReplyService(AppProperties props, AiProvider provider,
                          AiConversationService aiConversationService,
                          ConversationMemberRepository memberRepository,
                          MessageService messageService,
                          PersonaPrompts personaPrompts) {
        this.props = props;
        this.provider = provider;
        this.aiConversationService = aiConversationService;
        this.memberRepository = memberRepository;
        this.messageService = messageService;
        this.personaPrompts = personaPrompts;
    }

    @Async(AsyncConfig.AI_EXECUTOR)
    @EventListener
    public void onMessageCreated(MessageCreatedEvent event) {
        try {
            handle(event);
        } catch (Exception e) {
            log.warn("AI 回复失败 conversationId={}: {}", event.conversationId(), e.getMessage());
        }
    }

    private void handle(MessageCreatedEvent event) {
        AppProperties.Ai ai = props.getAi();
        if (!ai.isEnabled()) {
            return;
        }
        // 只回应用户的文本消息；AI 自己的消息、系统消息、图片文件都不触发
        if (event.senderType() == UserType.BOT || !"TEXT".equals(event.type())) {
            return;
        }
        Long botId = aiConversationService.botId();
        if (botId == null || botId.equals(event.senderId())) {
            return;
        }
        // 只有 AI 在该会话里才回复（群聊里也能用）
        if (!memberRepository.existsByConversationIdAndUserId(event.conversationId(), botId)) {
            return;
        }

        List<AiTurn> history = buildHistory(event.conversationId(), botId, ai);
        String reply;
        try {
            reply = provider.reply(history);
        } catch (AiException e) {
            reply = "（我这边调用模型失败了：" + e.getMessage() + "）";
            log.warn("模型调用失败 provider={}: {}", provider.name(), e.getMessage());
        }

        messageService.send(botId, event.conversationId(),
                new SendMessageRequest(null, "TEXT", reply, null, null));
        log.debug("AI 已回复 conversationId={} provider={}", event.conversationId(), provider.name());
    }

    /** 取最近 N 条消息拼成上下文，图片/文件用占位符代替（不把二进制塞给模型）。 */
    private List<AiTurn> buildHistory(Long conversationId, Long botId, AppProperties.Ai ai) {
        List<AiTurn> turns = new ArrayList<>();
        turns.add(AiTurn.system(personaPrompts.chatPrompt(ai.getSystemPrompt())));

        MessagePageVO page = messageService.history(botId, conversationId, null, null, ai.getMaxHistoryMessages());
        for (MessageVO message : page.messages()) {
            String text = switch (message.type()) {
                case "TEXT" -> message.content();
                case "IMAGE" -> "[发送了一张图片]";
                case "FILE" -> "[发送了文件：" + (message.fileName() == null ? "未命名" : message.fileName()) + "]";
                default -> null;
            };
            if (text == null || text.isBlank()) {
                continue;
            }
            turns.add(message.senderId().equals(botId) ? AiTurn.assistant(text) : AiTurn.user(text));
        }
        return turns;
    }
}
