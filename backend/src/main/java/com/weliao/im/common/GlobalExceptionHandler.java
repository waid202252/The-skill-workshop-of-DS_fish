package com.weliao.im.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：所有异常都转成 { code, message, data } 信封。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R> handleBiz(BizException e, HttpServletRequest req) {
        log.debug("业务异常 {} {} -> {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(R.fail(e.getCode(), e.getMessage()));
    }

    /** @Valid 校验失败，取第一条字段错误作为提示。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(R.fail(40000, msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(R.fail(40000, "请求体格式不正确"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(R.fail(40000, "缺少参数：" + e.getParameterName()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(R.fail(41300, "文件超过 20MB 限制"));
    }

    /**
     * GitHub 拉取失败 → 502。
     * <p>
     * 这类问题的责任方是外部服务（网络/限流），不是我们的 bug，
     * 返回 502 Bad Gateway 比 500 更能说明问题；message 里已经写了人话。
     */
    @ExceptionHandler(com.weliao.im.service.GitHubException.class)
    public ResponseEntity<R> handleGitHub(com.weliao.im.service.GitHubException e) {
        log.warn("GitHub 调用失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(R.fail(50200, e.getMessage()));
    }

    /** 静态资源（上传文件）路径不存在时按 404 处理，而不是 500。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(40400, "文件不存在"));
    }

    /**
     * 数据库唯一约束冲突 → 409。
     * <p>
     * 典型场景：两个请求同时注册同一用户名（"先 existsByUsername 再 save" 挡不住并发），
     * 或两个请求同时为同一对用户创建单聊会话。
     * 兜底成 409 + 可读文案，而不是抛 500 让前端看到"服务器内部错误"。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R> handleConflict(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("唯一约束冲突 {} {}: {}", req.getMethod(), req.getRequestURI(),
                e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(40900, "数据已存在或发生并发冲突，请刷新后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R> handleOther(Exception e, HttpServletRequest req) {
        log.error("未处理异常 {} {}", req.getMethod(), req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(50000, "服务器内部错误"));
    }
}
