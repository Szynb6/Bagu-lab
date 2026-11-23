package com.bagu.controller;

import com.bagu.common.Result;
import com.bagu.dto.auth.DeleteAccountRequest;
import com.bagu.dto.auth.LoginRequest;
import com.bagu.dto.auth.LoginResponse;
import com.bagu.dto.auth.RegisterRequest;
import com.bagu.dto.auth.ResetPasswordRequest;
import com.bagu.dto.auth.SendCodeRequest;
import com.bagu.dto.auth.SliderCaptchaVO;
import com.bagu.dto.auth.SliderVerifyRequest;
import com.bagu.dto.auth.SliderVerifyResponse;
import com.bagu.dto.auth.UserInfoVO;
import com.bagu.service.AuthService;
import com.bagu.service.SliderCaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证接口", description = "邮箱密码登录、滑块拼图验证、163 邮箱验证码注册、密码找回与账户注销（Sa-Token）")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SliderCaptchaService sliderCaptchaService;

    @Operation(summary = "获取滑块拼图验证码")
    @GetMapping("/captcha/slider")
    public Result<SliderCaptchaVO> getSliderCaptcha() {
        return Result.ok(sliderCaptchaService.generate());
    }

    @Operation(summary = "校验滑块拼图位置并签发一次性通行票据")
    @PostMapping("/captcha/verify")
    public Result<SliderVerifyResponse> verifySliderCaptcha(@Valid @RequestBody SliderVerifyRequest request) {
        return Result.ok(sliderCaptchaService.verify(request));
    }

    @Operation(summary = "邮箱密码登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "发送 163 邮箱验证码（支持注册与找回密码）")
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        authService.sendEmailCode(request, resolveClientIp(servletRequest));
        return Result.ok();
    }

    @Operation(summary = "向当前已登录用户的绑定邮箱发送账户注销验证码")
    @PostMapping("/send-delete-code")
    public Result<Void> sendDeleteCode(HttpServletRequest servletRequest) {
        authService.sendDeleteAccountCode(resolveClientIp(servletRequest));
        return Result.ok();
    }

    @Operation(summary = "校验邮箱验证码并永久注销当前账户（级联清理全部学习数据）")
    @PostMapping("/delete-account")
    public Result<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(request);
        return Result.ok();
    }

    @Operation(summary = "邮箱验证码注册并自动登录")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @Operation(summary = "通过邮箱验证码重置密码")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.currentUser());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
