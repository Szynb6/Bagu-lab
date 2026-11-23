package com.bagu.mapper;

import com.bagu.entity.StudyProgress;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface StudyProgressMapper extends BaseMapper<StudyProgress> {

    List<Map<String, Object>> selectUserModuleStats(
            @Param("userId") Long userId,
            @Param("endOfToday") OffsetDateTime endOfToday
    );

    List<Long> selectDueQuestionIds(
            @Param("userId") Long userId,
            @Param("endOfToday") OffsetDateTime endOfToday
    );

    List<Long> selectNewQuestionIdsForToday(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    List<Long> selectP0UnmasteredQuestionIds(@Param("userId") Long userId);

    List<Long> selectMistakeQuestionIds(@Param("userId") Long userId);

    List<Long> selectStarredQuestionIds(@Param("userId") Long userId);
}
