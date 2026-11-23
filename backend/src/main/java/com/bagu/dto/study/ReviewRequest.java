package com.bagu.dto.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "提交单题自评打分请求")
public class ReviewRequest {

    @Schema(description = "题目主键 ID", example = "1")
    @NotNull(message = "questionId 不能为空")
    private Long questionId;

    @Schema(description = "自评打分：1不会 2模糊 3掌握 4秒杀", example = "3")
    @NotNull(message = "rating 不能为空")
    @Min(value = 1, message = "rating 必须在 1~4 之间")
    @Max(value = 4, message = "rating 必须在 1~4 之间")
    private Integer rating;

    @Schema(description = "本题思考耗时（秒）", example = "35")
    @Min(value = 0, message = "durationSec 不能为负数")
    private Integer durationSec = 0;
}
