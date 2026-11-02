package com.weliao.im.ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 本地模拟实现：未配置真实模型时使用，保证「克隆下来就能体验」。
 * <p>
 * 这里刻意做成规则式而不是假装成 AI：它会明确告诉用户当前是模拟模式、
 * 以及怎么接入真实模型。诚实比"看起来像 AI"重要——面试演示时被问「这是真的模型吗」
 * 能直接指着这段代码解释。
 */
public class MockAiProvider implements AiProvider {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String reply(List<AiTurn> history) {
        String userText = lastUserText(history);
        String lower = userText.toLowerCase(Locale.ROOT);

        if (lower.contains("时间") || lower.contains("几点")) {
            return "现在是 " + LocalDateTime.now().format(TIME_FMT) + "。\n（本地模拟模式，未接入真实模型）";
        }
        if (lower.contains("你好") || lower.contains("hi") || lower.contains("hello")) {
            return "你好呀，我是微聊里的 AI 伙伴。\n"
                    + "现在跑的是**本地模拟实现**，回复是规则生成的，不是真模型。\n"
                    + "配置 `app.ai.base-url` + `app.ai.api-key` 就能换成真实模型，业务代码不用改。";
        }
        if (lower.contains("你是谁") || lower.contains("介绍")) {
            return "我是微聊的内置 AI 账号：和你说话走的是同一套消息管道"
                    + "（落库 → 分配 seq → 事务提交后推送），所以历史、未读、多端同步都和非 AI 好友一样。";
        }
        if (userText.isBlank()) {
            return "你想聊点什么？";
        }
        if (userText.length() > 200) {
            return "收到你这条比较长的消息（" + userText.length() + " 字）。"
                    + "当前是模拟模式，接上真实模型后它会正常理解并回答。";
        }
        return "收到：「" + userText + "」\n"
                + "当前是本地模拟模式（未配置模型 API）。"
                + "把 `app.ai.base-url`、`app.ai.api-key`、`app.ai.model` 填上即可接入真实模型。";
    }

    private String lastUserText(List<AiTurn> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) {
                return history.get(i).content() == null ? "" : history.get(i).content().trim();
            }
        }
        return "";
    }
}
