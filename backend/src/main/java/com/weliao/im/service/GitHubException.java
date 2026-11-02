package com.weliao.im.service;

/**
 * GitHub 调用失败。区分「限流」和「其他故障」，两者对用户的提示不同。
 */
public class GitHubException extends RuntimeException {

    private final boolean rateLimited;

    public GitHubException(String message, boolean rateLimited) {
        super(message);
        this.rateLimited = rateLimited;
    }

    public boolean isRateLimited() {
        return rateLimited;
    }
}
