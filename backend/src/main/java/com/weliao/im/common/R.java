package com.weliao.im.common;

/**
 * 统一响应信封：{ code, message, data }。
 * code = 0 表示成功。
 */
public record R(int code, String message, Object data) {

    public static R ok(Object data) {
        return new R(0, "ok", data);
    }

    public static R ok() {
        return new R(0, "ok", null);
    }

    public static R fail(int code, String message) {
        return new R(code, message, null);
    }
}
