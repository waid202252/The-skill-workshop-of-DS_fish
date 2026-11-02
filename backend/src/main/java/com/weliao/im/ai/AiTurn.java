package com.weliao.im.ai;

/**
 * 一轮对话。role 取值与 OpenAI 协议一致：system / user / assistant。
 */
public record AiTurn(String role, String content) {

    public static AiTurn system(String content) {
        return new AiTurn("system", content);
    }

    public static AiTurn user(String content) {
        return new AiTurn("user", content);
    }

    public static AiTurn assistant(String content) {
        return new AiTurn("assistant", content);
    }
}
