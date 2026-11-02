package com.weliao.im.config;

import com.weliao.im.ai.AiProvider;
import com.weliao.im.ai.MockAiProvider;
import com.weliao.im.ai.OpenAiCompatibleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 按配置装配 AI 供应商。
 * <p>
 * 没有配置真实模型时回落到本地模拟实现——保证「不填任何 key 也能把项目跑起来并体验 AI 对话」，
 * 而不是启动直接报错。这是可演示性优先于"看起来很正规"的一处刻意取舍。
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiProvider aiProvider(AppProperties props) {
        AppProperties.Ai ai = props.getAi();
        if (!ai.isEnabled()) {
            log.info("AI 伙伴已关闭（app.ai.enabled=false）");
            return new MockAiProvider();
        }
        if (!StringUtils.hasText(ai.getBaseUrl()) || !StringUtils.hasText(ai.getModel())) {
            log.info("AI 伙伴运行在【本地模拟模式】：未配置 app.ai.base-url / model，回复由规则生成");
            return new MockAiProvider();
        }
        if (!StringUtils.hasText(ai.getApiKey())) {
            log.info("AI 未配置 api-key，将按无鉴权本地模型（如 Ollama）访问: {}", ai.getBaseUrl());
        }
        log.info("AI 伙伴接入真实模型: baseUrl={} model={}", ai.getBaseUrl(), ai.getModel());
        return new OpenAiCompatibleProvider(ai.getBaseUrl(), ai.getApiKey(), ai.getModel(),
                Duration.ofSeconds(ai.getTimeoutSeconds()), ai.getTemperature());
    }
}
