package com.xiaoce.agent.auth.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 角色-权限关联实体类
 * 
 * 作用：建立角色和权限的多对多关联关系
 * 
 * 设计原则：
 * - 使用复合主键（角色ID + 权限ID）
 * - 支持关联关系的额外属性（如权限范围）
 * - 包含关联关系的生命周期管理
 */
@Entity
@Table(name = "role_permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"role_id", "permission_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    /**
     * 关联ID - 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 角色ID - 外键关联角色表
     */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * 权限ID - 外键关联权限表
     */
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    /**
     * 权限范围
     * 用于数据权限控制（如部门、组织等）
     */
    @Column(name = "permission_scope", length = 255)
    private String permissionScope;

    /**
     * 权限类型
     * 1: 允许, 2: 拒绝, 3: 继承
     */
    @Column(name = "permission_type", nullable = false)
    private Integer permissionType = 1;

    /**
     * 关联状态
     * 0: 无效, 1: 有效
     */
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    /**
     * 有效期开始时间
     */
    @Column(name = "valid_from")
    private Instant validFrom;

    /**
     * 有效期结束时间
     */
    @Column(name = "valid_to")
    private Instant validTo;

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
     * 构造函数 - 基础关联
     */
    public RolePermission(Long roleId, Long permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 构造函数 - 完整关联
     */
    public RolePermission(Long roleId, Long permissionId, String permissionScope, Integer permissionType) {
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.permissionScope = permissionScope;
        this.permissionType = permissionType;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 检查关联是否有效
     */
    public boolean isValid() {
        if (this.status == null || this.status == 0) {
            return false;
        }
        
        Instant now = Instant.now();
        
        // 检查有效期开始时间
        if (this.validFrom != null && now.isBefore(this.validFrom)) {
            return false;
        }
        
        // 检查有效期结束时间
        if (this.validTo != null && now.isAfter(this.validTo)) {
            return false;
        }
        
        return true;
    }

    /**
     * 检查是否为允许权限
     */
    public boolean isAllowed() {
        return this.permissionType != null && this.permissionType == 1;
    }

    /**
     * 检查是否为拒绝权限
     */
    public boolean isDenied() {
        return this.permissionType != null && this.permissionType == 2;
    }

    /**
     * 检查是否为继承权限
     */
    public boolean isInherited() {
        return this.permissionType != null && this.permissionType == 3;
    }

    /**
     * 启用关联
     */
    public void enable() {
        this.status = 1;
    }

    /**
     * 禁用关联
     */
    public void disable() {
        this.status = 0;
    }

    /**
     * 设置允许权限
     */
    public void setAllowed() {
        this.permissionType = 1;
    }

    /**
     * 设置拒绝权限
     */
    public void setDenied() {
        this.permissionType = 2;
    }

    /**
     * 设置继承权限
     */
    public void setInherited() {
        this.permissionType = 3;
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
        return "RolePermission{" +
                "id=" + id +
                ", roleId=" + roleId +
                ", permissionId=" + permissionId +
                ", permissionType=" + permissionType +
                ", status=" + status +
                '}';
    }
}