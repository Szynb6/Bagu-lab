package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "滑块拼图校验结果")
public class SliderVerifyResponse {

    @Schema(description = "是否校验通过")
    private boolean passed;

    @Schema(description = "一次性人机验证通行票据（登录或发送邮箱验证码时携带）")
    private String captchaToken;
}
