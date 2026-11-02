-- ============================================================================
-- V4：单聊会话防重 + 并发加固
--
-- 背景（对应存储审查里 6.2 的坑）：
--   ConversationService.openSingle 是「先查再建」，两个并发请求会同时查不到、
--   各自建出一个单聊会话，于是同一对用户出现两个会话，消息被劈成两半。
--
-- 修法：给单聊会话加一个派生唯一键 single_key = "小userId:大userId"。
--   · 群聊这一列留 NULL —— MySQL 和 H2 的唯一索引都允许多个 NULL，互不冲突
--   · 服务层另外加了同键互斥锁，正常路径根本不会走到唯一约束冲突
--   · 唯一索引只作为「跨进程/多实例」时的最后一道保险
--
-- 不做历史回填：老数据的 single_key 保持 NULL 不会被约束影响；
--   而 openSingle 仍然会先用 findIdsBetweenUsers 找到已有会话，不会重复创建。
-- ============================================================================

ALTER TABLE t_conversation ADD COLUMN single_key VARCHAR(64);

CREATE UNIQUE INDEX ux_conv_single_key ON t_conversation (single_key);
