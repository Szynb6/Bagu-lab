package com.bagu.dto.auth;

import com.bagu.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "当前用户信息")
public class UserInfoVO {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Integer dailyGoal;

    public static UserInfoVO from(SysUser user) {
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .dailyGoal(user.getDailyGoal())
                .build();
    }
}
