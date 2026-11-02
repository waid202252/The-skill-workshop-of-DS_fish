package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.dto.FriendRequestCreate;
import com.weliao.im.security.CurrentUser;
import com.weliao.im.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 好友与好友申请。
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    public R list() {
        return R.ok(friendService.listFriends(CurrentUser.id()));
    }

    @DeleteMapping("/{userId}")
    public R delete(@PathVariable Long userId) {
        friendService.deleteFriend(CurrentUser.id(), userId);
        return R.ok();
    }

    @GetMapping("/requests")
    public R requests() {
        return R.ok(friendService.listRequests(CurrentUser.id()));
    }

    @PostMapping("/requests")
    public R createRequest(@Valid @RequestBody FriendRequestCreate req) {
        return R.ok(friendService.createRequest(CurrentUser.id(), req));
    }

    @PostMapping("/requests/{id}/accept")
    public R accept(@PathVariable Long id) {
        return R.ok(friendService.acceptRequest(CurrentUser.id(), id));
    }

    @PostMapping("/requests/{id}/reject")
    public R reject(@PathVariable Long id) {
        friendService.rejectRequest(CurrentUser.id(), id);
        return R.ok();
    }
}
