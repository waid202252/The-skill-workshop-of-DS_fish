package com.weliao.im.service;

import com.weliao.im.common.BizException;
import com.weliao.im.dto.CommunityAskRequest;
import com.weliao.im.entity.CommunityFeedback;
import com.weliao.im.entity.CommunityGuide;
import com.weliao.im.entity.CommunityGuideSkill;
import com.weliao.im.entity.CommunityQuestion;
import com.weliao.im.repository.CommunityFeedbackRepository;
import com.weliao.im.repository.CommunityGuideRepository;
import com.weliao.im.repository.CommunityGuideSkillRepository;
import com.weliao.im.repository.CommunityQuestionRepository;
import com.weliao.im.vo.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 社区指南/配方与 AI 首答。
 */
@Service
public class CommunityService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final Set<String> TYPES = Set.of("GUIDE", "RECIPE");
    private static final Set<String> STOP_WORDS = Set.of(
            "什么", "怎么", "为什么", "可以", "能不能", "能跑", "没有", "我的", "我是",
            "请问", "一下", "到底", "哪里", "哪个", "如何", "是否", "这个", "那个",
            "一个", "哪些", "就是", "还是", "以及", "然后", "但是", "因为", "所以",
            "如果", "现在", "时候", "问题", "有没有", "要不要", "是不是");
    private static final int MAX_LIMIT = 30;

    private final CommunityGuideRepository guideRepository;
    private final CommunityGuideSkillRepository guideSkillRepository;
    private final CommunityFeedbackRepository feedbackRepository;
    private final CommunityQuestionRepository questionRepository;
    private final CommunityAiService aiService;

    public CommunityService(CommunityGuideRepository guideRepository,
                            CommunityGuideSkillRepository guideSkillRepository,
                            CommunityFeedbackRepository feedbackRepository,
                            CommunityQuestionRepository questionRepository,
                            CommunityAiService aiService) {
        this.guideRepository = guideRepository;
        this.guideSkillRepository = guideSkillRepository;
        this.feedbackRepository = feedbackRepository;
        this.questionRepository = questionRepository;
        this.aiService = aiService;
    }

    // ---------------------------------------------------------------- 列表/详情

    public List<CommunityGuideCardVO> list(String rawType, String rawKeyword, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        PageRequest page = PageRequest.of(0, size);
        String keyword = StringUtils.hasText(rawKeyword) ? rawKeyword.trim() : null;

        List<CommunityGuide> guides;
        if (keyword != null) {
            guides = guideRepository.searchPublished(keyword, PUBLISHED, page);
        } else {
            String type = normalizeType(rawType);
            guides = type == null
                    ? guideRepository.findByReviewStatusOrderByPublishedAtDesc(PUBLISHED, page)
                    : guideRepository.findByReviewStatusAndTypeOrderByPublishedAtDesc(PUBLISHED, type, page);
        }
        return toCards(guides);
    }

    @Transactional
    public CommunityGuideVO detail(Long guideId, Long userId) {
        CommunityGuide guide = guideRepository.findByIdAndReviewStatus(guideId, PUBLISHED)
                .orElseThrow(() -> BizException.notFound("这篇内容不存在或已下线"));
        guide.setViewCount(guide.getViewCount() + 1);
        guideRepository.save(guide);

        Boolean myFeedback = null;
        if (userId != null) {
            myFeedback = feedbackRepository.findByGuideIdAndUserId(guideId, userId)
                    .map(CommunityFeedback::isHelpful)
                    .orElse(null);
        }
        return toDetail(guide, myFeedback);
    }

    public List<CommunityGuideCardVO> guidesForSkill(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return List.of();
        }
        List<Long> guideIds = guideSkillRepository.findByFullName(fullName.trim()).stream()
                .map(CommunityGuideSkill::getGuideId)
                .distinct()
                .toList();
        if (guideIds.isEmpty()) {
            return List.of();
        }
        List<CommunityGuide> guides = guideRepository.findByReviewStatusAndIdIn(PUBLISHED, guideIds);
        guides.sort(Comparator.comparing(CommunityGuide::getPublishedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return toCards(guides.stream().limit(6).toList());
    }

    // ---------------------------------------------------------------- 反馈

    @Transactional
    public CommunityFeedbackVO feedback(Long guideId, Long userId, boolean helpful) {
        CommunityGuide guide = guideRepository.findByIdAndReviewStatus(guideId, PUBLISHED)
                .orElseThrow(() -> BizException.notFound("这篇内容不存在或已下线"));

        CommunityFeedback fb = feedbackRepository.findByGuideIdAndUserId(guideId, userId)
                .orElseGet(() -> {
                    CommunityFeedback created = new CommunityFeedback();
                    created.setGuideId(guideId);
                    created.setUserId(userId);
                    created.setCreatedAt(LocalDateTime.now());
                    return created;
                });
        fb.setHelpful(helpful);
        feedbackRepository.save(fb);

        guide.setHelpfulCount((int) feedbackRepository.countByGuideIdAndHelpfulTrue(guideId));
        guide.setNotHelpfulCount((int) feedbackRepository.countByGuideIdAndHelpfulFalse(guideId));
        guideRepository.save(guide);
        return new CommunityFeedbackVO(guide.getHelpfulCount(), guide.getNotHelpfulCount(), helpful);
    }

    // ---------------------------------------------------------------- AI 首答

    @Transactional
    public CommunityAskVO ask(Long userId, CommunityAskRequest request) {
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.length() < 2) {
            throw BizException.badRequest("问题太短了，再具体一点");
        }
        if (question.length() > 500) {
            throw BizException.badRequest("问题最多 500 字");
        }

        List<CommunityGuide> context = findContext(question);
        String answer = aiService.answer(question, context);

        CommunityQuestion saved = new CommunityQuestion();
        saved.setUserId(userId);
        saved.setQuestion(question);
        saved.setAnswer(answer);
        saved.setStatus("AI_ANSWERED");
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());
        questionRepository.save(saved);

        return new CommunityAskVO(saved.getId(), answer, aiService.isMock(), toCards(context));
    }

    @Transactional
    public void questionFeedback(Long questionId, Long userId, boolean helpful) {
        CommunityQuestion question = questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> BizException.notFound("问题记录不存在"));
        question.setHelpful(helpful);
        question.setStatus(helpful ? "RESOLVED" : "NEEDS_HUMAN");
        question.setUpdatedAt(LocalDateTime.now());
        questionRepository.save(question);
    }

    private List<CommunityGuide> findContext(String question) {
        Map<Long, CommunityGuide> byId = new LinkedHashMap<>();
        Map<Long, Integer> score = new HashMap<>();
        for (Map.Entry<String, Integer> term : extractTerms(question).entrySet()) {
            List<CommunityGuide> hits = guideRepository.searchPublished(
                    term.getKey(), PUBLISHED, PageRequest.of(0, 4));
            for (CommunityGuide guide : hits) {
                byId.putIfAbsent(guide.getId(), guide);
                score.merge(guide.getId(), term.getValue(), Integer::sum);
            }
        }
        if (!byId.isEmpty()) {
            int maxScore = score.values().stream().mapToInt(Integer::intValue).max().orElse(1);
            int threshold = maxScore >= 4 ? Math.max(1, maxScore - 2) : 1;
            return byId.values().stream()
                    .filter(g -> score.getOrDefault(g.getId(), 0) >= threshold)
                    .sorted(Comparator
                            .comparingInt((CommunityGuide g) -> score.getOrDefault(g.getId(), 0))
                            .reversed()
                            .thenComparing(CommunityGuide::getHelpfulCount, Comparator.reverseOrder())
                            .thenComparing(CommunityGuide::getPublishedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(3)
                    .toList();
        }
        // 没有关键词命中时给最近/最有用的几篇，避免首答直接空手而归
        return guideRepository.findByReviewStatusOrderByHelpfulCountDescPublishedAtDesc(
                PUBLISHED, PageRequest.of(0, 3));
    }

    /**
     * 把自然语言问题拆成可检索的词。
     * <p>
     * 不引入分词库：英文/数字按空格，中文按 2 字滑窗；英文词权重更高，
     * 这样“ComfyUI 没有显卡能跑吗”会优先命中 ComfyUI 配方，而不是泛泛的入门指南。
     */
    private Map<String, Integer> extractTerms(String question) {
        Map<String, Integer> terms = new LinkedHashMap<>();
        String cleaned = question.replaceAll("[^\\p{IsHan}A-Za-z0-9]+", " ").trim();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (part.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")) {
                if (part.length() >= 2) {
                    terms.merge(part, 3, Integer::sum);
                }
            } else {
                for (int i = 0; i + 1 < part.length() && terms.size() < 10; i++) {
                    String term = part.substring(i, i + 2);
                    if (!STOP_WORDS.contains(term)) {
                        terms.merge(term, 1, Integer::sum);
                    }
                }
            }
            if (terms.size() >= 10) {
                break;
            }
        }
        if (terms.isEmpty() && question.length() >= 2) {
            terms.put(question, 1);
        }
        return terms;
    }

    // ---------------------------------------------------------------- 映射

    private String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return null;
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        return TYPES.contains(type) ? type : null;
    }

    private List<CommunityGuideCardVO> toCards(List<CommunityGuide> guides) {
        if (guides.isEmpty()) {
            return List.of();
        }
        List<Long> ids = guides.stream().map(CommunityGuide::getId).toList();
        Map<Long, List<CommunitySkillVO>> skillsByGuide = guideSkillRepository.findByGuideIdIn(ids).stream()
                .sorted(Comparator.comparingInt(CommunityGuideSkill::getDisplayOrder))
                .collect(Collectors.groupingBy(CommunityGuideSkill::getGuideId,
                        LinkedHashMap::new,
                        Collectors.mapping(s -> new CommunitySkillVO(
                                s.getFullName(), s.getName(), s.getOwner(), s.getHtmlUrl()),
                                Collectors.toList())));
        return guides.stream()
                .map(g -> new CommunityGuideCardVO(
                        g.getId(),
                        g.getType(),
                        g.getTitle(),
                        g.getSummary(),
                        g.getEmoji(),
                        splitTags(g.getTags()),
                        g.getDifficulty(),
                        g.getDuration(),
                        g.getCost(),
                        g.isAiGenerated(),
                        g.getHelpfulCount(),
                        g.getPublishedAt(),
                        skillsByGuide.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    private CommunityGuideVO toDetail(CommunityGuide guide, Boolean myFeedback) {
        List<CommunitySkillVO> skills = guideSkillRepository
                .findByGuideIdOrderByDisplayOrderAsc(guide.getId()).stream()
                .map(s -> new CommunitySkillVO(s.getFullName(), s.getName(), s.getOwner(), s.getHtmlUrl()))
                .toList();
        return new CommunityGuideVO(
                guide.getId(),
                guide.getType(),
                guide.getTitle(),
                guide.getSummary(),
                guide.getContent(),
                guide.getPromptText(),
                guide.getEmoji(),
                splitTags(guide.getTags()),
                guide.getDifficulty(),
                guide.getDuration(),
                guide.getCost(),
                guide.isAiGenerated(),
                guide.getReviewStatus(),
                guide.getReviewedBy(),
                guide.getHelpfulCount(),
                guide.getNotHelpfulCount(),
                guide.getViewCount(),
                guide.getPublishedAt(),
                skills,
                myFeedback);
    }

    private List<String> splitTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
