package com.weliao.im.ai;

/**
 * 调用模型失败。区分「可重试」与「配置错误」，便于上层决定是提示用户还是只记日志。
 */
public class AiException extends RuntimeException {

    private final boolean retryable;

    public AiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AiException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
