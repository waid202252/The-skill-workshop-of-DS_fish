package com.weliao.im.vo;

import java.util.List;

/**
 * 技能搜索结果。
 *
 * @param items    当前页技能
 * @param total    命中总数（GitHub 返回，可能是不精确的估算值）
 * @param page     页码，从 1 开始
 * @param perPage  每页条数
 * @param cached   true 表示本次结果来自本地缓存，没有消耗 GitHub 配额
 * @param degraded true 表示 GitHub 请求失败，退回了本地缓存搜索
 */
public record SkillSearchVO(
        List<SkillVO> items,
        long total,
        int page,
        int perPage,
        boolean cached,
        boolean degraded,
        String notice
) {
}
