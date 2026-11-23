package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "滑块拼图校验请求")
public class SliderVerifyRequest {

    @Schema(description = "验证码唯一标识")
    @NotBlank(message = "验证码标识不能为空")
    private String captchaId;

    @Schema(description = "前端滑块最终水平偏移量 (基于 310px 标准画布宽度)")
    @NotNull(message = "滑块坐标不能为空")
    private Integer moveX;

    @Schema(description = "拖动总耗时 (毫秒)")
    @NotNull(message = "滑动耗时不能为空")
    private Long durationMs;
}
