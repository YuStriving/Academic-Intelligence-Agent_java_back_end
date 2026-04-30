package com.xiaoce.agent.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoce.agent.auth.domain.po.User;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface UsersMapper extends BaseMapper<User> {

    boolean existsByUsername(@Param("username") String username);

    boolean existsByEmail(@Param("email") String email);

    User findByEmail(@Param("email") String email);

    User findByUsername(@Param("username") String username);

    int updateAcademicIdIfAllowed(@Param("userId") Long userId,
                                  @Param("newAcademicId") String academicId,
                                  @Param("now") LocalDateTime now,
                                  @Param("allowBefore") LocalDateTime allowBefore);

    int updateUserEmail(@Param("userId") Long userId, @Param("newEmail") String newEmail);

    int updateProfileSelective(@Param("userId") Long userId,
                               @Param("nickname") String nickname,
                               @Param("avatarUrl") String avatarUrl,
                               @Param("academicId") String academicId,
                               @Param("email") String email,
                               @Param("bio") String bio,
                               @Param("gender") Integer gender,
                               @Param("school") String school,
                               @Param("academicIdChanged") boolean academicIdChanged,
                               @Param("allowBefore") LocalDateTime allowBefore,
                               @Param("now") LocalDateTime now);
}
