package com.weliao.im.security;

import com.weliao.im.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 取当前登录用户 id 的小工具。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 当前登录用户 id；未登录时抛 401 业务异常。
     */
    public static Long id() {
        Long userId = idOrNull();
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        return userId;
    }

    /**
     * 当前登录用户 id；未登录返回 null。
     * <p>
     * 社区指南允许游客只读，因此详情接口需要“有登录就带反馈状态，没有也能看”。
     */
    public static Long idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
