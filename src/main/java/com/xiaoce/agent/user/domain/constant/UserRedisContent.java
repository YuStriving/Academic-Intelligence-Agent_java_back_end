package com.xiaoce.agent.user.domain.constant;

import com.xiaoce.agent.auth.mapper.UsersMapper;

/**
 * UserRedisContent
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/25 20:32
 */
public class UserRedisContent {
    public UserRedisContent(){

    }

    public static final String USER_PROFILE_ACADEMIC_ID_LAST_UPDATE_PREFIX = "user:profile:academic:update:last:";

    public static String buildKeyAboutUserProfileAcademicIdLastUpdate(Long userId){
        return USER_PROFILE_ACADEMIC_ID_LAST_UPDATE_PREFIX + userId;
    }
}
