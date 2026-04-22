-- 用户-角色-权限三级结构数据库初始化脚本
-- 创建时间：2026-04-22
-- 作者：小策

-- 创建角色表
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色代码',
    name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) COMMENT '角色描述',
    level INT NOT NULL DEFAULT 0 COMMENT '角色级别',
    is_system BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否系统内置角色',
    status INT NOT NULL DEFAULT 1 COMMENT '角色状态：0-禁用，1-启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_status (status),
    INDEX idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 创建权限表
CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE COMMENT '权限代码',
    name VARCHAR(64) NOT NULL COMMENT '权限名称',
    description VARCHAR(255) COMMENT '权限描述',
    type INT NOT NULL DEFAULT 1 COMMENT '权限类型：1-菜单权限，2-操作权限，3-数据权限，4-接口权限',
    permission_group VARCHAR(64) NOT NULL COMMENT '权限分组',
    parent_id BIGINT COMMENT '父权限ID',
    level INT NOT NULL DEFAULT 1 COMMENT '权限级别',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    path VARCHAR(255) COMMENT '权限路径',
    icon VARCHAR(64) COMMENT '权限图标',
    is_system BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否系统内置权限',
    status INT NOT NULL DEFAULT 1 COMMENT '权限状态：0-禁用，1-启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_status (status),
    INDEX idx_type (type),
    INDEX idx_group (permission_group),
    INDEX idx_parent_id (parent_id),
    INDEX idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 创建用户-角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    status INT NOT NULL DEFAULT 1 COMMENT '关联状态：0-无效，1-有效',
    valid_from TIMESTAMP NULL COMMENT '有效期开始时间',
    valid_to TIMESTAMP NULL COMMENT '有效期结束时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id),
    INDEX idx_status (status),
    INDEX idx_valid_from (valid_from),
    INDEX idx_valid_to (valid_to),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 创建角色-权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    permission_scope VARCHAR(255) COMMENT '权限范围',
    permission_type INT NOT NULL DEFAULT 1 COMMENT '权限类型：1-允许，2-拒绝，3-继承',
    status INT NOT NULL DEFAULT 1 COMMENT '关联状态：0-无效，1-有效',
    valid_from TIMESTAMP NULL COMMENT '有效期开始时间',
    valid_to TIMESTAMP NULL COMMENT '有效期结束时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id),
    INDEX idx_status (status),
    INDEX idx_permission_type (permission_type),
    INDEX idx_valid_from (valid_from),
    INDEX idx_valid_to (valid_to),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- 插入系统默认角色数据
INSERT IGNORE INTO roles (code, name, description, level, is_system, status) VALUES
('ROLE_USER', '普通用户', '系统基础用户角色', 1, TRUE, 1),
('ROLE_ADMIN', '管理员', '系统管理员角色', 10, TRUE, 1),
('ROLE_SUPER_ADMIN', '超级管理员', '系统超级管理员角色', 100, TRUE, 1);

-- 插入系统默认权限数据
INSERT IGNORE INTO permissions (code, name, description, type, permission_group, level, sort_order) VALUES
-- 用户管理权限
('user:read:profile', '查看用户资料', '查看用户基本信息', 2, '用户管理', 1, 1),
('user:update:profile', '更新用户资料', '修改用户基本信息', 2, '用户管理', 2, 2),
('user:delete:profile', '删除用户', '删除用户账号', 2, '用户管理', 3, 3),

-- 角色管理权限
('role:read:list', '查看角色列表', '查看所有角色信息', 2, '角色管理', 1, 1),
('role:create', '创建角色', '创建新角色', 2, '角色管理', 2, 2),
('role:update', '更新角色', '修改角色信息', 2, '角色管理', 3, 3),
('role:delete', '删除角色', '删除角色', 2, '角色管理', 4, 4),

-- 权限管理权限
('permission:read:list', '查看权限列表', '查看所有权限信息', 2, '权限管理', 1, 1),
('permission:assign', '分配权限', '为角色分配权限', 2, '权限管理', 2, 2),
('permission:revoke', '撤销权限', '从角色撤销权限', 2, '权限管理', 3, 3),

-- 系统管理权限
('system:config:read', '查看系统配置', '查看系统配置信息', 2, '系统管理', 1, 1),
('system:config:update', '更新系统配置', '修改系统配置', 2, '系统管理', 2, 2),
('system:log:read', '查看系统日志', '查看系统操作日志', 2, '系统管理', 3, 3),

-- 数据管理权限
('data:export', '导出数据', '导出系统数据', 2, '数据管理', 1, 1),
('data:import', '导入数据', '导入系统数据', 2, '数据管理', 2, 2),
('data:backup', '数据备份', '备份系统数据', 2, '数据管理', 3, 3),

-- 菜单权限
('menu:dashboard', '访问仪表板', '访问系统仪表板', 1, '菜单权限', 1, 1),
('menu:user:management', '访问用户管理', '访问用户管理页面', 1, '菜单权限', 2, 2),
('menu:role:management', '访问角色管理', '访问角色管理页面', 1, '菜单权限', 3, 3),
('menu:permission:management', '访问权限管理', '访问权限管理页面', 1, '菜单权限', 4, 4),
('menu:system:settings', '访问系统设置', '访问系统设置页面', 1, '菜单权限', 5, 5);

-- 为普通用户角色分配基础权限
INSERT IGNORE INTO role_permissions (role_id, permission_id, permission_type) 
SELECT r.id, p.id, 1 
FROM roles r, permissions p 
WHERE r.code = 'ROLE_USER' 
AND p.code IN ('menu:dashboard', 'user:read:profile', 'user:update:profile');

-- 为管理员角色分配权限
INSERT IGNORE INTO role_permissions (role_id, permission_id, permission_type) 
SELECT r.id, p.id, 1 
FROM roles r, permissions p 
WHERE r.code = 'ROLE_ADMIN' 
AND p.code IN ('menu:dashboard', 'menu:user:management', 'menu:role:management', 
               'user:read:profile', 'user:update:profile', 'user:delete:profile',
               'role:read:list', 'role:create', 'role:update', 'role:delete',
               'permission:read:list', 'permission:assign', 'permission:revoke',
               'system:config:read', 'system:log:read',
               'data:export', 'data:import', 'data:backup');

-- 为超级管理员角色分配所有权限
INSERT IGNORE INTO role_permissions (role_id, permission_id, permission_type) 
SELECT r.id, p.id, 1 
FROM roles r, permissions p 
WHERE r.code = 'ROLE_SUPER_ADMIN' 
AND p.status = 1;

-- 创建默认管理员用户（假设用户表已存在，需要先创建用户）
-- INSERT IGNORE INTO users (username, email, password_hash, status, user_type) 
-- VALUES ('admin', 'admin@example.com', '$2a$10$your_hashed_password', 1, 1);

-- 为管理员用户分配管理员角色
-- INSERT IGNORE INTO user_roles (user_id, role_id) 
-- SELECT u.id, r.id 
-- FROM users u, roles r 
-- WHERE u.username = 'admin' AND r.code = 'ROLE_ADMIN';

-- 为管理员用户分配超级管理员角色
-- INSERT IGNORE INTO user_roles (user_id, role_id) 
-- SELECT u.id, r.id 
-- FROM users u, roles r 
-- WHERE u.username = 'admin' AND r.code = 'ROLE_SUPER_ADMIN';