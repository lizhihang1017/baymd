-- ============================================
-- BayMD 新功能建表脚本（幂等，可重复执行）
-- 覆盖 Phase 1 报告 / Phase 2 随访 / Phase 3 药物相互作用
-- 用法：psql -h localhost -U postgres -d baymd -f resources/database/init_feature_tables.sql
-- ============================================

-- ---------- Phase 2: 用户邮箱扩展 ----------
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS email VARCHAR(128);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS email_verified SMALLINT DEFAULT 0;
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS followup_enabled SMALLINT DEFAULT 1;

-- ---------- Phase 1: 医学检查报告表 ----------
CREATE TABLE IF NOT EXISTS t_medical_report (
    id            VARCHAR(20)    NOT NULL PRIMARY KEY,
    user_id       VARCHAR(20)    NOT NULL,
    file_name     VARCHAR(256)   NOT NULL,
    storage_key   VARCHAR(512)   NOT NULL,
    mime_type     VARCHAR(64),
    raw_text      TEXT,
    structured    JSONB,
    parse_status  VARCHAR(16)    DEFAULT 'PENDING',
    error_message TEXT,
    create_by     VARCHAR(20),
    update_by     VARCHAR(20),
    create_time   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_report_user ON t_medical_report (user_id, create_time DESC);

-- ---------- Phase 2: 随访任务表 ----------
CREATE TABLE IF NOT EXISTS t_followup_task (
    id              VARCHAR(20)   NOT NULL PRIMARY KEY,
    user_id         VARCHAR(20)   NOT NULL,
    conversation_id VARCHAR(20),
    topic           VARCHAR(64),
    question        VARCHAR(512)  NOT NULL,
    trigger_time    TIMESTAMP     NOT NULL,
    status          VARCHAR(16)   DEFAULT 'PENDING',
    channel         VARCHAR(16)   DEFAULT 'email',
    unsub_token     VARCHAR(64)   NOT NULL,
    lock_until      TIMESTAMP,
    sent_time       TIMESTAMP,
    answered_time   TIMESTAMP,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_followup_scan ON t_followup_task (status, trigger_time);
CREATE INDEX IF NOT EXISTS idx_followup_user ON t_followup_task (user_id, topic, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_followup_unsub ON t_followup_task (unsub_token);

-- ---------- Phase 3.2: 药物相互作用表 ----------
CREATE TABLE IF NOT EXISTS t_drug_interaction (
    id          VARCHAR(20)    NOT NULL PRIMARY KEY,
    drug_a      VARCHAR(128)   NOT NULL,
    drug_b      VARCHAR(128)   NOT NULL,
    severity    VARCHAR(16)    NOT NULL,
    description TEXT           NOT NULL,
    create_by   VARCHAR(20)    DEFAULT 'system',
    update_by   VARCHAR(20),
    create_time TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_drug_int_a ON t_drug_interaction (drug_a);
CREATE INDEX IF NOT EXISTS idx_drug_int_b ON t_drug_interaction (drug_b);

-- ---------- 语义记忆表（t_user_fact / t_user_episode 及向量表）----------
CREATE TABLE IF NOT EXISTS t_user_fact (
    id            VARCHAR(64) PRIMARY KEY,
    user_id       VARCHAR(64) NOT NULL,
    fact_type     VARCHAR(32) NOT NULL,
    fact_text     TEXT NOT NULL,
    confidence    FLOAT DEFAULT 1.0,
    source_msg_id VARCHAR(64),
    content_hash  VARCHAR(64),
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_fact_user ON t_user_fact(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_fact_hash ON t_user_fact(user_id, content_hash);

CREATE TABLE IF NOT EXISTS t_user_fact_vector (
    id        VARCHAR(64) PRIMARY KEY REFERENCES t_user_fact(id) ON DELETE CASCADE,
    embedding vector(1024)
);
CREATE INDEX IF NOT EXISTS idx_fact_vec_hnsw ON t_user_fact_vector USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS t_user_episode (
    id              VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    title           VARCHAR(256),
    summary         TEXT,
    topics          TEXT[],
    created_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_episode_user ON t_user_episode(user_id, created_at);

CREATE TABLE IF NOT EXISTS t_user_episode_vector (
    id        VARCHAR(64) PRIMARY KEY REFERENCES t_user_episode(id) ON DELETE CASCADE,
    embedding vector(1024)
);
CREATE INDEX IF NOT EXISTS idx_ep_vec_hnsw ON t_user_episode_vector USING hnsw (embedding vector_cosine_ops);

-- ---------- 运行时配置表（DB 覆盖 yaml，重启生效）----------
CREATE TABLE IF NOT EXISTS t_app_config (
    id           VARCHAR(20) NOT NULL PRIMARY KEY,
    section      VARCHAR(32) NOT NULL,
    config_value JSONB NOT NULL,
    updated_at   TIMESTAMP DEFAULT NOW(),
    deleted      SMALLINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_app_config_section ON t_app_config (section);
