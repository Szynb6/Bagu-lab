package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发送邮箱验证码请求")
public class SendCodeRequest {

    @Schema(description = "接收验证码的邮箱", example = "developer@163.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "请输入有效的邮箱地址")
    @Size(max = 120, message = "邮箱长度不能超过 120 个字符")
    private String email;

    @Schema(description = "验证码场景：register（注册）或 reset（重置密码）", example = "register")
    @Pattern(regexp = "^(register|reset)$", message = "验证码场景仅支持 register 或 reset")
    private String scene = "register";

    @Schema(description = "滑块拼图验证通过后签发的一次性通行票据")
    @NotBlank(message = "请先完成滑块拼图验证")
    private String captchaToken;
}
