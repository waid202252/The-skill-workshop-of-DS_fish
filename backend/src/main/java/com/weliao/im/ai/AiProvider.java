package com.weliao.im.ai;

import java.util.List;

/**
 * 模型能力抽象。
 * <p>
 * 存在的意义：本项目的 AI 伙伴不绑定任何一家厂商。无论是 OpenAI 协议兼容的平台
 * （DeepSeek、通义、以及各类聚合 API 服务），还是将来接入本地推理，都只需要换一个实现。
 * 换模型 = 改配置，不动业务代码。
 */
public interface AiProvider {

    /** 供应商名，用于日志与健康检查展示。 */
    String name();

    /**
     * 生成回复。
     *
     * @param history 已按时间正序排好的历史对话（含 system prompt）
     * @return 模型回复文本，永不返回 null
     */
    String reply(List<AiTurn> history) throws AiException;
}
