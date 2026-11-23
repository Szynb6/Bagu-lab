package com.bagu.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.bagu.common.BizException;
import com.bagu.common.ErrorCode;
import com.bagu.dto.auth.LoginRequest;
import com.bagu.dto.auth.LoginResponse;
import com.bagu.dto.auth.UserInfoVO;
import com.bagu.entity.SysUser;
import com.bagu.mapper.SysUserMapper;
import com.bagu.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        return LoginResponse.builder()
                .tokenName(tokenInfo.getTokenName())
                .tokenValue(tokenInfo.getTokenValue())
                .user(UserInfoVO.from(user))
                .build();
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
}
