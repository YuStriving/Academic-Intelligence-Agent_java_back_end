package com.xiaoce.agent.auth.service;

import com.xiaoce.agent.auth.domain.entity.Permission;
import com.xiaoce.agent.auth.domain.entity.RolePermission;
import com.xiaoce.agent.auth.repository.PermissionRepository;
import com.xiaoce.agent.auth.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 权限服务类
 * 
 * 作用：提供权限管理和角色权限关联的CRUD操作
 * 
 * 功能：
 * - 权限管理：创建、查询、更新、删除权限
 * - 角色权限关联：为角色分配、移除权限
 * - 权限检查：检查用户是否具有指定权限
 * - 权限初始化：系统启动时初始化默认权限
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * 初始化系统默认权限
     * 系统启动时自动调用
     */
    @Transactional
    public void initDefaultPermissions() {
        // 检查权限是否已存在
        if (permissionRepository.count() > 0) {
            return;
        }

        // 创建系统默认权限
        List<Permission> permissions = List.of(
            // 用户管理权限
            new Permission("user:read:profile", "查看用户资料", "查看用户基本信息", "用户管理", 2),
            new Permission("user:update:profile", "更新用户资料", "修改用户基本信息", "用户管理", 2),
            new Permission("user:delete:profile", "删除用户", "删除用户账号", "用户管理", 2),
            
            // 角色管理权限
            new Permission("role:read:list", "查看角色列表", "查看所有角色信息", "角色管理", 2),
            new Permission("role:create", "创建角色", "创建新角色", "角色管理", 2),
            new Permission("role:update", "更新角色", "修改角色信息", "角色管理", 2),
            new Permission("role:delete", "删除角色", "删除角色", "角色管理", 2),
            
            // 权限管理权限
            new Permission("permission:read:list", "查看权限列表", "查看所有权限信息", "权限管理", 2),
            new Permission("permission:assign", "分配权限", "为角色分配权限", "权限管理", 2),
            new Permission("permission:revoke", "撤销权限", "从角色撤销权限", "权限管理", 2),
            
            // 系统管理权限
            new Permission("system:config:read", "查看系统配置", "查看系统配置信息", "系统管理", 2),
            new Permission("system:config:update", "更新系统配置", "修改系统配置", "系统管理", 2),
            new Permission("system:log:read", "查看系统日志", "查看系统操作日志", "系统管理", 2),
            
            // 数据管理权限
            new Permission("data:export", "导出数据", "导出系统数据", "数据管理", 2),
            new Permission("data:import", "导入数据", "导入系统数据", "数据管理", 2),
            new Permission("data:backup", "数据备份", "备份系统数据", "数据管理", 2),
            
            // 菜单权限
            new Permission("menu:dashboard", "访问仪表板", "访问系统仪表板", "菜单权限", 1),
            new Permission("menu:user:management", "访问用户管理", "访问用户管理页面", "菜单权限", 1),
            new Permission("menu:role:management", "访问角色管理", "访问角色管理页面", "菜单权限", 1),
            new Permission("menu:permission:management", "访问权限管理", "访问权限管理页面", "菜单权限", 1),
            new Permission("menu:system:settings", "访问系统设置", "访问系统设置页面", "菜单权限", 1)
        );

        permissionRepository.saveAll(permissions);
    }

    /**
     * 根据权限代码查找权限
     */
    public Optional<Permission> findByCode(String code) {
        return permissionRepository.findByCodeAndEnabled(code);
    }

    /**
     * 获取所有启用的权限
     */
    public List<Permission> findAllEnabledPermissions() {
        return permissionRepository.findByEnabled();
    }

    /**
     * 为角色分配权限
     */
    @Transactional
    public void assignPermissionToRole(Long roleId, String permissionCode) {
        Optional<Permission> permissionOpt = permissionRepository.findByCodeAndEnabled(permissionCode);
        if (permissionOpt.isEmpty()) {
            throw new IllegalArgumentException("权限不存在或已禁用: " + permissionCode);
        }

        Permission permission = permissionOpt.get();
        
        // 检查是否已分配该权限
        boolean alreadyAssigned = rolePermissionRepository.existsByRoleIdAndPermissionIdAndValid(roleId, permission.getId());
        if (alreadyAssigned) {
            return; // 已分配，无需重复分配
        }

        // 创建角色权限关联
        RolePermission rolePermission = new RolePermission(roleId, permission.getId());
        rolePermissionRepository.save(rolePermission);
    }

    /**
     * 为角色移除权限
     */
    @Transactional
    public void removePermissionFromRole(Long roleId, String permissionCode) {
        Optional<Permission> permissionOpt = permissionRepository.findByCodeAndEnabled(permissionCode);
        if (permissionOpt.isEmpty()) {
            return; // 权限不存在，无需移除
        }

        Permission permission = permissionOpt.get();
        
        // 查找并删除角色权限关联
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permission.getId());
        rolePermissionRepository.deleteAll(rolePermissions);
    }

    /**
     * 获取角色的所有有效权限
     */
    public List<Permission> getRolePermissions(Long roleId) {
        return permissionRepository.findByRoleId(roleId);
    }

    /**
     * 获取角色的权限代码列表
     */
    public List<String> getRolePermissionCodes(Long roleId) {
        List<Permission> permissions = getRolePermissions(roleId);
        return permissions.stream()
                .map(Permission::getCode)
                .toList();
    }

    /**
     * 获取用户的所有有效权限
     */
    public List<Permission> getUserPermissions(Long userId) {
        return permissionRepository.findByUserId(userId);
    }

    /**
     * 获取用户的权限代码列表
     */
    public List<String> getUserPermissionCodes(Long userId) {
        List<Permission> permissions = getUserPermissions(userId);
        return permissions.stream()
                .map(Permission::getCode)
                .toList();
    }

    /**
     * 检查用户是否具有指定权限
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        List<String> userPermissionCodes = getUserPermissionCodes(userId);
        return userPermissionCodes.contains(permissionCode);
    }

    /**
     * 检查用户是否具有所有指定权限
     */
    public boolean hasAllPermissions(Long userId, List<String> permissionCodes) {
        List<String> userPermissionCodes = getUserPermissionCodes(userId);
        return userPermissionCodes.containsAll(permissionCodes);
    }

    /**
     * 检查用户是否具有任一指定权限
     */
    public boolean hasAnyPermission(Long userId, List<String> permissionCodes) {
        List<String> userPermissionCodes = getUserPermissionCodes(userId);
        return permissionCodes.stream().anyMatch(userPermissionCodes::contains);
    }

    /**
     * 检查角色是否具有指定权限
     */
    public boolean roleHasPermission(Long roleId, String permissionCode) {
        List<String> rolePermissionCodes = getRolePermissionCodes(roleId);
        return rolePermissionCodes.contains(permissionCode);
    }

    /**
     * 根据权限类型查找权限
     */
    public List<Permission> findByType(Integer type) {
        return permissionRepository.findByType(type);
    }

    /**
     * 根据权限分组查找权限
     */
    public List<Permission> findByGroup(String group) {
        return permissionRepository.findByPermissionGroup(group);
    }

    /**
     * 创建新权限
     */
    @Transactional
    public Permission createPermission(Permission permission) {
        // 检查权限代码是否已存在
        if (permissionRepository.existsByCode(permission.getCode())) {
            throw new IllegalArgumentException("权限代码已存在: " + permission.getCode());
        }
        
        return permissionRepository.save(permission);
    }

    /**
     * 更新权限
     */
    @Transactional
    public Permission updatePermission(Long permissionId, Permission permissionDetails) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在: " + permissionId));
        
        // 更新权限信息
        permission.setName(permissionDetails.getName());
        permission.setDescription(permissionDetails.getDescription());
        permission.setPermissionGroup(permissionDetails.getPermissionGroup());
        permission.setType(permissionDetails.getType());
        permission.setLevel(permissionDetails.getLevel());
        permission.setSortOrder(permissionDetails.getSortOrder());
        permission.setPath(permissionDetails.getPath());
        permission.setIcon(permissionDetails.getIcon());
        
        return permissionRepository.save(permission);
    }

    /**
     * 删除权限
     */
    @Transactional
    public void deletePermission(Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在: " + permissionId));
        
        // 检查是否为系统内置权限
        if (permission.isSystemPermission()) {
            throw new IllegalArgumentException("系统内置权限不可删除: " + permission.getCode());
        }
        
        // 删除角色权限关联
        List<RolePermission> rolePermissions = rolePermissionRepository.findByPermissionId(permissionId);
        rolePermissionRepository.deleteAll(rolePermissions);
        
        // 删除权限
        permissionRepository.delete(permission);
    }
}