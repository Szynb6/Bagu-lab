package com.bagu.dto.study;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
@Schema(description = "刷题队列查询参数")
public class QueueQuery {

    @Schema(description = "模式：today / p0 / mistakes / starred / module / custom / single", example = "today")
    private String mode = "today";

    @Schema(description = "模块 slug（mode=module 时使用）")
    private String slug;

    @Schema(description = "篇目 ID（mode=module 时可选）")
    private Long chapterId;

    @Schema(description = "逗号分隔的题目 ID 列表（mode=custom 时使用）")
    private String ids;

    @Schema(description = "单题 ID（mode=single 时使用）")
    private Long id;

    public List<Long> parsedIds() {
        if (ids == null || ids.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .distinct()
                .toList();
    }
}
