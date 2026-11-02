package com.weliao.im.vo;

import java.util.List;

/**
 * AI 首答结果。
 *
 * @param mock           true 表示当前是本地模拟模式，没有调用真实模型
 * @param relatedGuides  回答引用的指南，前端可继续跳转
 */
public record CommunityAskVO(
        Long questionId,
        String answer,
        boolean mock,
        List<CommunityGuideCardVO> relatedGuides
) {
}
