package com.bagu.service;

import com.bagu.common.PageResult;
import com.bagu.dto.knowledge.ModuleMetaVO;
import com.bagu.dto.knowledge.QuestionDetailVO;
import com.bagu.dto.knowledge.QuestionItemVO;
import com.bagu.dto.knowledge.QuestionQuery;
import com.bagu.dto.knowledge.ReaderVO;

import java.util.List;

public interface KnowledgeService {

    List<ModuleMetaVO> listModules();

    PageResult<QuestionItemVO> pageQuestions(QuestionQuery query);

    List<Long> listQuestionIds(QuestionQuery query);

    QuestionDetailVO getQuestionDetail(Long id);

    ReaderVO getModuleReader(String slug, String priority);
}
