package com.weliao.im.controller;

import com.weliao.im.ai.AiConversationService;
import com.weliao.im.ai.AiProvider;
import com.weliao.im.common.R;
import com.weliao.im.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 伙伴入口。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiConversationService aiConversationService;
    private final AiProvider aiProvider;

    public AiController(AiConversationService aiConversationService, AiProvider aiProvider) {
        this.aiConversationService = aiConversationService;
        this.aiProvider = aiProvider;
    }

    /** 打开与 AI 的会话（不存在则创建），返回会话 id。 */
    @PostMapping("/conversation")
    public R openConversation() {
        Long conversationId = aiConversationService.openBotConversation(CurrentUser.id());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("botId", aiConversationService.botId());
        return R.ok(data);
    }

    /** 当前 AI 链路状态，前端可据此提示「模拟模式 / 已接入模型」。 */
    @GetMapping("/status")
    public R status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("botId", aiConversationService.botId());
        data.put("provider", aiProvider.name());
        data.put("mock", "mock".equals(aiProvider.name()));
        return R.ok(data);
    }
}
