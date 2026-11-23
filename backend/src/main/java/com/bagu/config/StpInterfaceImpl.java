package com.bagu.config;

import cn.dev33.satoken.stp.StpInterface;
import com.bagu.entity.SysUser;
import com.bagu.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper sysUserMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = sysUserMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null || user.getRole() == null) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }
}
