package com.bagu.controller;

import com.bagu.common.PageResult;
import com.bagu.common.Result;
import com.bagu.dto.knowledge.QuestionDetailVO;
import com.bagu.dto.knowledge.QuestionItemVO;
import com.bagu.dto.knowledge.QuestionQuery;
import com.bagu.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "题库查询接口", description = "多维分页筛题、跨页 ID 队列获取与单题详情")
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "题库多维筛选列表（后端分页，不含 answerMd）")
    @GetMapping
    public Result<PageResult<QuestionItemVO>> pageQuestions(QuestionQuery query) {
        return Result.ok(knowledgeService.pageQuestions(query));
    }

    @Operation(summary = "获取筛选结果全量题目 ID 列表（支撑跨页一键开刷）")
    @GetMapping("/ids")
    public Result<List<Long>> listQuestionIds(QuestionQuery query) {
        return Result.ok(knowledgeService.listQuestionIds(query));
    }

    @Operation(summary = "获取单题完整详情（含答案 Markdown、图解、Hints 与个人进度）")
    @GetMapping("/{id}")
    public Result<QuestionDetailVO> detail(
            @Parameter(description = "题目主键 ID") @PathVariable Long id
    ) {
        return Result.ok(knowledgeService.getQuestionDetail(id));
    }
}
