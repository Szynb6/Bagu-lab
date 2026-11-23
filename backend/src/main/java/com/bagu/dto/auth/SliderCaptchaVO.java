package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "滑块拼图验证码图形数据")
public class SliderCaptchaVO {

    @Schema(description = "验证码唯一标识")
    private String captchaId;

    @Schema(description = "背景底图 (data:image/png;base64,...)")
    private String backgroundImage;

    @Schema(description = "拼图滑块图 (data:image/png;base64,...)")
    private String blockImage;

    @Schema(description = "拼图块纵向偏移 Y 坐标 (px)")
    private int blockY;

    @Schema(description = "底图标准宽度 (px)", example = "310")
    private int canvasWidth;

    @Schema(description = "底图标准高度 (px)", example = "150")
    private int canvasHeight;
}
