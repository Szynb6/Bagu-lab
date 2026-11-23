package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "邮箱密码登录请求")
public class LoginRequest {

    @Schema(description = "登录邮箱", example = "developer@163.com")
    @NotBlank(message = "请输入邮箱地址")
    @Email(message = "请输入有效的邮箱地址")
    @Size(max = 120, message = "邮箱长度不能超过 120 个字符")
    private String email;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6~64 位之间")
    private String password;

    @Schema(description = "滑块拼图验证通过后签发的一次性通行票据")
    @NotBlank(message = "请先完成滑块拼图验证")
    private String captchaToken;
}
