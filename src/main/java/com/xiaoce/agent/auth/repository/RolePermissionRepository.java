package com.xiaoce.agent.auth.repository;

import com.xiaoce.agent.auth.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色-权限关联数据访问接口
 * 
 * 作用：提供角色-权限关联实体的CRUD操作和复杂查询
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    /**
     * 根据角色ID查找关联
     */
    List<RolePermission> findByRoleId(Long roleId);

    /**
     * 根据权限ID查找关联
     */
    List<RolePermission> findByPermissionId(Long permissionId);

    /**
     * 根据角色ID和权限ID查找关联
     */
    List<RolePermission> findByRoleIdAndPermissionId(Long roleId, Long permissionId);

    /**
     * 检查角色和权限是否已关联
     */
    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    /**
     * 检查角色和权限是否有效关联
     */
    @Query("SELECT COUNT(rp) > 0 FROM RolePermission rp " +
           "WHERE rp.roleId = :roleId AND rp.permissionId = :permissionId " +
           "AND rp.status = 1 " +
           "AND (rp.validFrom IS NULL OR rp.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rp.validTo IS NULL OR rp.validTo >= CURRENT_TIMESTAMP)")
    boolean existsByRoleIdAndPermissionIdAndValid(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /**
     * 删除角色和权限的关联
     */
    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);

    /**
     * 根据角色ID列表查找关联
     */
    List<RolePermission> findByRoleIdIn(List<Long> roleIds);

    /**
     * 根据权限类型查找关联
     */
    List<RolePermission> findByPermissionType(Integer permissionType);

    /**
     * 查找允许类型的关联
     */
    @Query("SELECT rp FROM RolePermission rp WHERE rp.permissionType = 1 AND rp.status = 1")
    List<RolePermission> findAllowedPermissions();

    /**
     * 查找拒绝类型的关联
     */
    @Query("SELECT rp FROM RolePermission rp WHERE rp.permissionType = 2 AND rp.status = 1")
    List<RolePermission> findDeniedPermissions();

    /**
     * 根据角色ID查找有效的权限关联
     */
    @Query("SELECT rp FROM RolePermission rp " +
           "WHERE rp.roleId = :roleId " +
           "AND rp.status = 1 " +
           "AND (rp.validFrom IS NULL OR rp.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rp.validTo IS NULL OR rp.validTo >= CURRENT_TIMESTAMP)")
    List<RolePermission> findByRoleIdAndValid(@Param("roleId") Long roleId);

    /**
     * 根据权限ID查找有效的角色关联
     */
    @Query("SELECT rp FROM RolePermission rp " +
           "WHERE rp.permissionId = :permissionId " +
           "AND rp.status = 1 " +
           "AND (rp.validFrom IS NULL OR rp.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rp.validTo IS NULL OR rp.validTo >= CURRENT_TIMESTAMP)")
    List<RolePermission> findByPermissionIdAndValid(@Param("permissionId") Long permissionId);

    /**
     * 统计角色的权限数量
     */
    @Query("SELECT COUNT(rp) FROM RolePermission rp WHERE rp.roleId = :roleId AND rp.status = 1")
    long countByRoleId(@Param("roleId") Long roleId);

    /**
     * 统计权限被分配的角色数量
     */
    @Query("SELECT COUNT(rp) FROM RolePermission rp WHERE rp.permissionId = :permissionId AND rp.status = 1")
    long countByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * 根据权限范围查找关联
     */
    List<RolePermission> findByPermissionScope(String permissionScope);

    /**
     * 根据权限范围和类型查找关联
     */
    List<RolePermission> findByPermissionScopeAndPermissionType(String permissionScope, Integer permissionType);

    /**
     * 查找过期的关联
     */
    @Query("SELECT rp FROM RolePermission rp WHERE rp.validTo IS NOT NULL AND rp.validTo < CURRENT_TIMESTAMP")
    List<RolePermission> findExpiredPermissions();

    /**
     * 查找即将过期的关联（7天内）
     */
    @Query("SELECT rp FROM RolePermission rp WHERE rp.validTo IS NOT NULL " +
           "AND rp.validTo BETWEEN CURRENT_TIMESTAMP AND CURRENT_TIMESTAMP + 7 DAY")
    List<RolePermission> findExpiringPermissions();
}