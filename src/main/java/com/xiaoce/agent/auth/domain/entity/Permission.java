package com.xiaoce.agent.auth.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 权限实体类
 * 
 * 作用：定义系统中的具体权限，用于细粒度的访问控制
 * 
 * 设计原则：
 * - 使用RBAC（基于角色的访问控制）模型
 * - 支持权限层级和继承
 * - 包含权限生命周期管理
 * - 支持权限分组和分类
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    /**
     * 权限ID - 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 权限代码 - 唯一标识
     * 格式：模块:操作:资源（如user:read:profile）
     */
    @Column(name = "code", nullable = false, unique = true, length = 128)
    private String code;

    /**
     * 权限名称 - 显示名称
     */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /**
     * 权限描述
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 权限类型
     * 1: 菜单权限, 2: 操作权限, 3: 数据权限, 4: 接口权限
     */
    @Column(name = "type", nullable = false)
    private Integer type = 1;

    /**
     * 权限分组
     * 用于权限分类（如用户管理、系统设置等）
     */
    @Column(name = "permission_group", nullable = false, length = 64)
    private String permissionGroup;

    /**
     * 父权限ID - 用于权限层级
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 权限级别
     * 数值越大，权限级别越高
     */
    @Column(name = "level", nullable = false)
    private Integer level = 1;

    /**
     * 排序序号
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 权限路径
     * 用于菜单权限的URL路径
     */
    @Column(name = "path", length = 255)
    private String path;

    /**
     * 权限图标
     */
    @Column(name = "icon", length = 64)
    private String icon;

    /**
     * 是否系统内置权限
     * 系统内置权限不可删除
     */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    /**
     * 权限状态
     * 0: 禁用, 1: 启用
     */
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * 构造函数 - 基础权限
     */
    public Permission(String code, String name, String description, String permissionGroup, Integer type) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.permissionGroup = permissionGroup;
        this.type = type;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 构造函数 - 完整权限
     */
    public Permission(String code, String name, String description, String permissionGroup, 
                     Integer type, Integer level, Integer sortOrder, String path, String icon) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.permissionGroup = permissionGroup;
        this.type = type;
        this.level = level;
        this.sortOrder = sortOrder;
        this.path = path;
        this.icon = icon;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 检查权限是否启用
     */
    public boolean isEnabled() {
        return this.status != null && this.status == 1;
    }

    /**
     * 检查权限是否禁用
     */
    public boolean isDisabled() {
        return this.status != null && this.status == 0;
    }

    /**
     * 检查是否为系统内置权限
     */
    public boolean isSystemPermission() {
        return this.isSystem != null && this.isSystem;
    }

    /**
     * 检查是否为菜单权限
     */
    public boolean isMenuPermission() {
        return this.type != null && this.type == 1;
    }

    /**
     * 检查是否为操作权限
     */
    public boolean isActionPermission() {
        return this.type != null && this.type == 2;
    }

    /**
     * 检查是否为数据权限
     */
    public boolean isDataPermission() {
        return this.type != null && this.type == 3;
    }

    /**
     * 检查是否为接口权限
     */
    public boolean isApiPermission() {
        return this.type != null && this.type == 4;
    }

    /**
     * 启用权限
     */
    public void enable() {
        this.status = 1;
    }

    /**
     * 禁用权限
     */
    public void disable() {
        this.status = 0;
    }

    /**
     * 设置父权限
     */
    public void setParent(Permission parent) {
        this.parentId = parent != null ? parent.getId() : null;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "Permission{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", group='" + permissionGroup + '\'' +
                ", status=" + status +
                '}';
    }
}