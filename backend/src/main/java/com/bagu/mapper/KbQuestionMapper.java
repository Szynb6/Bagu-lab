package com.bagu.mapper;

import com.bagu.dto.knowledge.QuestionItemRow;
import com.bagu.entity.KbQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface KbQuestionMapper extends BaseMapper<KbQuestion> {

    IPage<QuestionItemRow> selectQuestionPage(
            IPage<QuestionItemRow> page,
            @Param("userId") Long userId,
            @Param("slug") String slug,
            @Param("chapterId") Long chapterId,
            @Param("priorities") List<String> priorities,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    List<Long> selectQuestionIds(
            @Param("userId") Long userId,
            @Param("slug") String slug,
            @Param("chapterId") Long chapterId,
            @Param("priorities") List<String> priorities,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    List<Map<String, Object>> selectModuleStats();

    List<Map<String, Object>> selectChapterQuestionCounts();
}
