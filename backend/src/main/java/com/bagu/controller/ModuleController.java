package com.bagu.controller;

import com.bagu.common.Result;
import com.bagu.dto.knowledge.ModuleMetaVO;
import com.bagu.dto.knowledge.ReaderVO;
import com.bagu.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "知识模块与通读接口", description = "十大模块篇目树与单模块沉浸通读")
@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "获取十大模块元信息与篇目树")
    @GetMapping
    public Result<List<ModuleMetaVO>> listModules() {
        return Result.ok(knowledgeService.listModules());
    }

    @Operation(summary = "获取单模块沉浸通读全量数据")
    @GetMapping("/{slug}/reader")
    public Result<ReaderVO> reader(
            @Parameter(description = "模块 slug，如 01-jvm") @PathVariable String slug,
            @Parameter(description = "可选考频过滤：P0 / P1 / P2") @RequestParam(required = false) String priority
    ) {
        return Result.ok(knowledgeService.getModuleReader(slug, priority));
    }
}
