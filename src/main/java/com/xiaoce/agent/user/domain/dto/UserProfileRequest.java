package com.xiaoce.agent.user.domain.dto;

import com.xiaoce.agent.auth.enums.GenderEnum;

import java.util.Optional;

/**
 * UserProfileRequest
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/25 20:02
 */
public record UserProfileRequest(
        Optional<String> nickname,
        Optional<String> avatarUrl,
        Optional<String> academicId,
        Optional<String> email,
        Optional<String> bio,
        Optional<GenderEnum> gender,
        Optional<String> school,
        // 更新用户的学术id和更新用户的邮箱时需要传入验证码
        Optional<String> validateCode
) {
        public static boolean UserProfileRequestIsEmpty(UserProfileRequest request){
                return request.academicId.isEmpty()&&
                        request.email.isEmpty()&&
                        request.nickname.isEmpty()&&
                        request.avatarUrl.isEmpty()&&
                        request.bio.isEmpty()&&
                        request.gender.isEmpty()&&
                        request.school.isEmpty();
                }
}
