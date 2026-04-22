package com.xiaoce.agent.auth.service;

import com.xiaoce.agent.auth.domain.entity.Permission;
import com.xiaoce.agent.auth.domain.entity.Role;
import com.xiaoce.agent.auth.domain.entity.RolePermission;
import com.xiaoce.agent.auth.domain.entity.UserRole;
import com.xiaoce.agent.auth.repository.PermissionRepository;
import com.xiaoce.agent.auth.repository.RolePermissionRepository;
import com.xiaoce.agent.auth.repository.RoleRepository;
import com.xiaoce.agent.auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 角色服务类
 * 
 * 作用：提供角色管理和用户角色关联的CRUD操作
 * 
 * 功能：
 * - 角色管理：创建、查询、更新、删除角色
 * - 用户角色关联：为用户分配、移除角色
 * - 权限检查：检查用户是否具有指定角色
 * - 角色初始化：系统启动时初始化默认角色
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * 初始化系统默认角色
     * 系统启动时自动调用
     */
    @Transactional
    public void initDefaultRoles() {
        // 检查角色是否已存在
        if (roleRepository.count() > 0) {
            return;
        }

        // 创建系统默认角色
        Role userRole = new Role("ROLE_USER", "普通用户", "系统基础用户角色", 1, true);
        Role adminRole = new Role("ROLE_ADMIN", "管理员", "系统管理员角色", 10, true);
        Role superAdminRole = new Role("ROLE_SUPER_ADMIN", "超级管理员", "系统超级管理员角色", 100, true);

        roleRepository.saveAll(List.of(userRole, adminRole, superAdminRole));
    }

    /**
     * 根据角色代码查找角色
     */
    public Optional<Role> findByCode(String code) {
        return roleRepository.findByCodeAndEnabled(code);
    }

    /**
     * 获取所有启用的角色
     */
    public List<Role> findAllEnabledRoles() {
        return roleRepository.findByEnabled();
    }

    /**
     * 为用户分配角色
     */
    @Transactional
    public void assignRoleToUser(Long userId, String roleCode) {
        Optional<Role> roleOpt = roleRepository.findByCodeAndEnabled(roleCode);
        if (roleOpt.isEmpty()) {
            throw new IllegalArgumentException("角色不存在或已禁用: " + roleCode);
        }

        Role role = roleOpt.get();
        
        // 检查是否已分配该角色
        boolean alreadyAssigned = userRoleRepository.existsByUserIdAndRoleIdAndValid(userId, role.getId());
        if (alreadyAssigned) {
            return; // 已分配，无需重复分配
        }

        // 创建用户角色关联
        UserRole userRole = new UserRole(userId, role.getId());
        userRoleRepository.save(userRole);
    }

    /**
     * 为用户移除角色
     */
    @Transactional
    public void removeRoleFromUser(Long userId, String roleCode) {
        Optional<Role> roleOpt = roleRepository.findByCodeAndEnabled(roleCode);
        if (roleOpt.isEmpty()) {
            return; // 角色不存在，无需移除
        }

        Role role = roleOpt.get();
        
        // 查找并删除用户角色关联
        List<UserRole> userRoles = userRoleRepository.findByUserIdAndRoleId(userId, role.getId());
        userRoleRepository.deleteAll(userRoles);
    }

    /**
     * 获取用户的所有有效角色
     */
    public List<Role> getUserRoles(Long userId) {
        return roleRepository.findByUserId(userId);
    }

    /**
     * 获取用户的角色代码列表
     */
    public List<String> getUserRoleCodes(Long userId) {
        List<Role> roles = getUserRoles(userId);
        return roles.stream()
                .map(Role::getCode)
                .toList();
    }

    /**
     * 检查用户是否具有指定角色
     */
    public boolean hasRole(Long userId, String roleCode) {
        List<String> userRoleCodes = getUserRoleCodes(userId);
        return userRoleCodes.contains(roleCode);
    }

    /**
     * 检查用户是否具有管理员权限
     */
    public boolean isAdmin(Long userId) {
        return hasRole(userId, "ROLE_ADMIN") || hasRole(userId, "ROLE_SUPER_ADMIN");
    }

    /**
     * 检查用户是否具有超级管理员权限
     */
    public boolean isSuperAdmin(Long userId) {
        return hasRole(userId, "ROLE_SUPER_ADMIN");
    }

    /**
     * 为用户分配默认角色（普通用户）
     */
    @Transactional
    public void assignDefaultRole(User user) {
        assignRoleToUser(user.getId(), "ROLE_USER");
    }

    /**
     * 为用户分配管理员角色
     */
    @Transactional
    public void assignAdminRole(User user) {
        assignRoleToUser(user.getId(), "ROLE_ADMIN");
        assignRoleToUser(user.getId(), "ROLE_USER"); // 管理员也具备普通用户权限
    }

    /**
     * 为用户分配超级管理员角色
     */
    @Transactional
    public void assignSuperAdminRole(User user) {
        assignRoleToUser(user.getId(), "ROLE_SUPER_ADMIN");
        assignRoleToUser(user.getId(), "ROLE_ADMIN");
        assignRoleToUser(user.getId(), "ROLE_USER"); // 超级管理员具备所有权限
    }

    /**
     * 为角色分配权限
     */
    @Transactional
    public void assignPermissionToRole(String roleCode, String permissionCode) {
        Optional<Role> roleOpt = roleRepository.findByCodeAndEnabled(roleCode);
        if (roleOpt.isEmpty()) {
            throw new IllegalArgumentException("角色不存在或已禁用: " + roleCode);
        }

        Optional<Permission> permissionOpt = permissionRepository.findByCodeAndEnabled(permissionCode);
        if (permissionOpt.isEmpty()) {
            throw new IllegalArgumentException("权限不存在或已禁用: " + permissionCode);
        }

        Role role = roleOpt.get();
        Permission permission = permissionOpt.get();

        // 检查是否已分配该权限
        boolean alreadyAssigned = rolePermissionRepository.existsByRoleIdAndPermissionIdAndValid(role.getId(), permission.getId());
        if (alreadyAssigned) {
            return; // 已分配，无需重复分配
        }

        // 创建角色权限关联
        RolePermission rolePermission = new RolePermission(role.getId(), permission.getId());
        rolePermissionRepository.save(rolePermission);
    }

    /**
     * 为角色移除权限
     */
    @Transactional
    public void removePermissionFromRole(String roleCode, String permissionCode) {
        Optional<Role> roleOpt = roleRepository.findByCodeAndEnabled(roleCode);
        if (roleOpt.isEmpty()) {
            return; // 角色不存在，无需移除
        }

        Optional<Permission> permissionOpt = permissionRepository.findByCodeAndEnabled(permissionCode);
        if (permissionOpt.isEmpty()) {
            return; // 权限不存在，无需移除
        }

        Role role = roleOpt.get();
        Permission permission = permissionOpt.get();

        // 查找并删除角色权限关联
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIdAndPermissionId(role.getId(), permission.getId());
        rolePermissionRepository.deleteAll(rolePermissions);
    }

    /**
     * 获取角色的所有权限
     */
    public List<Permission> getRolePermissions(String roleCode) {
        Optional<Role> roleOpt = roleRepository.findByCodeAndEnabled(roleCode);
        if (roleOpt.isEmpty()) {
            return List.of();
        }

        Role role = roleOpt.get();
        return permissionRepository.findByRoleId(role.getId());
    }

    /**
     * 获取用户的完整权限列表（包含所有角色权限）
     */
    public List<Permission> getUserPermissions(Long userId) {
        return permissionRepository.findByUserId(userId);
    }

    /**
     * 检查用户是否具有指定权限
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        List<Permission> userPermissions = getUserPermissions(userId);
        return userPermissions.stream()
                .map(Permission::getCode)
                .anyMatch(code -> code.equals(permissionCode));
    }

    /**
     * 检查用户是否具有所有指定权限
     */
    public boolean hasAllPermissions(Long userId, List<String> permissionCodes) {
        List<Permission> userPermissions = getUserPermissions(userId);
        List<String> userPermissionCodes = userPermissions.stream()
                .map(Permission::getCode)
                .toList();
        return userPermissionCodes.containsAll(permissionCodes);
    }

    /**
     * 检查用户是否具有任一指定权限
     */
    public boolean hasAnyPermission(Long userId, List<String> permissionCodes) {
        List<Permission> userPermissions = getUserPermissions(userId);
        List<String> userPermissionCodes = userPermissions.stream()
                .map(Permission::getCode)
                .toList();
        return permissionCodes.stream().anyMatch(userPermissionCodes::contains);
    }
}