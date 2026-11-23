package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "Token 请求头名称", example = "satoken")
    private String tokenName;

    @Schema(description = "Token 值")
    private String tokenValue;

    @Schema(description = "当前用户信息")
    private UserInfoVO user;
}
