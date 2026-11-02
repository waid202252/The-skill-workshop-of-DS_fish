-- ============================================================================
-- V3：技能市场
--
-- 背景：产品定位从「IM + AI 伙伴」转向「Skill 市场客户端 + 聊天为辅」。
--
-- 设计取舍：
--   1. t_skill 是 GitHub 仓库信息的【本地缓存】。
--      必须有缓存，原因很硬：GitHub 未认证配额只有 search 10 次/分钟、core 60 次/小时，
--      直接透传会几秒就被限流。缓存后同一查询在 TTL 内不再打 GitHub。
--   2. github_id 与 full_name 都建唯一索引：前者防止重命名导致重复入库，
--      后者保证 owner/repo 只有一个本地记录。
--   3. topics 用逗号分隔的 VARCHAR 而不是 JSON 类型：
--      H2（本地开发）和 MySQL 都要能跑，避免方言差异。
--   4. 安装关系单独建表，不放到 t_skill 上——技能是全局共享的，安装是每人一份。
-- ============================================================================

CREATE TABLE t_skill (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    -- GitHub 的仓库 id，稳定的数字标识
    github_id     BIGINT        NOT NULL,
    -- owner/repo 形式，例如 anthropics/skills
    full_name     VARCHAR(200)  NOT NULL,
    owner         VARCHAR(100)  NOT NULL,
    owner_avatar  VARCHAR(255),
    name          VARCHAR(150)  NOT NULL,
    description   VARCHAR(1024),
    stars         BIGINT        NOT NULL DEFAULT 0,
    forks         BIGINT        NOT NULL DEFAULT 0,
    language      VARCHAR(60),
    -- 逗号分隔的 topics，例如 "ai,video-generation,skill"
    topics        VARCHAR(512),
    html_url      VARCHAR(512)  NOT NULL,
    homepage      VARCHAR(512),
    default_branch VARCHAR(100),
    -- 最后一次从 GitHub 同步的时间，用于判断缓存是否过期
    fetched_at    DATETIME      NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_skill_github_id ON t_skill (github_id);
CREATE UNIQUE INDEX ux_skill_full_name ON t_skill (full_name);
CREATE INDEX idx_skill_stars ON t_skill (stars);
CREATE INDEX idx_skill_fetched ON t_skill (fetched_at);

CREATE TABLE t_skill_install (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    skill_id   BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_install_user_skill ON t_skill_install (user_id, skill_id);
CREATE INDEX idx_install_user ON t_skill_install (user_id);
