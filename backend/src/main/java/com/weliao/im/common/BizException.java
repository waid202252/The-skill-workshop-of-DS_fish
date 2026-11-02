package com.weliao.im.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。抛出后由 {@link GlobalExceptionHandler} 转成统一信封 + 对应 HTTP 状态码。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BizException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BizException badRequest(String message) {
        return new BizException(40000, message, HttpStatus.BAD_REQUEST);
    }

    public static BizException unauthorized(String message) {
        return new BizException(40100, message, HttpStatus.UNAUTHORIZED);
    }

    public static BizException forbidden(String message) {
        return new BizException(40300, message, HttpStatus.FORBIDDEN);
    }

    public static BizException notFound(String message) {
        return new BizException(40400, message, HttpStatus.NOT_FOUND);
    }

    public static BizException conflict(String message) {
        return new BizException(40900, message, HttpStatus.CONFLICT);
    }
}
