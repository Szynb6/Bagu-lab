package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "通过邮箱验证码重置密码请求")
public class ResetPasswordRequest {

    @Schema(description = "已绑定的邮箱", example = "developer@163.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "请输入有效的邮箱地址")
    @Size(max = 120, message = "邮箱长度不能超过 120 个字符")
    private String email;

    @Schema(description = "6 位邮箱验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "请输入 6 位数字验证码")
    private String code;

    @Schema(description = "新密码（6~64 位）", example = "newPass123")
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在 6~64 位之间")
    private String newPassword;
}
