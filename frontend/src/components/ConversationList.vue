<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import type { ConversationVO } from '@/types/api'
import { formatConversationTime } from '@/utils/time'
import { formatLastMessage, formatUnread } from '@/utils/format'
import UserAvatar from './UserAvatar.vue'

defineProps<{
  conversations: ConversationVO[]
  activeId: number | null
  loading: boolean
}>()

const emit = defineEmits<{
  select: [conversation: ConversationVO]
}>()

const auth = useAuthStore()

function summarize(conversation: ConversationVO): string {
  const last = conversation.lastMessage
  if (!last) return '暂无消息'
  const body = formatLastMessage(last.type, last.content, last.fileName)
  const prefix = auth.user && last.senderId === auth.user.id ? '我: ' : ''
  return `${prefix}${body}`
}

function displayTime(conversation: ConversationVO): string {
  return formatConversationTime(conversation.lastMessage?.createdAt ?? conversation.updatedAt)
}

function onlineOf(conversation: ConversationVO): boolean | null {
  if (conversation.type === 'SINGLE' && conversation.peer) return conversation.peer.online
  return null
}
</script>

<template>
  <div class="conversation-list">
    <div v-if="loading && conversations.length === 0" class="list-placeholder">
      <span class="spinner"></span>
      加载会话中…
    </div>

    <template v-else>
      <button
        v-for="conversation in conversations"
        :key="conversation.id"
        class="conversation-item"
        :class="{ 'conversation-item--active': conversation.id === activeId }"
        type="button"
        @click="emit('select', conversation)"
      >
        <UserAvatar
          :src="conversation.avatar"
          :name="conversation.name"
          :size="44"
          :online="onlineOf(conversation)"
          :square="conversation.type === 'GROUP'"
        />
        <div class="conversation-main">
          <div class="conversation-top">
            <span class="conversation-name">{{ conversation.name }}</span>
            <span class="conversation-time">{{ displayTime(conversation) }}</span>
          </div>
          <div class="conversation-bottom">
            <span class="conversation-summary">{{ summarize(conversation) }}</span>
            <Transition name="unread">
              <span v-if="conversation.unread > 0" :key="conversation.unread" class="conversation-unread">
                {{ formatUnread(conversation.unread) }}
              </span>
            </Transition>
          </div>
        </div>
      </button>

      <div v-if="conversations.length === 0" class="list-empty">
        <p>还没有会话</p>
        <p class="list-empty-hint">去「联系人」发起一场对话吧</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 12px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  margin-bottom: 2px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  text-align: left;
  transition:
    background var(--transition),
    border-color var(--transition);
}

.conversation-item:hover {
  background: var(--color-bg-subtle);
}

.conversation-item--active,
.conversation-item--active:hover {
  border-color: rgba(18, 183, 245, 0.24);
  background: var(--color-primary-softer);
}

.conversation-main {
  flex: 1;
  min-width: 0;
}

.conversation-top,
.conversation-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conversation-name {
  flex: 1;
  min-width: 0;
  color: var(--color-text);
  font-size: 13px;
  font-weight: var(--weight-semibold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-time {
  flex: 0 0 auto;
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.conversation-bottom {
  margin-top: 3px;
}

.conversation-summary {
  flex: 1;
  min-width: 0;
  color: var(--color-text-muted);
  font-size: 11.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-unread {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--color-danger);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1;
}

.unread-enter-active,
.unread-leave-active {
  transition:
    transform 0.2s ease,
    opacity 0.2s ease;
}

.unread-enter-from,
.unread-leave-to {
  transform: scale(0.5);
  opacity: 0;
}

.list-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.list-empty {
  padding: 48px 12px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

.list-empty p {
  margin: 0;
}

.list-empty-hint {
  margin-top: 6px !important;
  font-size: 12px;
}
</style>
