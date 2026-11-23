package com.bagu.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.bagu.common.BizException;
import com.bagu.common.ErrorCode;
import com.bagu.dto.auth.DeleteAccountRequest;
import com.bagu.dto.auth.LoginRequest;
import com.bagu.dto.auth.LoginResponse;
import com.bagu.dto.auth.RegisterRequest;
import com.bagu.dto.auth.ResetPasswordRequest;
import com.bagu.dto.auth.SendCodeRequest;
import com.bagu.dto.auth.UserInfoVO;
import com.bagu.entity.SysUser;
import com.bagu.mapper.SysUserMapper;
import com.bagu.service.AuthService;
import com.bagu.service.SliderCaptchaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter YMD_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String KEY_CODE_PREFIX = "bagu:auth:code:";
    private static final String KEY_TRIES_PREFIX = "bagu:auth:tries:";
    private static final String KEY_COOLDOWN_PREFIX = "bagu:auth:cooldown:";
    private static final String KEY_DAILY_IP_PREFIX = "bagu:auth:daily:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final int MAX_VERIFY_TRIES = 5;
    private static final long MAX_DAILY_SEND_PER_IP = 20;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender mailSender;
    private final SliderCaptchaService sliderCaptchaService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Override
    public LoginResponse login(LoginRequest request) {
        sliderCaptchaService.consumeCaptchaToken(request.getCaptchaToken());

        String email = normalizeEmail(request.getEmail());
        SysUser user = findByEmail(email);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱或密码错误");
        }

        return issueToken(user);
    }

    @Override
    public void sendEmailCode(SendCodeRequest request, String clientIp) {
        sliderCaptchaService.consumeCaptchaToken(request.getCaptchaToken());

        String email = normalizeEmail(request.getEmail());
        String scene = StringUtils.hasText(request.getScene()) ? request.getScene().trim() : "register";

        SysUser existing = findByEmail(email);
        if ("register".equals(scene) && existing != null) {
            throw new BizException(ErrorCode.CONFLICT, "该邮箱已注册账号，请直接登录");
        }
        if ("reset".equals(scene) && existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该邮箱尚未注册任何账号");
        }

        dispatchVerificationCode(email, scene, clientIp);
    }

    @Override
    public void sendDeleteAccountCode(String clientIp) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            StpUtil.logout();
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }
        if (!StringUtils.hasText(user.getEmail())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前账号未绑定电子邮箱，无法发送注销验证码");
        }

        String email = normalizeEmail(user.getEmail());
        dispatchVerificationCode(email, "delete", clientIp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(DeleteAccountRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            StpUtil.logout();
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }
        if (!StringUtils.hasText(user.getEmail())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前账号未绑定电子邮箱，无法校验注销验证码");
        }

        String email = normalizeEmail(user.getEmail());
        // 1. 校验并销毁 Redis 中的注销验证码
        verifyAndConsumeCode("delete", email, request.getCode().trim());

        // 2. 删除 sys_user 记录（PostgreSQL 外键 ON DELETE CASCADE 自动级联删除 study_progress 与 study_review_log）
        sysUserMapper.deleteById(userId);

        // 3. 清理该用户在 Redis 中的打卡 Bitmap、今日刷题计数与邮箱冷却锁
        purgeUserRedisData(userId, email);

        // 4. 强制注销该用户在所有设备（PC + 移动端）上的 Sa-Token 会话
        StpUtil.logout(userId);
        log.info("用户账户已永久注销并清理全部关联数据: userId={}, email={}", userId, email);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String username = request.getUsername().trim();

        // 1. 校验 Redis 验证码
        verifyAndConsumeCode("register", email, request.getCode().trim());

        // 2. 校验邮箱唯一性（email 为唯一登录凭证，username 保留用于右上角头像展示）
        if (findByEmail(email) != null) {
            throw new BizException(ErrorCode.CONFLICT, "该邮箱已注册账号，请直接登录");
        }

        // 3. 创建新用户
        SysUser newUser = new SysUser();
        newUser.setUsername(username);
        newUser.setNickname(username);
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setRole("USER");
        newUser.setDailyGoal(20);
        newUser.setCreatedAt(OffsetDateTime.now());
        newUser.setUpdatedAt(OffsetDateTime.now());

        sysUserMapper.insert(newUser);
        log.info("新用户注册成功: id={}, email={}, username={}", newUser.getId(), email, username);

        return issueToken(newUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        SysUser user = findByEmail(email);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该邮箱尚未注册任何账号");
        }

        verifyAndConsumeCode("reset", email, request.getCode().trim());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        sysUserMapper.updateById(user);
        log.info("用户通过邮箱验证码重置密码成功: id={}, email={}", user.getId(), email);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserInfoVO currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            StpUtil.logout();
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }
        return UserInfoVO.from(user);
    }

    private void dispatchVerificationCode(String email, String scene, String clientIp) {
        // 1. 60 秒发送冷却检查
        String cooldownKey = KEY_COOLDOWN_PREFIX + email;
        Long remainSec = stringRedisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (remainSec != null && remainSec > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "验证码发送过于频繁，请 " + remainSec + " 秒后再试");
        }

        // 2. 单 IP 每日上限检查
        String ipKey = KEY_DAILY_IP_PREFIX + (StringUtils.hasText(clientIp) ? clientIp : "unknown");
        Long dailyCount = stringRedisTemplate.opsForValue().increment(ipKey);
        if (dailyCount != null && dailyCount == 1L) {
            stringRedisTemplate.expire(ipKey, Duration.ofHours(24));
        }
        if (dailyCount != null && dailyCount > MAX_DAILY_SEND_PER_IP) {
            throw new BizException(ErrorCode.BAD_REQUEST, "今日验证码请求次数已达上限，请明日再试");
        }

        // 3. 生成 6 位数字验证码并写入 Redis (5 分钟有效)
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        String codeKey = buildCodeKey(scene, email);
        String triesKey = buildTriesKey(scene, email);

        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
        stringRedisTemplate.delete(triesKey);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_TTL);

        // 4. 发送 163 邮件（若本地未配置发件账号则打印日志降级）
        if (!StringUtils.hasText(mailUsername)) {
            if ("prod".equalsIgnoreCase(activeProfile)) {
                stringRedisTemplate.delete(cooldownKey);
                throw new BizException(ErrorCode.INTERNAL_ERROR, "服务端尚未配置 163 发件邮箱 (MAIL_USERNAME / MAIL_PASSWORD)");
            }
            log.warn("================================================================");
            log.warn("[本地开发模式] 未配置 MAIL_USERNAME，邮箱 {} 的 [{}] 验证码为: {}", email, scene, code);
            log.warn("================================================================");
            return;
        }

        try {
            sendHtmlVerificationMail(email, code, scene);
            log.info("已向邮箱 {} 发送 [{}] 验证码邮件", email, scene);
        } catch (Exception e) {
            stringRedisTemplate.delete(cooldownKey);
            stringRedisTemplate.delete(codeKey);
            log.error("发送验证码邮件至 {} 失败", email, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "验证码邮件发送失败，请检查邮箱地址或稍后重试");
        }
    }

    private void purgeUserRedisData(long userId, String email) {
        try {
            List<String> keysToDelete = new ArrayList<>();
            LocalDate today = LocalDate.now(SHANGHAI);
            YearMonth currentYm = YearMonth.from(today);
            for (int i = 0; i <= 12; i++) {
                keysToDelete.add("bagu:checkin:" + userId + ":" + currentYm.minusMonths(i).format(YM_FMT));
            }
            for (int i = 0; i <= 2; i++) {
                keysToDelete.add("bagu:daily:cnt:" + userId + ":" + today.minusDays(i).format(YMD_FMT));
            }
            keysToDelete.add(KEY_COOLDOWN_PREFIX + email);
            for (String sc : List.of("register", "reset", "delete")) {
                keysToDelete.add(buildCodeKey(sc, email));
                keysToDelete.add(buildTriesKey(sc, email));
            }
            stringRedisTemplate.delete(keysToDelete);
        } catch (Exception e) {
            log.warn("清理注销用户 Redis 缓存键时发生非致命异常 userId={}", userId, e);
        }
    }

    private LoginResponse issueToken(SysUser user) {
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return LoginResponse.builder()
                .tokenName(tokenInfo.getTokenName())
                .tokenValue(tokenInfo.getTokenValue())
                .user(UserInfoVO.from(user))
                .build();
    }

    private SysUser findByEmail(String normalizedEmail) {
        return sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .apply("LOWER(email) = {0}", normalizedEmail)
                        .last("LIMIT 1"));
    }

    private void verifyAndConsumeCode(String scene, String email, String inputCode) {
        String codeKey = buildCodeKey(scene, email);
        String savedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(savedCode)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "验证码已过期或尚未获取，请重新发送");
        }

        if (!savedCode.equals(inputCode)) {
            String triesKey = buildTriesKey(scene, email);
            Long tries = stringRedisTemplate.opsForValue().increment(triesKey);
            if (tries != null && tries == 1L) {
                stringRedisTemplate.expire(triesKey, CODE_TTL);
            }
            if (tries != null && tries >= MAX_VERIFY_TRIES) {
                stringRedisTemplate.delete(codeKey);
                stringRedisTemplate.delete(triesKey);
                throw new BizException(ErrorCode.BAD_REQUEST, "验证码错误次数过多已失效，请重新获取");
            }
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱验证码不正确");
        }

        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(buildTriesKey(scene, email));
    }

    private void sendHtmlVerificationMail(String toEmail, String code, String scene) throws Exception {
        String actionTitle = switch (scene) {
            case "reset" -> "重置登录密码";
            case "delete" -> "永久注销并删除学习账户";
            default -> "注册学习工作台账号";
        };
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(mailUsername, "Bagu Lab 工程记忆手稿", "UTF-8"));
        helper.setTo(toEmail);
        helper.setSubject("【Bagu Lab】" + code + " 是您的邮箱验证码");

        String html = """
                <div style="background-color:#F7F6F2;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:#161614;">
                  <div style="max-width:480px;margin:0 auto;background-color:#FFFFFF;border:1px solid #E5E2DA;border-radius:12px;padding:28px 32px;">
                    <div style="border-bottom:1px solid #E5E2DA;padding-bottom:16px;margin-bottom:20px;">
                      <span style="display:inline-block;background-color:#C84B31;color:#FFFFFF;font-weight:700;font-size:12px;padding:3px 8px;border-radius:4px;letter-spacing:0.5px;">BAGU LAB</span>
                      <span style="margin-left:8px;font-size:13px;color:#5E5A52;">Java 后端工程记忆手稿</span>
                    </div>
                    <p style="font-size:15px;margin:0 0 12px;">您好，您正在申请<strong>%s</strong>。</p>
                    <p style="font-size:13px;color:#5E5A52;margin:0 0 18px;">请在 5 分钟内输入以下 6 位数字验证码完成验证：</p>
                    <div style="background-color:#F4F2EC;border:1px solid #D4D0C5;border-radius:8px;padding:16px;text-align:center;margin-bottom:20px;">
                      <span style="font-family:'JetBrains Mono',Consolas,monospace;font-size:28px;font-weight:700;letter-spacing:8px;color:#C84B31;">%s</span>
                    </div>
                    <p style="font-size:12px;color:#736E64;line-height:1.6;margin:0;">
                      如非本人操作，请忽略本邮件，您的账号安全不会受到影响。
                    </p>
                  </div>
                </div>
                """.formatted(actionTitle, code);

        helper.setText(html, true);
        mailSender.send(message);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String buildCodeKey(String scene, String email) {
        return KEY_CODE_PREFIX + scene + ":" + email;
    }

    private String buildTriesKey(String scene, String email) {
        return KEY_TRIES_PREFIX + scene + ":" + email;
    }
}
