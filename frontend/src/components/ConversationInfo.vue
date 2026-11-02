<script setup lang="ts">
import { computed } from 'vue'
import type { ConversationVO } from '@/types/api'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{ conversation: ConversationVO }>()

const emit = defineEmits<{ 'open-members': [] }>()

const isGroup = computed(() => props.conversation.type === 'GROUP')
const members = computed(() =>
  Array.isArray(props.conversation.members) ? props.conversation.members : [],
)
const visibleMembers = computed(() => members.value.slice(0, 9))
const onlineCount = computed(() => members.value.filter((member) => member.online).length)
const isOnline = computed(() => {
  if (!isGroup.value) return props.conversation.peer?.online === true
  return onlineCount.value > 0
})
const presenceText = computed(() => {
  if (!isGroup.value) return isOnline.value ? '在线' : '离线'
  return `${props.conversation.memberCount} 位成员 · ${onlineCount.value} 人在线`
})
</script>

<template>
  <aside class="conv-info">
    <header class="info-head">
      <h2>会话信息</h2>
    </header>

    <div class="info-scroll">
      <section class="profile">
        <UserAvatar
          :src="conversation.avatar"
          :name="conversation.name"
          :size="64"
          :square="isGroup"
          :online="isGroup ? null : (conversation.peer?.online ?? null)"
        />
        <h3>{{ conversation.name }}</h3>
        <p v-if="!isGroup && conversation.peer" class="account">@{{ conversation.peer.username }}</p>
        <span class="presence">
          <span class="presence-dot" :class="{ 'presence-dot--on': isOnline }"></span>
          {{ presenceText }}
        </span>
      </section>

      <section v-if="!isGroup && conversation.peer?.signature" class="block">
        <p class="block-label">个性签名</p>
        <p class="signature">{{ conversation.peer.signature }}</p>
      </section>

      <section class="block">
        <p class="block-label">会话</p>
        <dl class="meta-list">
          <div><dt>类型</dt><dd>{{ isGroup ? '群聊' : '单聊' }}</dd></div>
          <div><dt>成员</dt><dd>{{ conversation.memberCount }}</dd></div>
          <div><dt>未读</dt><dd>{{ conversation.unread }}</dd></div>
        </dl>
      </section>

      <section v-if="isGroup" class="block">
        <div class="block-head">
          <p class="block-label">群成员</p>
          <button class="block-action" type="button" @click="emit('open-members')">查看全部</button>
        </div>

        <div v-if="visibleMembers.length" class="member-grid">
          <div v-for="member in visibleMembers" :key="member.userId" class="member">
            <UserAvatar :src="member.avatar" :name="member.nickname" :size="32" :online="member.online" />
            <span class="member-name">{{ member.nickname }}</span>
          </div>
        </div>
        <p v-else class="block-empty">成员列表暂不可用</p>

        <button class="btn btn-sm manage-btn" type="button" @click="emit('open-members')">
          群成员管理
        </button>
      </section>

      <p class="info-hint">按 Esc 可关闭当前弹窗；消息、成员变更会实时同步。</p>
    </div>
  </aside>
</template>

<style scoped>
.conv-info {
  display: flex;
  flex-direction: column;
  flex: 0 0 300px;
  width: 300px;
  min-height: 0;
  border-left: 1px solid var(--color-border);
  background: var(--color-panel);
}

.info-head {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  height: 60px;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-border);
}

.info-head h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 13.5px;
  font-weight: var(--weight-semibold);
}

.info-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 18px 28px;
}

.profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}

.profile h3 {
  margin: 8px 0 0;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text);
  font-size: 16px;
  font-weight: var(--weight-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.presence {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-subtle);
  color: var(--color-text-muted);
  font-size: 11px;
}

.presence-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c3c9d2;
}

.presence-dot--on {
  background: var(--color-success);
}

.block {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--color-divider);
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.block-label {
  margin: 0 0 10px;
  color: var(--color-text-muted);
  font-size: 10.5px;
  font-weight: var(--weight-bold);
  letter-spacing: 1.2px;
  text-transform: uppercase;
}

.block-head .block-label {
  margin-bottom: 10px;
}

.block-action {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-primary-strong);
  font-size: 11.5px;
}

.signature {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.65;
  word-break: break-word;
}

.meta-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 0;
}

.meta-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.meta-list dt {
  color: var(--color-text-muted);
}

.meta-list dd {
  margin: 0;
  color: var(--color-text);
  font-weight: var(--weight-medium);
}

.member-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 8px;
}

.member {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.member-name {
  width: 100%;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 10.5px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-empty {
  margin: 0;
  padding: 16px 0;
  color: var(--color-text-muted);
  font-size: 11.5px;
  text-align: center;
}

.manage-btn {
  width: 100%;
  margin-top: 14px;
}

.info-hint {
  margin: 24px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--color-divider);
  color: var(--color-text-muted);
  font-size: 10.5px;
  line-height: 1.6;
}
</style>
