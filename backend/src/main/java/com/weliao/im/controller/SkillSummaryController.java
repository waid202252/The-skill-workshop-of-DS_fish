package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.service.SkillSummaryService;
import org.springframework.web.bind.annotation.*;

/**
 * 技能 README 的 AI 总结。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillSummaryController {

    private final SkillSummaryService summaryService;

    public SkillSummaryController(SkillSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * 点击技能详情时调用。首次会真实调用模型，之后缓存 30 分钟。
     * refresh=true 可强制重新生成。
     */
    @GetMapping("/{id}/summary")
    public R summary(@PathVariable Long id,
                     @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return R.ok(summaryService.summarize(id, refresh));
    }
}
