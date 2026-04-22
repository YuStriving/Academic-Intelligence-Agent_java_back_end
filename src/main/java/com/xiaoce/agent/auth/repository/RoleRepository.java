package com.xiaoce.agent.auth.repository;

import com.xiaoce.agent.auth.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色数据访问接口
 * 
 * 作用：提供角色实体的CRUD操作和复杂查询
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * 根据角色代码查找角色
     */
    Optional<Role> findByCode(String code);

    /**
     * 根据角色代码和状态查找角色
     */
    Optional<Role> findByCodeAndStatus(String code, Integer status);

    /**
     * 查找所有启用的角色
     */
    @Query("SELECT r FROM Role r WHERE r.status = 1 ORDER BY r.level DESC, r.createdAt ASC")
    List<Role> findByEnabled();

    /**
     * 根据角色级别查找角色
     */
    List<Role> findByLevel(Integer level);

    /**
     * 查找级别大于等于指定值的角色
     */
    @Query("SELECT r FROM Role r WHERE r.level >= :level AND r.status = 1 ORDER BY r.level DESC")
    List<Role> findByLevelGreaterThanEqual(@Param("level") Integer level);

    /**
     * 根据角色代码查找启用的角色
     */
    @Query("SELECT r FROM Role r WHERE r.code = :code AND r.status = 1")
    Optional<Role> findByCodeAndEnabled(@Param("code") String code);

    /**
     * 根据用户ID查找用户的所有角色
     */
    @Query("SELECT DISTINCT r FROM Role r " +
           "JOIN UserRole ur ON r.id = ur.roleId " +
           "WHERE ur.userId = :userId " +
           "AND ur.status = 1 " +
           "AND r.status = 1 " +
           "AND (ur.validFrom IS NULL OR ur.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ur.validTo IS NULL OR ur.validTo >= CURRENT_TIMESTAMP) " +
           "ORDER BY r.level DESC")
    List<Role> findByUserId(@Param("userId") Long userId);

    /**
     * 根据权限ID查找拥有该权限的角色
     */
    @Query("SELECT DISTINCT r FROM Role r " +
           "JOIN RolePermission rp ON r.id = rp.roleId " +
           "WHERE rp.permissionId = :permissionId " +
           "AND rp.status = 1 " +
           "AND r.status = 1 " +
           "AND (rp.validFrom IS NULL OR rp.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rp.validTo IS NULL OR rp.validTo >= CURRENT_TIMESTAMP) " +
           "ORDER BY r.level DESC")
    List<Role> findByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * 检查角色是否存在
     */
    boolean existsByCode(String code);

    /**
     * 检查角色是否启用
     */
    @Query("SELECT COUNT(r) > 0 FROM Role r WHERE r.code = :code AND r.status = 1")
    boolean existsByCodeAndEnabled(@Param("code") String code);

    /**
     * 根据角色代码列表查找角色
     */
    @Query("SELECT r FROM Role r WHERE r.code IN :codes AND r.status = 1 ORDER BY r.level DESC")
    List<Role> findByCodesAndEnabled(@Param("codes") List<String> codes);

    /**
     * 查找系统内置角色
     */
    List<Role> findByIsSystemTrue();

    /**
     * 查找非系统内置角色
     */
    List<Role> findByIsSystemFalse();

    /**
     * 根据角色名称模糊查找
     */
    List<Role> findByNameContainingIgnoreCase(String name);

    /**
     * 统计启用的角色数量
     */
    @Query("SELECT COUNT(r) FROM Role r WHERE r.status = 1")
    long countEnabledRoles();

    /**
     * 查找最高级别的角色
     */
    @Query("SELECT r FROM Role r WHERE r.status = 1 ORDER BY r.level DESC LIMIT 1")
    Optional<Role> findHighestLevelRole();

    /**
     * 查找最低级别的角色
     */
    @Query("SELECT r FROM Role r WHERE r.status = 1 ORDER BY r.level ASC LIMIT 1")
    Optional<Role> findLowestLevelRole();

    /**
     * 根据用户ID和角色代码检查用户是否具有该角色
     */
    @Query("SELECT COUNT(r) > 0 FROM Role r " +
           "JOIN UserRole ur ON r.id = ur.roleId " +
           "WHERE ur.userId = :userId AND r.code = :roleCode " +
           "AND ur.status = 1 AND r.status = 1 " +
           "AND (ur.validFrom IS NULL OR ur.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ur.validTo IS NULL OR ur.validTo >= CURRENT_TIMESTAMP)")
    boolean existsByUserIdAndRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}