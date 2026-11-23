package com.bagu.controller;

import com.bagu.common.Result;
import com.bagu.dto.knowledge.QuestionDetailVO;
import com.bagu.dto.study.DashboardVO;
import com.bagu.dto.study.NoteRequest;
import com.bagu.dto.study.QueueQuery;
import com.bagu.dto.study.ReviewRequest;
import com.bagu.dto.study.ReviewResultVO;
import com.bagu.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "学习与 SM-2 调度接口", description = "复习看板统计、7 种刷题队列、SM-2 自评打分、收藏与个人助记")
@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @Operation(summary = "获取复习看板聚合数据（今日待复习、热力图、连续打卡、9 模块掌握度）")
    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.ok(studyService.getDashboard());
    }

    @Operation(summary = "获取刷题队列（支持 today / p0 / mistakes / starred / module / custom / single）")
    @GetMapping("/queue")
    public Result<List<QuestionDetailVO>> queue(QueueQuery query) {
        return Result.ok(studyService.getQueue(query));
    }

    @Operation(summary = "提交单题自评打分（1不会 2模糊 3掌握 4秒杀），推进 SM-2 状态并打卡")
    @PostMapping("/review")
    public Result<ReviewResultVO> review(@Valid @RequestBody ReviewRequest request) {
        return Result.ok(studyService.review(request));
    }

    @Operation(summary = "切换题目收藏状态（返回切换后的最新 isStarred）")
    @PatchMapping("/questions/{id}/star")
    public Result<Boolean> toggleStar(
            @Parameter(description = "题目主键 ID") @PathVariable Long id
    ) {
        return Result.ok(studyService.toggleStar(id));
    }

    @Operation(summary = "保存或清空个人口述助记备注")
    @PutMapping("/questions/{id}/note")
    public Result<Void> saveNote(
            @Parameter(description = "题目主键 ID") @PathVariable Long id,
            @Valid @RequestBody NoteRequest request
    ) {
        studyService.saveNote(id, request.getNote());
        return Result.ok();
    }
}
