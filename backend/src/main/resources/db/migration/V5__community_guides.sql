-- ============================================================================
-- V5：配方社区
--
-- 设计取舍：
--   1. 指南/配方统一放 t_community_guide，用 type 区分 GUIDE / RECIPE。
--      浅度用户先消费配方，后续再扩展问答，不单独造一套论坛表。
--   2. 关联技能存快照（full_name / name / owner / html_url），不强制外键：
--      指南是编辑内容，即使技能尚未被本地缓存，也能正常展示和跳转。
--   3. 反馈按 (guide_id, user_id) 唯一，重复点击只更新态度，不重复计数。
--   4. 问题表保存 AI 首答与“有帮助/没帮助”，用于后续把真实问题沉淀成指南。
-- ============================================================================

CREATE TABLE t_community_guide (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    type              VARCHAR(20)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    summary           VARCHAR(500) NOT NULL,
    content           TEXT         NOT NULL,
    prompt_text       TEXT,
    emoji             VARCHAR(16),
    tags              VARCHAR(512),
    difficulty        VARCHAR(20),
    duration          VARCHAR(50),
    cost              VARCHAR(100),
    ai_generated      BOOLEAN      NOT NULL DEFAULT FALSE,
    review_status     VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    reviewed_by       VARCHAR(100),
    helpful_count     INT          NOT NULL DEFAULT 0,
    not_helpful_count INT          NOT NULL DEFAULT 0,
    view_count        INT          NOT NULL DEFAULT 0,
    published_at      DATETIME,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_community_guide_type ON t_community_guide (type);
CREATE INDEX idx_community_guide_published ON t_community_guide (published_at);
CREATE INDEX idx_community_guide_helpful ON t_community_guide (helpful_count);

CREATE TABLE t_community_guide_skill (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    guide_id      BIGINT       NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    owner         VARCHAR(100) NOT NULL,
    html_url      VARCHAR(512) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_community_guide_skill_guide ON t_community_guide_skill (guide_id);
CREATE INDEX idx_community_guide_skill_full_name ON t_community_guide_skill (full_name);

CREATE TABLE t_community_feedback (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    guide_id   BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    helpful    BOOLEAN  NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_community_feedback_guide_user ON t_community_feedback (guide_id, user_id);
CREATE INDEX idx_community_feedback_guide ON t_community_feedback (guide_id);

CREATE TABLE t_community_question (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    question   VARCHAR(1000) NOT NULL,
    answer     TEXT          NOT NULL,
    status     VARCHAR(20)   NOT NULL DEFAULT 'AI_ANSWERED',
    helpful    BOOLEAN,
    created_at DATETIME      NOT NULL,
    updated_at DATETIME      NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_community_question_user ON t_community_question (user_id);
CREATE INDEX idx_community_question_created ON t_community_question (created_at);
