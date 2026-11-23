package com.bagu.service;

import cn.dev33.satoken.stp.StpUtil;
import com.bagu.common.BizException;
import com.bagu.common.ErrorCode;
import com.bagu.dto.knowledge.ModuleMetaVO;
import com.bagu.dto.knowledge.ProgressVO;
import com.bagu.dto.knowledge.QuestionDetailVO;
import com.bagu.dto.knowledge.QuestionImageVO;
import com.bagu.dto.study.DashboardVO;
import com.bagu.dto.study.HeatmapItemVO;
import com.bagu.dto.study.ModuleStatVO;
import com.bagu.dto.study.QueueQuery;
import com.bagu.dto.study.ReviewRequest;
import com.bagu.dto.study.ReviewResultVO;
import com.bagu.entity.KbQuestion;
import com.bagu.entity.StudyProgress;
import com.bagu.entity.StudyReviewLog;
import com.bagu.entity.SysUser;
import com.bagu.mapper.KbQuestionMapper;
import com.bagu.mapper.StudyProgressMapper;
import com.bagu.mapper.StudyReviewLogMapper;
import com.bagu.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter YMD_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DAILY_NEW_QUOTA = 10;

    private final KnowledgeService knowledgeService;
    private final Sm2Scheduler scheduler;
    private final KbQuestionMapper questionMapper;
    private final StudyProgressMapper progressMapper;
    private final StudyReviewLogMapper reviewLogMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public DashboardVO getDashboard() {
        long uid = StpUtil.getLoginIdAsLong();
        LocalDate today = LocalDate.now(SHANGHAI);
        OffsetDateTime startOfDay = today.atStartOfDay(SHANGHAI).toOffsetDateTime();
        OffsetDateTime endOfDay = today.atTime(LocalTime.MAX).atZone(SHANGHAI).toOffsetDateTime();

        SysUser user = userMapper.selectById(uid);
        int dailyGoal = (user != null && user.getDailyGoal() != null) ? user.getDailyGoal() : 20;

        List<ModuleMetaVO> mods = knowledgeService.listModules();
        Map<Long, Map<String, Object>> modStatMap = progressMapper.selectUserModuleStats(uid, endOfDay).stream()
                .collect(Collectors.toMap(r -> asLong(r.get("module_id")), Function.identity()));

        int totalQ = 0, p0Q = 0, mastered = 0, learned = 0, p0Mastered = 0, due = 0, mistakes = 0;
        List<ModuleStatVO> moduleVos = new ArrayList<>(mods.size());
        for (ModuleMetaVO m : mods) {
            Map<String, Object> row = modStatMap.getOrDefault(m.getId(), Collections.emptyMap());
            int mMastered = asInt(row.get("mastered"));
            int mP0 = asInt(row.get("p0_mastered"));
            int mLearned = asInt(row.get("learned"));
            int mDue = asInt(row.get("due"));

            totalQ += m.getQuestionCount();
            p0Q += m.getP0Count();
            mastered += mMastered;
            learned += mLearned;
            p0Mastered += mP0;
            due += mDue;
            mistakes += asInt(row.get("mistakes"));

            moduleVos.add(ModuleStatVO.from(m, mMastered, mP0, mLearned, mDue));
        }

        int unlearned = Math.max(0, totalQ - learned);
        int firstLearnedToday = reviewLogMapper.countTodayFirstLearned(uid, startOfDay);
        int newQuota = Math.min(unlearned, Math.max(0, DAILY_NEW_QUOTA - firstLearnedToday));

        OffsetDateTime heatStart = today.minusDays(90).atStartOfDay(SHANGHAI).toOffsetDateTime();
        Map<String, Integer> countByDay = reviewLogMapper.selectDailyCounts(uid, heatStart).stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.get("day_str")), r -> asInt(r.get("cnt"))));

        List<HeatmapItemVO> heatmap = new ArrayList<>(91);
        for (int offset = 90; offset >= 0; offset--) {
            String dayStr = today.minusDays(offset).toString();
            heatmap.add(new HeatmapItemVO(dayStr, countByDay.getOrDefault(dayStr, 0)));
        }

        int doneToday = fetchDoneToday(uid, today, countByDay.getOrDefault(today.toString(), 0));
        int checkinDays = countMonthBits(uid, YearMonth.from(today));
        int streak = computeStreak(uid, today);

        return DashboardVO.builder()
                .dueCount(due)
                .newQuota(newQuota)
                .doneToday(doneToday)
                .dailyGoal(dailyGoal)
                .totalQuestions(totalQ)
                .totalMastered(mastered)
                .totalLearned(learned)
                .p0Total(p0Q)
                .p0Mastered(p0Mastered)
                .streak(streak)
                .checkinDays(checkinDays)
                .heatmap(heatmap)
                .mistakeCount(mistakes)
                .modules(moduleVos)
                .build();
    }

    public List<QuestionDetailVO> getQueue(QueueQuery query) {
        long uid = StpUtil.getLoginIdAsLong();
        String mode = query.getMode() == null ? "today" : query.getMode().trim().toLowerCase();

        List<Long> ids;
        switch (mode) {
            case "today" -> {
                LocalDate today = LocalDate.now(SHANGHAI);
                OffsetDateTime startOfDay = today.atStartOfDay(SHANGHAI).toOffsetDateTime();
                OffsetDateTime endOfDay = today.atTime(LocalTime.MAX).atZone(SHANGHAI).toOffsetDateTime();
                Set<Long> merged = new LinkedHashSet<>(progressMapper.selectDueQuestionIds(uid, endOfDay));
                int quota = Math.max(0, DAILY_NEW_QUOTA - reviewLogMapper.countTodayFirstLearned(uid, startOfDay));
                if (quota > 0) {
                    merged.addAll(progressMapper.selectNewQuestionIdsForToday(uid, quota));
                }
                ids = new ArrayList<>(merged);
            }
            case "p0" -> ids = progressMapper.selectP0UnmasteredQuestionIds(uid);
            case "mistakes" -> ids = progressMapper.selectMistakeQuestionIds(uid);
            case "starred" -> ids = progressMapper.selectStarredQuestionIds(uid);
            case "module" -> {
                if (query.getSlug() == null || query.getSlug().isBlank()) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "mode=module 时必须传入 slug");
                }
                ModuleMetaVO mod = knowledgeService.listModules().stream()
                        .filter(m -> m.getSlug().equals(query.getSlug().trim()))
                        .findFirst()
                        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "模块不存在: " + query.getSlug()));
                LambdaQueryWrapper<KbQuestion> qw = new LambdaQueryWrapper<KbQuestion>()
                        .select(KbQuestion::getId)
                        .eq(KbQuestion::getModuleId, mod.getId())
                        .eq(KbQuestion::getIsDeleted, false)
                        .orderByAsc(KbQuestion::getSortOrder, KbQuestion::getId);
                if (query.getChapterId() != null) {
                    qw.eq(KbQuestion::getChapterId, query.getChapterId());
                }
                ids = questionMapper.selectList(qw).stream().map(KbQuestion::getId).toList();
            }
            case "custom" -> ids = query.parsedIds();
            case "single" -> {
                if (query.getId() == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "mode=single 时必须传入 id");
                }
                return List.of(knowledgeService.getQuestionDetail(query.getId()));
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "无效的队列模式: " + mode);
        }

        return assembleDetailsInOrder(uid, ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReviewResultVO review(ReviewRequest req) {
        long uid = StpUtil.getLoginIdAsLong();
        Long qid = req.getQuestionId();
        verifyQuestionExists(qid);

        OffsetDateTime now = OffsetDateTime.now(SHANGHAI);
        StudyProgress sp = loadOrCreate(uid, qid);
        int prevIv = sp.getIntervalDays() == null ? 0 : sp.getIntervalDays();

        scheduler.applyRating(sp, req.getRating(), now);
        if (sp.getId() == null) {
            progressMapper.insert(sp);
        } else {
            progressMapper.updateById(sp);
        }

        StudyReviewLog logRow = new StudyReviewLog();
        logRow.setUserId(uid);
        logRow.setQuestionId(qid);
        logRow.setRating(req.getRating().shortValue());
        logRow.setDurationSec(req.getDurationSec() == null ? 0 : req.getDurationSec());
        logRow.setPrevInterval(prevIv);
        logRow.setNextInterval(sp.getIntervalDays());
        logRow.setReviewedAt(now);
        reviewLogMapper.insert(logRow);

        int doneToday = markCheckinAndIncr(uid, now.toLocalDate());
        return ReviewResultVO.builder()
                .progress(ProgressVO.from(sp, qid))
                .doneToday(doneToday)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleStar(Long questionId) {
        long uid = StpUtil.getLoginIdAsLong();
        verifyQuestionExists(questionId);

        StudyProgress sp = loadOrCreate(uid, questionId);
        boolean next = !Boolean.TRUE.equals(sp.getIsStarred());
        sp.setIsStarred(next);
        sp.setUpdatedAt(OffsetDateTime.now(SHANGHAI));
        saveProgress(sp);
        return next;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveNote(Long questionId, String note) {
        long uid = StpUtil.getLoginIdAsLong();
        verifyQuestionExists(questionId);

        StudyProgress sp = loadOrCreate(uid, questionId);
        sp.setPersonalNote(note == null ? "" : note);
        sp.setUpdatedAt(OffsetDateTime.now(SHANGHAI));
        saveProgress(sp);
    }

    private List<QuestionDetailVO> assembleDetailsInOrder(long uid, List<Long> orderedIds) {
        if (orderedIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<KbQuestion> rows = questionMapper.selectList(
                new LambdaQueryWrapper<KbQuestion>()
                        .in(KbQuestion::getId, orderedIds)
                        .eq(KbQuestion::getIsDeleted, false));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ModuleMetaVO> modMap = knowledgeService.listModules().stream()
                .collect(Collectors.toMap(ModuleMetaVO::getId, Function.identity()));
        Map<Long, KbQuestion> qMap = rows.stream()
                .collect(Collectors.toMap(KbQuestion::getId, Function.identity()));
        Map<Long, StudyProgress> pMap = progressMapper.selectList(
                        new LambdaQueryWrapper<StudyProgress>()
                                .eq(StudyProgress::getUserId, uid)
                                .in(StudyProgress::getQuestionId, orderedIds))
                .stream()
                .collect(Collectors.toMap(StudyProgress::getQuestionId, Function.identity()));

        return orderedIds.stream()
                .map(qMap::get)
                .filter(Objects::nonNull)
                .map(q -> {
                    ModuleMetaVO mod = modMap.get(q.getModuleId());
                    List<String> hints = parseList(q.getHintsJson(), new TypeReference<>() {});
                    List<QuestionImageVO> images = parseList(q.getImagesJson(), new TypeReference<>() {});
                    return QuestionDetailVO.builder()
                            .id(q.getId())
                            .moduleSlug(mod != null ? mod.getSlug() : "")
                            .moduleTitle(mod != null ? mod.getTitle() : "")
                            .chapterId(q.getChapterId())
                            .code(q.getCode())
                            .priority(q.getPriority())
                            .title(q.getTitle())
                            .hints(hints)
                            .answerMd(q.getAnswerMd())
                            .images(images)
                            .hasTable(Boolean.TRUE.equals(q.getHasTable()))
                            .hasCode(Boolean.TRUE.equals(q.getHasCode()))
                            .animationKey(q.getAnimationKey())
                            .order(q.getSortOrder())
                            .progress(ProgressVO.from(pMap.get(q.getId()), q.getId()))
                            .build();
                })
                .toList();
    }

    private void verifyQuestionExists(Long qid) {
        Long cnt = questionMapper.selectCount(new LambdaQueryWrapper<KbQuestion>()
                .eq(KbQuestion::getId, qid)
                .eq(KbQuestion::getIsDeleted, false));
        if (cnt == null || cnt == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + qid);
        }
    }

    private StudyProgress loadOrCreate(long uid, Long qid) {
        StudyProgress sp = progressMapper.selectOne(new LambdaQueryWrapper<StudyProgress>()
                .eq(StudyProgress::getUserId, uid)
                .eq(StudyProgress::getQuestionId, qid));
        if (sp != null) {
            return sp;
        }
        StudyProgress created = new StudyProgress();
        created.setUserId(uid);
        created.setQuestionId(qid);
        created.setStatus((short) 0);
        created.setEaseFactor(new BigDecimal("2.50"));
        created.setIntervalDays(0);
        created.setRepetitions(0);
        created.setLapseCount(0);
        created.setIsStarred(false);
        created.setPersonalNote("");
        return created;
    }

    private void saveProgress(StudyProgress sp) {
        if (sp.getId() == null) {
            progressMapper.insert(sp);
        } else {
            progressMapper.updateById(sp);
        }
    }

    private String bitmapKey(long uid, YearMonth ym) {
        return "bagu:checkin:" + uid + ":" + ym.format(YM_FMT);
    }

    private String dailyKey(long uid, LocalDate d) {
        return "bagu:daily:cnt:" + uid + ":" + d.format(YMD_FMT);
    }

    private int markCheckinAndIncr(long uid, LocalDate today) {
        try {
            redis.opsForValue().setBit(bitmapKey(uid, YearMonth.from(today)), today.getDayOfMonth() - 1L, true);
            String dk = dailyKey(uid, today);
            Long c = redis.opsForValue().increment(dk);
            redis.expire(dk, Duration.ofHours(48));
            return c == null ? 1 : c.intValue();
        } catch (Exception e) {
            log.warn("Redis 打卡写入失败 uid={}", uid, e);
            return 1;
        }
    }

    private int fetchDoneToday(long uid, LocalDate today, int fallback) {
        try {
            String dk = dailyKey(uid, today);
            String val = redis.opsForValue().get(dk);
            if (val != null) {
                return Integer.parseInt(val);
            }
            if (fallback > 0) {
                redis.opsForValue().set(dk, String.valueOf(fallback), Duration.ofHours(48));
            }
            return fallback;
        } catch (Exception e) {
            log.warn("读取今日刷题数失败 uid={}", uid, e);
            return fallback;
        }
    }

    private int countMonthBits(long uid, YearMonth ym) {
        try {
            byte[] key = bitmapKey(uid, ym).getBytes(StandardCharsets.UTF_8);
            Long cnt = redis.execute((RedisCallback<Long>) conn -> conn.stringCommands().bitCount(key));
            return cnt == null ? 0 : cnt.intValue();
        } catch (Exception e) {
            log.warn("读取月度 Bitmap 失败 uid={}", uid, e);
            return 0;
        }
    }

    private int computeStreak(long uid, LocalDate today) {
        try {
            Map<YearMonth, byte[]> cache = new HashMap<>();
            LocalDate day = hasBit(uid, today, cache) ? today : today.minusDays(1);
            int streak = 0;
            for (int i = 0; i < 365; i++) {
                if (hasBit(uid, day, cache)) {
                    streak++;
                    day = day.minusDays(1);
                } else {
                    break;
                }
            }
            return streak;
        } catch (Exception e) {
            log.warn("计算连续打卡天数失败 uid={}", uid, e);
            return 0;
        }
    }

    private boolean hasBit(long uid, LocalDate d, Map<YearMonth, byte[]> cache) {
        YearMonth ym = YearMonth.from(d);
        byte[] raw = cache.computeIfAbsent(ym, k -> {
            byte[] key = bitmapKey(uid, k).getBytes(StandardCharsets.UTF_8);
            byte[] data = redis.execute((RedisCallback<byte[]>) conn -> conn.stringCommands().get(key));
            return data == null ? new byte[0] : data;
        });
        int bitIdx = d.getDayOfMonth() - 1;
        int byteIdx = bitIdx / 8;
        if (byteIdx >= raw.length) {
            return false;
        }
        return (raw[byteIdx] & (1 << (7 - (bitIdx % 8)))) != 0;
    }

    private <T> List<T> parseList(String raw, TypeReference<List<T>> ref) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return json.readValue(raw, ref);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
