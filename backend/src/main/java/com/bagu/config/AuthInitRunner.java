package com.bagu.config;

import com.bagu.entity.SysUser;
import com.bagu.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时若 sys_user 为空，自动创建一个初始账号（BCrypt 加密），无需开放注册接口即可登录使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInitRunner implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${bagu.auth.init-username:admin}")
    private String initUsername;

    @Value("${bagu.auth.init-password:123456}")
    private String initPassword;

    @Value("${bagu.auth.init-nickname:博主}")
    private String initNickname;

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(initUsername);
        user.setPasswordHash(passwordEncoder.encode(initPassword));
        user.setNickname(initNickname);
        user.setRole("ADMIN");
        user.setDailyGoal(20);
        sysUserMapper.insert(user);
        log.info("已自动初始化默认账号: username={}, role=ADMIN（可在 application-local.yml 中自定义或登录后修改）", initUsername);
    }
}
