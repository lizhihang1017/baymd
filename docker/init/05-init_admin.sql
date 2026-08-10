-- ============================================================
-- 默认管理员账号种子数据(幂等,可重复执行)
-- 默认账号: admin / admin123456
-- 登录后请立即在「系统设置 → 用户管理」中修改密码
-- ============================================================
INSERT INTO t_user (id, username, password, role, email, create_time, update_time, deleted)
SELECT '1', 'admin', 'admin123456', 'admin', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'admin' AND deleted = 0);
