package com.xiaoce.agent.auth.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户-角色关联实体类
 * 
 * 作用：建立用户和角色的多对多关联关系
 * 
 * 设计原则：
 * - 使用复合主键（用户ID + 角色ID）
 * - 支持关联关系的额外属性（如有效期）
 * - 包含关联关系的生命周期管理
 */
@Entity
@Table(name = "user_roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "role_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    /**
     * 关联ID - 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID - 外键关联用户表
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 角色ID - 外键关联角色表
     */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

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
    public UserRole(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 构造函数 - 带有效期的关联
     */
    public UserRole(Long userId, Long roleId, Instant validFrom, Instant validTo) {
        this.userId = userId;
        this.roleId = roleId;
        this.validFrom = validFrom;
        this.validTo = validTo;
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
        return "UserRole{" +
                "id=" + id +
                ", userId=" + userId +
                ", roleId=" + roleId +
                ", status=" + status +
                '}';
    }
}