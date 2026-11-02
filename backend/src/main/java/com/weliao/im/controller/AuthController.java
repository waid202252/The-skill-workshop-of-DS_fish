package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.dto.LoginRequest;
import com.weliao.im.dto.RegisterRequest;
import com.weliao.im.security.CurrentUser;
import com.weliao.im.service.UserService;
import com.weliao.im.vo.AuthVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口，免登录访问。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public R register(@Valid @RequestBody RegisterRequest req) {
        AuthVO vo = userService.register(req);
        return R.ok(vo);
    }

    /** 修改密码：需要旧密码。接口只返回成功与否，不返回新 token（旧 token 仍然有效） */
    @PostMapping("/password")
    public R changePassword(@Valid @RequestBody com.weliao.im.dto.ChangePasswordRequest req) {
        userService.changePassword(CurrentUser.id(), req.oldPassword(), req.newPassword());
        return R.ok(null);
    }

    @PostMapping("/login")
    public R login(@Valid @RequestBody LoginRequest req) {
        AuthVO vo = userService.login(req);
        return R.ok(vo);
    }
}
