package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.common.BizException;
import com.weliao.im.dto.CommunityAskRequest;
import com.weliao.im.dto.CommunityFeedbackRequest;
import com.weliao.im.security.CurrentUser;
import com.weliao.im.service.CommunityService;
import org.springframework.web.bind.annotation.*;

/**
 * 社区：指南/配方 + AI 首答。
 * <p>
 * GET 指南允许游客只读（配合前端公开社区页）；提问与反馈需要登录。
 */
@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /** 信息流。type=GUIDE/RECIPE，q 为可选关键词，limit 默认 12。 */
    @GetMapping("/guides")
    public R guides(@RequestParam(name = "type", required = false) String type,
                    @RequestParam(name = "q", required = false) String q,
                    @RequestParam(name = "limit", defaultValue = "12") int limit) {
        return R.ok(communityService.list(type, q, limit));
    }

    /** 某个技能关联的配方/指南。 */
    @GetMapping("/guides/by-skill")
    public R guidesBySkill(@RequestParam("fullName") String fullName) {
        return R.ok(communityService.guidesForSkill(fullName));
    }

    @GetMapping("/guides/{id}")
    public R guideDetail(@PathVariable Long id) {
        return R.ok(communityService.detail(id, CurrentUser.idOrNull()));
    }

    @PostMapping("/guides/{id}/feedback")
    public R feedback(@PathVariable Long id, @RequestBody CommunityFeedbackRequest request) {
        if (request == null) {
            throw BizException.badRequest("缺少反馈内容");
        }
        return R.ok(communityService.feedback(id, CurrentUser.id(), request.helpful()));
    }

    /** AI 首答：基于社区指南回答，并保存问题用于后续人工补充。 */
    @PostMapping("/ask")
    public R ask(@RequestBody CommunityAskRequest request) {
        return R.ok(communityService.ask(CurrentUser.id(), request));
    }

    /** 首答是否有帮助；没帮助的进入人工补充队列。 */
    @PostMapping("/questions/{id}/feedback")
    public R questionFeedback(@PathVariable Long id, @RequestBody CommunityFeedbackRequest request) {
        if (request == null) {
            throw BizException.badRequest("缺少反馈内容");
        }
        communityService.questionFeedback(id, CurrentUser.id(), request.helpful());
        return R.ok();
    }
}
