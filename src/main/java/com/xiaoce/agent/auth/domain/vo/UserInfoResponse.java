package com.xiaoce.agent.auth.domain.vo;

import com.xiaoce.agent.auth.enums.GenderEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应类
 * 用于封装和返回用户相关的信息数据
 * 使用@Data注解自动生成getter、setter等方法
 * 使用@NoArgsConstructor和@AllArgsConstructor注解自动生成无参和全参构造方法
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private String id;           // 用户ID
    private String username;     // 用户名
    private String academicId;   // 学术id
    private String email;        // 电子邮箱
    private String nickname;     // 用户昵称
    private String avatarUrl;    // 头像链接
    private String school;       // 所属学校
    private String bio;         // 个人简介
    private GenderEnum gender;     // 用户性别 0 男 ， 1 女
    private String createdAt;   // 创建时间


}
