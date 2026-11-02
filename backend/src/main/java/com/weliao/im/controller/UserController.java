package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.dto.UpdateProfileRequest;
import com.weliao.im.security.CurrentUser;
import com.weliao.im.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 当前用户资料与用户搜索。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public R me() {
        return R.ok(userService.me(CurrentUser.id()));
    }

    @PutMapping("/me")
    public R updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return R.ok(userService.updateProfile(CurrentUser.id(), req));
    }

    @GetMapping("/search")
    public R search(@RequestParam(name = "keyword", required = false) String keyword) {
        return R.ok(userService.search(CurrentUser.id(), keyword));
    }
}
