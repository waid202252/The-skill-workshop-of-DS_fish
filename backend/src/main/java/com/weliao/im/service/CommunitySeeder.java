package com.weliao.im.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weliao.im.config.AppProperties;
import com.weliao.im.entity.CommunityGuide;
import com.weliao.im.entity.CommunityGuideSkill;
import com.weliao.im.repository.CommunityGuideRepository;
import com.weliao.im.repository.CommunityGuideSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 空表时导入 AI 整理的基础指南/配方。
 * <p>
 * 只在 t_community_guide 为空时执行，避免覆盖运营后续编辑的内容。
 * 内容里的 AI 标识会原样保存，前端会显示“AI 整理 · 试验版”。
 */
@Component
public class CommunitySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CommunitySeeder.class);

    private final CommunityGuideRepository guideRepository;
    private final CommunityGuideSkillRepository guideSkillRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public CommunitySeeder(CommunityGuideRepository guideRepository,
                           CommunityGuideSkillRepository guideSkillRepository,
                           ObjectMapper objectMapper,
                           AppProperties props) {
        this.guideRepository = guideRepository;
        this.guideSkillRepository = guideSkillRepository;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.getCommunity().isSeedOnEmpty()) {
            return;
        }
        if (guideRepository.count() > 0) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("community/seed-guides.json");
        if (!resource.exists()) {
            log.warn("社区种子文件不存在，跳过导入");
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            List<SeedGuide> seeds = objectMapper.readValue(in, new TypeReference<>() {
            });
            LocalDateTime now = LocalDateTime.now();
            int imported = 0;
            for (int i = 0; i < seeds.size(); i++) {
                SeedGuide seed = seeds.get(i);
                CommunityGuide guide = new CommunityGuide();
                guide.setType(seed.type());
                guide.setTitle(seed.title());
                guide.setSummary(seed.summary());
                guide.setContent(seed.content());
                guide.setPromptText(seed.promptText());
                guide.setEmoji(seed.emoji());
                guide.setTags(seed.tags() == null ? "" : String.join(",", seed.tags()));
                guide.setDifficulty(seed.difficulty());
                guide.setDuration(seed.duration());
                guide.setCost(seed.cost());
                guide.setAiGenerated(true);
                guide.setReviewStatus("PUBLISHED");
                guide.setHelpfulCount(0);
                guide.setNotHelpfulCount(0);
                guide.setViewCount(0);
                // 让信息流有自然的先后顺序，而不是同一秒
                guide.setPublishedAt(now.minusHours(i));
                guide.setCreatedAt(now.minusHours(i));
                guide.setUpdatedAt(now.minusHours(i));
                CommunityGuide saved = guideRepository.save(guide);

                List<SeedSkill> skills = seed.relatedSkills();
                if (skills != null) {
                    for (int j = 0; j < skills.size(); j++) {
                        SeedSkill item = skills.get(j);
                        CommunityGuideSkill link = new CommunityGuideSkill();
                        link.setGuideId(saved.getId());
                        link.setFullName(item.fullName());
                        link.setName(item.name());
                        link.setOwner(item.owner());
                        link.setHtmlUrl(item.htmlUrl());
                        link.setDisplayOrder(j);
                        guideSkillRepository.save(link);
                    }
                }
                imported++;
            }
            log.info("已导入 {} 篇社区指南/配方（AI 整理，待运营复核）", imported);
        } catch (Exception e) {
            log.warn("社区种子导入失败，不影响主流程: {}", e.getMessage());
        }
    }

    private record SeedGuide(
            String type,
            String title,
            String summary,
            String content,
            String promptText,
            String emoji,
            List<String> tags,
            String difficulty,
            String duration,
            String cost,
            List<SeedSkill> relatedSkills
    ) {
    }

    private record SeedSkill(
            String fullName,
            String name,
            String owner,
            String htmlUrl
    ) {
    }
}
