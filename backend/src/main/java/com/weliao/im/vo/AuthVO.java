package com.weliao.im.vo;

/**
 * 登录/注册成功返回。
 */
public record AuthVO(String token, UserVO user) {
}
