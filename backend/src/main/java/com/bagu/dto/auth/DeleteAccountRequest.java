package com.bagu.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "邮箱验证码注销账户请求")
public class DeleteAccountRequest {

    @Schema(description = "6 位邮箱注销验证码", example = "123456")
    @NotBlank(message = "请输入 6 位邮箱验证码")
    @Pattern(regexp = "^\\d{6}$", message = "请输入 6 位数字验证码")
    private String code;
}
