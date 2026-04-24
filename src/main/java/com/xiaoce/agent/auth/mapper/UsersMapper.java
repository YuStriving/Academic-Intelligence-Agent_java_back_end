package com.xiaoce.agent.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoce.agent.auth.domain.po.User;
import org.apache.ibatis.annotations.Param;

public interface UsersMapper extends BaseMapper<User> {

    boolean existsByUsername(@Param("username") String username);

    boolean existsByEmail(@Param("email") String email);

    User findByEmail(@Param("email") String email);

    User findByUsername(@Param("username") String username);
}
