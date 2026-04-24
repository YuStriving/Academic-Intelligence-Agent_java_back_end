package com.xiaoce.agent.auth.domain.vo;

import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.enums.GenderEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应类
 * 用于封装和返回用户相关的信息数据
 * 使用@Data注解自动生成getter、setter等方法
 * 使用@NoArgsConstructor和@AllArgsConstructor注解自动生成无参和全参构造方法
 */

public record UserInfoResponse(
         String id,           // 用户ID
         String username ,    // 用户名
         String academicId ,  // 学术id
         String email  ,     // 电子邮箱
         String nickname  ,  // 用户昵称
         String avatarUrl ,   // 头像链接
         String school ,      // 学校
         String bio ,        // 个人简介
         String gender ,   // 用户性别 0 男 ， 1 女
         String createdAt  // 创建时间

) {
    public static UserInfoResponse toMapUserInfoResponse(User user) {
        return new UserInfoResponse(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getAcademicId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getSchool(),
                user.getBio(),
                GenderEnum.getByCode(user.getGender()).getDesc(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );

    }

}
