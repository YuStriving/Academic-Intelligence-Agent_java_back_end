package com.xiaoce.agent.auth.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色实体类
 * 
 * 作用：定义系统中的角色信息，用于权限控制
 * 
 * 设计原则：
 * - 使用JPA注解进行数据库映射
 * - 支持角色层级和权限继承
 * - 包含角色生命周期管理
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    /**
     * 角色ID - 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 角色代码 - 唯一标识
     * 格式：ROLE_XXX（如ROLE_USER, ROLE_ADMIN）
     */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /**
     * 角色名称 - 显示名称
     */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /**
     * 角色描述
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 角色级别 - 用于权限继承
     * 数值越大，权限越高
     */
    @Column(name = "level", nullable = false)
    private Integer level = 0;

    /**
     * 是否系统内置角色
     * 系统内置角色不可删除
     */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    /**
     * 角色状态
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
     * 角色权限关联关系（多对多）
     * 通过中间表role_permissions关联
     */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", referencedColumnName = "id")
    private List<RolePermission> rolePermissions = new ArrayList<>();

    /**
     * 构造函数 - 基础信息
     */
    public Role(String code, String name, String description, Integer level) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.level = level;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 构造函数 - 系统内置角色
     */
    public Role(String code, String name, String description, Integer level, Boolean isSystem) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.level = level;
        this.isSystem = isSystem;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 检查角色是否启用
     */
    public boolean isEnabled() {
        return this.status != null && this.status == 1;
    }

    /**
     * 检查角色是否禁用
     */
    public boolean isDisabled() {
        return this.status != null && this.status == 0;
    }

    /**
     * 检查是否为系统内置角色
     */
    public boolean isSystemRole() {
        return this.isSystem != null && this.isSystem;
    }

    /**
     * 启用角色
     */
    public void enable() {
        this.status = 1;
    }

    /**
     * 禁用角色
     */
    public void disable() {
        this.status = 0;
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
        return "Role{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", level=" + level +
                ", status=" + status +
                '}';
    }
}