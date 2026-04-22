package com.xiaoce.agent.auth.repository;

import com.xiaoce.agent.auth.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 权限数据访问接口
 * 
 * 作用：提供权限实体的CRUD操作和复杂查询
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /**
     * 根据权限代码查找权限
     */
    Optional<Permission> findByCode(String code);

    /**
     * 根据权限代码和状态查找权限
     */
    Optional<Permission> findByCodeAndStatus(String code, Integer status);

    /**
     * 查找所有启用的权限
     */
    @Query("SELECT p FROM Permission p WHERE p.status = 1 ORDER BY p.sortOrder ASC")
    List<Permission> findByEnabled();

    /**
     * 根据权限类型查找权限
     */
    List<Permission> findByType(Integer type);

    /**
     * 根据权限分组查找权限
     */
    List<Permission> findByPermissionGroup(String permissionGroup);

    /**
     * 根据父权限ID查找子权限
     */
    List<Permission> findByParentId(Long parentId);

    /**
     * 查找根权限（没有父权限的权限）
     */
    @Query("SELECT p FROM Permission p WHERE p.parentId IS NULL AND p.status = 1 ORDER BY p.sortOrder ASC")
    List<Permission> findRootPermissions();

    /**
     * 根据权限代码查找启用的权限
     */
    @Query("SELECT p FROM Permission p WHERE p.code = :code AND p.status = 1")
    Optional<Permission> findByCodeAndEnabled(@Param("code") String code);

    /**
     * 根据用户ID查找用户的所有权限
     */
    @Query("SELECT DISTINCT p FROM Permission p " +
           "JOIN RolePermission rp ON p.id = rp.permissionId " +
           "JOIN UserRole ur ON rp.roleId = ur.roleId " +
           "WHERE ur.userId = :userId " +
           "AND ur.status = 1 " +
           "AND rp.status = 1 " +
           "AND p.status = 1 " +
           "AND (ur.validFrom IS NULL OR ur.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (ur.validTo IS NULL OR ur.validTo >= CURRENT_TIMESTAMP) " +
           "AND (rp.validFrom IS NULL OR rp.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rp.validTo IS NULL OR rp.validTo >= CURRENT_TIMESTAMP) " +
           "ORDER BY p.sortOrder ASC")
    List<Permission> findByUserId(@Param("userId") Long userId);

    /**
     * 根据角色ID查找角色的所有权限
     */
    @Query("SELECT DISTINCT p FROM Permission p " +
           "JOIN RolePermission rp ON p.id = rp.permissionId " +
           "WHERE rp.roleId = :roleId " +
           "AND rp.status = 1 " +
           "AND p.status = 1 " +
           "AND (rp.validFrom IS NULL OR rp.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rp.validTo IS NULL OR rp.validTo >= CURRENT_TIMESTAMP) " +
           "ORDER BY p.sortOrder ASC")
    List<Permission> findByRoleId(@Param("roleId") Long roleId);

    /**
     * 检查权限是否存在
     */
    boolean existsByCode(String code);

    /**
     * 检查权限是否启用
     */
    @Query("SELECT COUNT(p) > 0 FROM Permission p WHERE p.code = :code AND p.status = 1")
    boolean existsByCodeAndEnabled(@Param("code") String code);

    /**
     * 根据权限代码列表查找权限
     */
    @Query("SELECT p FROM Permission p WHERE p.code IN :codes AND p.status = 1 ORDER BY p.sortOrder ASC")
    List<Permission> findByCodesAndEnabled(@Param("codes") List<String> codes);

    /**
     * 根据权限级别查找权限
     */
    List<Permission> findByLevel(Integer level);

    /**
     * 查找级别大于等于指定值的权限
     */
    @Query("SELECT p FROM Permission p WHERE p.level >= :level AND p.status = 1 ORDER BY p.level ASC, p.sortOrder ASC")
    List<Permission> findByLevelGreaterThanEqual(@Param("level") Integer level);

    /**
     * 查找系统内置权限
     */
    List<Permission> findByIsSystemTrue();

    /**
     * 查找非系统内置权限
     */
    List<Permission> findByIsSystemFalse();
}