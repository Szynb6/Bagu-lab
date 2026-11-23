package com.bagu.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Data
@Schema(description = "题库多维筛选参数")
public class QuestionQuery {

    private static final Set<String> VALID_PRIORITIES = Set.of("P0", "P1", "P2");

    @Schema(description = "模块 slug，如 01-jvm")
    private String slug;

    @Schema(description = "篇目 ID")
    private Long chapterId;

    @Schema(description = "考频多选，逗号分隔，如 P0,P1")
    private String priorities;

    @Schema(description = "掌握状态：all / new / weak / reviewing / mastered / starred / noted", example = "all")
    private String status = "all";

    @Schema(description = "列表内题干或题号模糊过滤")
    private String keyword;

    @Schema(description = "页码（从 1 开始）", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数（1~100）", example = "20")
    private Integer size = 20;

    public List<String> parsedPriorities() {
        if (priorities == null || priorities.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(priorities.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(VALID_PRIORITIES::contains)
                .distinct()
                .toList();
    }

    public int normalizedPage() {
        return page == null || page < 1 ? 1 : page;
    }

    public int normalizedSize() {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    public String normalizedStatus() {
        return status == null || status.isBlank() ? "all" : status.trim().toLowerCase();
    }
}
