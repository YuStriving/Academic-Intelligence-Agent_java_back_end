package com.xiaoce.agent.auth.repository;

import com.xiaoce.agent.auth.domain.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户-角色关联数据访问接口
 * 
 * 作用：提供用户-角色关联实体的CRUD操作和复杂查询
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * 根据用户ID查找关联
     */
    List<UserRole> findByUserId(Long userId);

    /**
     * 根据角色ID查找关联
     */
    List<UserRole> findByRoleId(Long roleId);

    /**
     * 根据用户ID和角色ID查找关联
     */
    List<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 检查用户和角色是否已关联
     */
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 检查用户和角色是否有效关联
     */
    @Query("SELECT COUNT(ur) > 0 FROM UserRole ur " +
           "WHERE ur.userId = :userId AND ur.roleId = :roleId " +
           "AND ur.status = 1 " +
           "AND (ur.validFrom IS NULL OR ur.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ur.validTo IS NULL OR ur.validTo >= CURRENT_TIMESTAMP)")
    boolean existsByUserIdAndRoleIdAndValid(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 删除用户和角色的关联
     */
    void deleteByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 根据用户ID列表查找关联
     */
    List<UserRole> findByUserIdIn(List<Long> userIds);

    /**
     * 根据角色ID列表查找关联
     */
    List<UserRole> findByRoleIdIn(List<Long> roleIds);

    /**
     * 根据用户ID查找有效的角色关联
     */
    @Query("SELECT ur FROM UserRole ur " +
           "WHERE ur.userId = :userId " +
           "AND ur.status = 1 " +
           "AND (ur.validFrom IS NULL OR ur.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ur.validTo IS NULL OR ur.validTo >= CURRENT_TIMESTAMP)")
    List<UserRole> findByUserIdAndValid(@Param("userId") Long userId);

    /**
     * 根据角色ID查找有效的用户关联
     */
    @Query("SELECT ur FROM UserRole ur " +
           "WHERE ur.roleId = :roleId " +
           "AND ur.status = 1 " +
           "AND (ur.validFrom IS NULL OR ur.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ur.validTo IS NULL OR ur.validTo >= CURRENT_TIMESTAMP)")
    List<UserRole> findByRoleIdAndValid(@Param("roleId") Long roleId);

    /**
     * 统计用户的角色数量
     */
    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.userId = :userId AND ur.status = 1")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 统计角色被分配的用户数量
     */
    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.roleId = :roleId AND ur.status = 1")
    long countByRoleId(@Param("roleId") Long roleId);

    /**
     * 查找过期的关联
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.validTo IS NOT NULL AND ur.validTo < CURRENT_TIMESTAMP")
    List<UserRole> findExpiredRoles();

    /**
     * 查找即将过期的关联（7天内）
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.validTo IS NOT NULL " +
           "AND ur.validTo BETWEEN CURRENT_TIMESTAMP AND CURRENT_TIMESTAMP + 7 DAY")
    List<UserRole> findExpiringRoles();
}