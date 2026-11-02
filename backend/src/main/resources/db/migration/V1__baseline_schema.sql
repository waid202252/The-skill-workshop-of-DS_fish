-- ============================================================================
-- V1 基线：微聊 IM 的全部表结构
--
-- 约定：
--   1. 这是「数据库交付物」。别人 clone 仓库后，靠这份脚本就能重建出一模一样的库。
--   2. 从这里开始禁止使用 Hibernate 的 ddl-auto=update，表结构变更只能新增 V2__xxx.sql。
--   3. 索引单独用 CREATE INDEX 语句写，而不是内联 UNIQUE KEY，
--      这样同一份脚本在 MySQL/MariaDB 与 H2(MySQL 模式) 上都能执行。
--   4. 字符集在数据库层指定（utf8mb4），脚本内不写 ENGINE/CHARSET 以保持可移植。
-- ============================================================================

-- ---------------------------------------------------------------- 用户
CREATE TABLE t_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    username       VARCHAR(32)  NOT NULL,
    password       VARCHAR(100) NOT NULL,
    nickname       VARCHAR(32)  NOT NULL,
    avatar         VARCHAR(255),
    signature      VARCHAR(128),
    -- HUMAN / BOT。AI 伙伴就是一条 type=BOT 的普通用户记录
    type           VARCHAR(16),
    created_at     DATETIME     NOT NULL,
    last_active_at DATETIME,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_user_username ON t_user (username);

-- ---------------------------------------------------------------- 好友关系（双向各一行）
CREATE TABLE t_friend (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    friend_id  BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_friend_pair ON t_friend (user_id, friend_id);
CREATE INDEX idx_friend_user ON t_friend (user_id);

-- ---------------------------------------------------------------- 好友申请
CREATE TABLE t_friend_request (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    from_user_id BIGINT       NOT NULL,
    to_user_id   BIGINT       NOT NULL,
    message      VARCHAR(128),
    status       VARCHAR(16)  NOT NULL,
    created_at   DATETIME     NOT NULL,
    handled_at   DATETIME,
    PRIMARY KEY (id)
);

CREATE INDEX idx_fr_to_status ON t_friend_request (to_user_id, status);
CREATE INDEX idx_fr_from_status ON t_friend_request (from_user_id, status);

-- ---------------------------------------------------------------- 会话（单聊/群聊共用一张表）
CREATE TABLE t_conversation (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    type            VARCHAR(16) NOT NULL,
    name            VARCHAR(64),
    avatar          VARCHAR(255),
    owner_id        BIGINT,
    -- 会话内消息序号水位，发消息时锁行自增，保证 seq 严格递增
    max_seq         BIGINT      NOT NULL DEFAULT 0,
    last_message_id BIGINT,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_conv_updated ON t_conversation (updated_at);

-- ---------------------------------------------------------------- 会话成员（含已读位置）
CREATE TABLE t_conversation_member (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    joined_at       DATETIME    NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_member_conv_user ON t_conversation_member (conversation_id, user_id);
CREATE INDEX idx_member_user ON t_conversation_member (user_id);

-- ---------------------------------------------------------------- 消息
CREATE TABLE t_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    -- 会话内递增序号，游标分页与未读数的依据
    seq             BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    file_name       VARCHAR(255),
    file_size       BIGINT,
    -- 客户端生成的幂等 id，用于把服务端消息对应回本地气泡
    client_msg_id   VARCHAR(64),
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_msg_conv_seq ON t_message (conversation_id, seq);
CREATE INDEX idx_msg_conv_id ON t_message (conversation_id, id);
