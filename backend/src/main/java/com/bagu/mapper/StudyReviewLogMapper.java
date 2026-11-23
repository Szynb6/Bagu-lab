package com.bagu.mapper;

import com.bagu.entity.StudyReviewLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface StudyReviewLogMapper extends BaseMapper<StudyReviewLog> {

    List<Map<String, Object>> selectDailyCounts(
            @Param("userId") Long userId,
            @Param("startTime") OffsetDateTime startTime
    );

    int countTodayFirstLearned(
            @Param("userId") Long userId,
            @Param("dayStart") OffsetDateTime dayStart
    );
}
