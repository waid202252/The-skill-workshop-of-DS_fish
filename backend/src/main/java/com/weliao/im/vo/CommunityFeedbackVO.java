package com.weliao.im.vo;

/**
 * 指南反馈结果。
 */
public record CommunityFeedbackVO(
        int helpfulCount,
        int notHelpfulCount,
        boolean myFeedback
) {
}
