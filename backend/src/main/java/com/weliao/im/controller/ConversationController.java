package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.dto.*;
import com.weliao.im.security.CurrentUser;
import com.weliao.im.service.ConversationService;
import com.weliao.im.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 会话、消息、已读。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @GetMapping
    public R list() {
        return R.ok(conversationService.list(CurrentUser.id()));
    }

    @PostMapping("/single")
    public R openSingle(@Valid @RequestBody CreateSingleRequest req) {
        return R.ok(conversationService.openSingle(CurrentUser.id(), req.userId()));
    }

    @PostMapping("/group")
    public R createGroup(@Valid @RequestBody CreateGroupRequest req) {
        return R.ok(conversationService.createGroup(CurrentUser.id(), req.name(), req.memberIds()));
    }

    @GetMapping("/{id}")
    public R get(@PathVariable Long id) {
        return R.ok(conversationService.get(CurrentUser.id(), id));
    }

    @GetMapping("/{id}/messages")
    public R history(@PathVariable Long id,
                     @RequestParam(name = "beforeSeq", required = false) Long beforeSeq,
                     @RequestParam(name = "afterSeq", required = false) Long afterSeq,
                     @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return R.ok(messageService.history(CurrentUser.id(), id, beforeSeq, afterSeq, limit));
    }

    /** REST 兜底发送：WebSocket 不可用或移动端后台时可用，返回后仍会广播给其他成员。 */
    @PostMapping("/{id}/messages")
    public R send(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        return R.ok(messageService.send(CurrentUser.id(), id, req));
    }

    /** 撤回自己的消息（受时间窗口限制） */
    @PostMapping("/{id}/messages/{messageId}/recall")
    public R recall(@PathVariable Long id, @PathVariable Long messageId) {
        return R.ok(messageService.recall(CurrentUser.id(), id, messageId));
    }

    @PostMapping("/{id}/read")
    public R read(@PathVariable Long id, @Valid @RequestBody ReadRequest req) {
        conversationService.markRead(CurrentUser.id(), id, req.seq());
        return R.ok();
    }

    @PostMapping("/{id}/members")
    public R addMembers(@PathVariable Long id, @Valid @RequestBody AddMembersRequest req) {
        return R.ok(conversationService.addMembers(CurrentUser.id(), id, req.userIds()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public R removeMember(@PathVariable Long id, @PathVariable Long userId) {
        conversationService.removeMember(CurrentUser.id(), id, userId);
        return R.ok();
    }
}
