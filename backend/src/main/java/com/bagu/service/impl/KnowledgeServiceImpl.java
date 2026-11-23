package com.bagu.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.bagu.common.BizException;
import com.bagu.common.ErrorCode;
import com.bagu.common.PageResult;
import com.bagu.dto.knowledge.ChapterVO;
import com.bagu.dto.knowledge.ModuleMetaVO;
import com.bagu.dto.knowledge.ProgressSummaryVO;
import com.bagu.dto.knowledge.ProgressVO;
import com.bagu.dto.knowledge.QuestionDetailVO;
import com.bagu.dto.knowledge.QuestionImageVO;
import com.bagu.dto.knowledge.QuestionItemRow;
import com.bagu.dto.knowledge.QuestionItemVO;
import com.bagu.dto.knowledge.QuestionQuery;
import com.bagu.dto.knowledge.ReaderVO;
import com.bagu.entity.KbChapter;
import com.bagu.entity.KbModule;
import com.bagu.entity.KbQuestion;
import com.bagu.entity.StudyProgress;
import com.bagu.mapper.KbChapterMapper;
import com.bagu.mapper.KbModuleMapper;
import com.bagu.mapper.KbQuestionMapper;
import com.bagu.mapper.StudyProgressMapper;
import com.bagu.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final String KEY_MODULES_META = "bagu:modules:meta";
    private static final String KEY_QUESTION_DETAIL_PREFIX = "bagu:q:detail:v3:";
    private static final Set<String> VALID_PRIORITIES = Set.of("P0", "P1", "P2");

    private final KbModuleMapper moduleMapper;
    private final KbChapterMapper chapterMapper;
    private final KbQuestionMapper questionMapper;
    private final StudyProgressMapper progressMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<ModuleMetaVO> listModules() {
        String cached = readCache(KEY_MODULES_META);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("解析模块缓存失败，降级回源 DB", e);
            }
        }

        List<KbModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<KbModule>().orderByAsc(KbModule::getSortOrder, KbModule::getId));
        List<KbChapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<KbChapter>().orderByAsc(KbChapter::getSortOrder, KbChapter::getId));

        Map<Long, Map<String, Object>> moduleStats = questionMapper.selectModuleStats().stream()
                .collect(Collectors.toMap(r -> toLong(r.get("module_id")), Function.identity()));
        Map<Long, Integer> chapterCounts = questionMapper.selectChapterQuestionCounts().stream()
                .collect(Collectors.toMap(r -> toLong(r.get("chapter_id")), r -> toInt(r.get("question_count"))));

        Map<Long, List<KbChapter>> chaptersByModule = chapters.stream()
                .collect(Collectors.groupingBy(KbChapter::getModuleId));

        List<ModuleMetaVO> result = modules.stream().map(m -> {
            Map<String, Object> st = moduleStats.getOrDefault(m.getId(), Collections.emptyMap());
            List<ChapterVO> chVos = chaptersByModule.getOrDefault(m.getId(), Collections.emptyList()).stream()
                    .map(ch -> ChapterVO.builder()
                            .id(ch.getId())
                            .code(ch.getCode())
                            .title(ch.getTitle())
                            .questionCount(chapterCounts.getOrDefault(ch.getId(), 0))
                            .build())
                    .toList();

            return ModuleMetaVO.builder()
                    .id(m.getId())
                    .slug(m.getSlug())
                    .order(m.getSortOrder())
                    .title(m.getTitle())
                    .fullTitle(m.getFullTitle())
                    .icon(m.getIcon())
                    .description(m.getDescription())
                    .sprintGuide(m.getSprintGuide())
                    .questionCount(toInt(st.get("question_count")))
                    .p0Count(toInt(st.get("p0_count")))
                    .p1Count(toInt(st.get("p1_count")))
                    .p2Count(toInt(st.get("p2_count")))
                    .imageCount(toInt(st.get("image_count")))
                    .chapters(chVos)
                    .build();
        }).toList();

        writeCache(KEY_MODULES_META, result, Duration.ofHours(24));
        return result;
    }

    @Override
    public PageResult<QuestionItemVO> pageQuestions(QuestionQuery query) {
        long userId = StpUtil.getLoginIdAsLong();
        Page<QuestionItemRow> mpPage = new Page<>(query.normalizedPage(), query.normalizedSize());

        IPage<QuestionItemRow> pageResult = questionMapper.selectQuestionPage(
                mpPage,
                userId,
                normalizeSlug(query.getSlug()),
                query.getChapterId(),
                query.parsedPriorities(),
                query.normalizedStatus(),
                query.normalizedKeyword()
        );

        List<QuestionItemVO> records = pageResult.getRecords().stream()
                .map(row -> QuestionItemVO.builder()
                        .id(row.getId())
                        .moduleSlug(row.getModuleSlug())
                        .moduleTitle(row.getModuleTitle())
                        .chapterId(row.getChapterId())
                        .code(row.getCode())
                        .priority(row.getPriority())
                        .title(row.getTitle())
                        .imageCount(row.getImageCount() == null ? 0 : row.getImageCount())
                        .hasTable(Boolean.TRUE.equals(row.getHasTable()))
                        .hasCode(Boolean.TRUE.equals(row.getHasCode()))
                        .hasAnimation(Boolean.TRUE.equals(row.getHasAnimation()))
                        .order(row.getSortOrder())
                        .progress(ProgressSummaryVO.builder()
                                .status(row.getProgressStatus() == null ? 0 : row.getProgressStatus().intValue())
                                .lapseCount(row.getLapseCount() == null ? 0 : row.getLapseCount())
                                .nextReviewAt(row.getNextReviewAt() == null ? null : row.getNextReviewAt().toInstant().toEpochMilli())
                                .isStarred(Boolean.TRUE.equals(row.getIsStarred()))
                                .hasNote(Boolean.TRUE.equals(row.getHasNote()))
                                .build())
                        .build())
                .toList();

        return PageResult.of(pageResult, records);
    }

    @Override
    public List<Long> listQuestionIds(QuestionQuery query) {
        long userId = StpUtil.getLoginIdAsLong();
        return questionMapper.selectQuestionIds(
                userId,
                normalizeSlug(query.getSlug()),
                query.getChapterId(),
                query.parsedPriorities(),
                query.normalizedStatus(),
                query.normalizedKeyword()
        );
    }

    @Override
    public QuestionDetailVO getQuestionDetail(Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        QuestionDetailVO base = getStaticQuestionDetail(id);

        StudyProgress sp = progressMapper.selectOne(new LambdaQueryWrapper<StudyProgress>()
                .eq(StudyProgress::getUserId, userId)
                .eq(StudyProgress::getQuestionId, id));

        return base.toBuilder()
                .progress(ProgressVO.from(sp, id))
                .build();
    }

    @Override
    public ReaderVO getModuleReader(String slug, String priority) {
        long userId = StpUtil.getLoginIdAsLong();
        ModuleMetaVO module = listModules().stream()
                .filter(m -> m.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "模块不存在: " + slug));

        LambdaQueryWrapper<KbQuestion> qw = new LambdaQueryWrapper<KbQuestion>()
                .eq(KbQuestion::getModuleId, module.getId())
                .eq(KbQuestion::getIsDeleted, false)
                .orderByAsc(KbQuestion::getSortOrder, KbQuestion::getId);

        if (priority != null && !priority.isBlank()) {
            String p = priority.trim().toUpperCase();
            if (!VALID_PRIORITIES.contains(p)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "无效的考频筛选参数: " + priority);
            }
            qw.eq(KbQuestion::getPriority, p);
        }

        List<KbQuestion> questions = questionMapper.selectList(qw);
        if (questions.isEmpty()) {
            return ReaderVO.builder().module(module).questions(Collections.emptyList()).build();
        }

        List<Long> qIds = questions.stream().map(KbQuestion::getId).toList();
        Map<Long, StudyProgress> progressMap = progressMapper.selectList(
                        new LambdaQueryWrapper<StudyProgress>()
                                .eq(StudyProgress::getUserId, userId)
                                .in(StudyProgress::getQuestionId, qIds))
                .stream()
                .collect(Collectors.toMap(StudyProgress::getQuestionId, Function.identity()));

        List<QuestionDetailVO> detailList = questions.stream()
                .map(q -> toDetailVo(q, module.getSlug(), module.getTitle(), ProgressVO.from(progressMap.get(q.getId()), q.getId())))
                .toList();

        return ReaderVO.builder()
                .module(module)
                .questions(detailList)
                .build();
    }

    private QuestionDetailVO getStaticQuestionDetail(Long id) {
        String key = KEY_QUESTION_DETAIL_PREFIX + id;
        String cached = readCache(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, QuestionDetailVO.class);
            } catch (Exception e) {
                log.warn("解析题目详情缓存失败 id={}", id, e);
            }
        }

        KbQuestion q = questionMapper.selectOne(new LambdaQueryWrapper<KbQuestion>()
                .eq(KbQuestion::getId, id)
                .eq(KbQuestion::getIsDeleted, false));
        if (q == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + id);
        }

        KbModule module = moduleMapper.selectById(q.getModuleId());
        String slug = module == null ? "" : module.getSlug();
        String title = module == null ? "" : module.getTitle();

        QuestionDetailVO vo = toDetailVo(q, slug, title, null);
        // 12 小时 + 0~60 分钟随机抖动，防止缓存雪崩
        Duration ttl = Duration.ofHours(12).plusMinutes(ThreadLocalRandom.current().nextInt(61));
        writeCache(key, vo, ttl);
        return vo;
    }

    private QuestionDetailVO toDetailVo(KbQuestion q, String moduleSlug, String moduleTitle, ProgressVO progress) {
        return QuestionDetailVO.builder()
                .id(q.getId())
                .moduleSlug(moduleSlug)
                .moduleTitle(moduleTitle)
                .chapterId(q.getChapterId())
                .code(q.getCode())
                .priority(q.getPriority())
                .title(q.getTitle())
                .hints(parseJson(q.getHintsJson(), new TypeReference<>() {}, Collections.emptyList()))
                .answerMd(q.getAnswerMd())
                .images(parseJson(q.getImagesJson(), new TypeReference<>() {}, Collections.emptyList()))
                .hasTable(Boolean.TRUE.equals(q.getHasTable()))
                .hasCode(Boolean.TRUE.equals(q.getHasCode()))
                .animationKey(q.getAnimationKey())
                .order(q.getSortOrder())
                .progress(progress)
                .build();
    }

    private <T> T parseJson(String raw, TypeReference<T> typeRef, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(raw, typeRef);
        } catch (Exception e) {
            log.warn("JSON 字段解析失败: {}", raw, e);
            return fallback;
        }
    }

    private String readCache(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取 Redis 缓存失败 key={}", key, e);
            return null;
        }
    }

    private void writeCache(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("写入 Redis 缓存失败 key={}", key, e);
        }
    }

    private String normalizeSlug(String slug) {
        return (slug == null || slug.isBlank()) ? null : slug.trim();
    }

    private long toLong(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }

    private int toInt(Object val) {
        return val instanceof Number n ? n.intValue() : 0;
    }
}
