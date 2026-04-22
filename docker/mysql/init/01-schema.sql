-- 用户表（学术智能代理）
-- 字符集 utf8mb4，与 docker-compose MySQL 配置一致

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统自增主键',
  `academic_id` VARCHAR(64) DEFAULT NULL COMMENT '学术/学号标识（如：教工号、学生证号）',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `email` VARCHAR(128) NOT NULL COMMENT '邮箱',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  
  -- 主键
  PRIMARY KEY (`id`),
  
  -- 唯一索引：确保核心业务标识不重复
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_academic` (`academic_id`), -- 学术ID也具有唯一性约束
  
  -- 普通索引：优化查询性能
  KEY `idx_users_created_at` (`created_at`), -- 适用于后台按注册时间排序或统计
  KEY `idx_users_status_created` (`status`, `created_at`) -- 覆盖索引：用于过滤状态后进行排序
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基础信息表';

SET FOREIGN_KEY_CHECKS = 1;
