-- ============================================
-- BayMD 医学知识库初始化脚本
-- ============================================
-- 用法：在已执行 schema_pg.sql 的数据库中执行此脚本
--   psql -h localhost -U postgres -d baymd -f resources/database/init_medical_kb.sql

-- 1. 创建医学知识库
INSERT INTO t_knowledge_base (id, name, embedding_model, collection_name, created_by, updated_by, create_time, update_time, deleted)
VALUES ('9100000000000000001', '医学科知识库', 'qwen-emb-8b', 'medical-kb-store', 'system', 'system', NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

-- 2. 创建医学示例文档（待上传文件后自动分块索引）
-- 注：文档需要通过 API 上传，此处仅预建文档记录供参考
-- 上传命令示例：
-- curl -X POST "http://localhost:9090/api/baymd/knowledge-document/upload" \
--   -F "kbId=9100000000000000001" \
--   -F "file=@resources/demo/dept_recommend_demo.md"

-- 3. 插入示例问题（可选）
INSERT INTO t_sample_question (id, title, description, question, create_time, update_time, deleted) VALUES
('9100000000000000101', '科室推荐', '头疼症状咨询', '头疼应该挂什么科？', NOW(), NOW(), 0),
('9100000000000000102', '科室推荐', '胃部不适咨询', '胃不舒服看哪个科室？', NOW(), NOW(), 0),
('9100000000000000103', '科室推荐', '皮肤问题咨询', '皮肤起红疹去哪个科？', NOW(), NOW(), 0),
('9100000000000000104', '科室推荐', '腰部疼痛咨询', '腰疼挂什么科？', NOW(), NOW(), 0),
('9100000000000000105', '科室推荐', '儿童症状咨询', '小孩发烧去哪个科？', NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;
