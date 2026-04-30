package com.xiaoce.agent.user.domain.vo;

import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.enums.GenderEnum;

import java.time.LocalDateTime;

/**
 * UserProfileResponse
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/25 20:08
 */
public record UserProfileResponse(
        String nickname,
        String avatarUrl,
        String academicId,
        String email,
        String bio,
        String gender,
        String school,
        LocalDateTime academicIdLastModified  // 上次修改学术ID的时间，便于前端显示“一年内可修改”
) {
    public static UserProfileResponse toUserProfileResponse(User user , LocalDateTime date) {
        return new UserProfileResponse(
                user.getNickname(),
                user.getAvatarUrl(),
                user.getAcademicId(),
                user.getEmail(),
                user.getBio(),
                GenderEnum.getByCode(user.getGender()).getDesc(),
                user.getSchool(),
                date
        );
    }
}
