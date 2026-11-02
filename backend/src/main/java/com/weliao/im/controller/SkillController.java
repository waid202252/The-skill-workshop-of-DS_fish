package com.weliao.im.controller;

import com.weliao.im.common.R;
import com.weliao.im.security.CurrentUser;
import com.weliao.im.service.SkillService;
import com.weliao.im.vo.SkillReadmeVO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * 技能市场。产品主视图——从 GitHub 拉取 AI 技能仓库，聊天退居辅助。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 搜索技能。q 为空时使用默认搜索词。
     * 结果会本地缓存，同一查询在 TTL 内不重复打 GitHub。
     */
    @GetMapping("/search")
    public R search(@RequestParam(name = "q", required = false) String q,
                    @RequestParam(name = "page", defaultValue = "1") int page,
                    @RequestParam(name = "perPage", defaultValue = "20") int perPage) {
        return R.ok(skillService.search(CurrentUser.id(), q, page, perPage));
    }

    /** 首页推荐：本地缓存里 star 最高的，不消耗 GitHub 配额 */
    @GetMapping("/trending")
    public R trending() {
        return R.ok(skillService.trending(CurrentUser.id()));
    }

    /** 预置推荐分类（每个分类对应一个预设的 GitHub 查询） */
    @GetMapping("/categories")
    public R categories() {
        return R.ok(skillService.categories());
    }

    /** 技能说明文档正文（优先 SKILL.md，其次 README.md） */
    @GetMapping("/{id}/readme")
    public R readme(@PathVariable Long id) {
        return R.ok(skillService.readme(id));
    }

    /**
     * 下载说明文档。
     * <p>
     * 走服务端代理而不是让前端直连 raw.githubusercontent：
     * 一是避免浏览器跨域，二是国内网络下直连经常失败，服务端有缓存可以兜住。
     */
    @GetMapping("/{id}/readme/download")
    public ResponseEntity<byte[]> downloadReadme(@PathVariable Long id) {
        SkillReadmeVO readme = skillService.readme(id);
        byte[] bytes = readme.markdown().getBytes(StandardCharsets.UTF_8);
        String fileName = readme.fileName() == null || readme.fileName().isBlank()
                ? "README.md" : readme.fileName();
        String disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(bytes);
    }

    /** 我安装的技能 */
    @GetMapping("/installed")
    public R installed() {
        return R.ok(skillService.listInstalled(CurrentUser.id()));
    }

    @GetMapping("/{id}")
    public R detail(@PathVariable Long id) {
        return R.ok(skillService.detail(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/install")
    public R install(@PathVariable Long id) {
        return R.ok(skillService.install(CurrentUser.id(), id));
    }

    @DeleteMapping("/{id}/install")
    public R uninstall(@PathVariable Long id) {
        skillService.uninstall(CurrentUser.id(), id);
        return R.ok();
    }
}
