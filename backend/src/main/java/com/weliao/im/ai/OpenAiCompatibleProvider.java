package com.weliao.im.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 协议兼容的模型实现（<code>/v1/chat/completions</code>）。
 * <p>
 * 选这个协议而不是绑定某一家 SDK，是因为国内绝大多数平台
 * （DeepSeek、通义、各类聚合 API 服务）都兼容它：
 * 换供应商只需要改 base-url、api-key、model 三个配置项。
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final RestClient client;
    private final String model;
    private final String completionsPath;
    private final double temperature;

    public OpenAiCompatibleProvider(String baseUrl, String apiKey, String model,
                                    Duration timeout, double temperature) {
        this.model = model;
        this.temperature = temperature;
        // 允许用户把 /v1 或 /v4 写在 base-url 里，也允许只写到域名。
        // 智谱是 /api/paas/v4/chat/completions，OpenAI/OpenRouter/Ollama 是 /v1/chat/completions。
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.completionsPath = base.endsWith("/v1") || base.endsWith("/v4")
                ? "/chat/completions"
                : "/v1/chat/completions";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(base)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        // Ollama 等本地服务不需要鉴权；远程兼容服务仍然按 Bearer 传 key
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        this.client = builder.build();
    }

    @Override
    public String name() {
        return "openai-compatible(" + model + ")";
    }

    @Override
    public String reply(List<AiTurn> history) throws AiException {
        List<Map<String, String>> messages = new ArrayList<>(history.size());
        for (AiTurn turn : history) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("role", turn.role());
            m.put("content", turn.content());
            messages.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", false);

        JsonNode response;
        try {
            response = client.post()
                    .uri(completionsPath)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            // 4xx 多半是配置/额度问题，重试没意义；5xx/超时值得重试一次
            boolean retryable = !(e.getMessage() != null && e.getMessage().contains("400"));
            log.warn("调用模型失败 provider={}: {}", name(), e.getMessage());
            throw new AiException("模型调用失败: " + e.getMessage(), retryable, e);
        }

        if (response == null) {
            throw new AiException("模型返回为空", true);
        }
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new AiException("模型返回内容为空", true);
        }
        return content.trim();
    }
}
