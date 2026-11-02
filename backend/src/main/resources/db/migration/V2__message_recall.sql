-- ============================================================================
-- V2：消息撤回
--
-- 设计取舍：
--   1. 不物理删除。保留行，只把 content 清空并打 recalled 标记。
--      这样 seq 序列不出现空洞，分页游标与未读数计算都不受影响。
--   2. 不重算未读数。撤回的消息仍然占一个 seq，对方未读数不变——
--      这符合直觉：你确实收到过一条消息，只是内容不可见了。
--   3. recalled_at 便于将来做审计或"超过 N 天不可撤回"的策略。
-- ============================================================================

ALTER TABLE t_message ADD COLUMN recalled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE t_message ADD COLUMN recalled_at DATETIME;
