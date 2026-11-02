<script setup lang="ts">
import { computed, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import type { FriendRequestStatus } from '@/types/api'
import { formatMessageTime } from '@/utils/time'
import UserAvatar from './UserAvatar.vue'

const emit = defineEmits<{ close: [] }>()

const chat = useChatStore()
const processingId = ref<number | null>(null)

const pendingIncoming = computed(() =>
  chat.incomingRequests.filter((request) => request.status === 'PENDING'),
)

const statusText: Record<FriendRequestStatus, string> = {
  PENDING: '等待验证',
  ACCEPTED: '已通过',
  REJECTED: '已拒绝',
}

async function accept(requestId: number): Promise<void> {
  if (processingId.value !== null) return
  processingId.value = requestId
  try {
    await chat.acceptFriendRequest(requestId)
  } finally {
    processingId.value = null
  }
}

async function reject(requestId: number): Promise<void> {
  if (processingId.value !== null) return
  processingId.value = requestId
  try {
    await chat.rejectFriendRequest(requestId)
  } finally {
    processingId.value = null
  }
}
</script>

<template>
  <section class="requests-panel">
    <header class="panel-header">
      <button class="back-button" type="button" title="返回" @click="emit('close')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
          <path d="m14.5 5-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <h2 class="panel-title">新的朋友</h2>
      <span v-if="pendingIncoming.length > 0" class="title-badge">{{ pendingIncoming.length }}</span>
    </header>

    <div class="panel-body">
      <section class="request-section">
        <div class="section-head">
          <h3>待处理申请</h3>
          <span class="section-count">{{ pendingIncoming.length }}</span>
        </div>

        <div v-if="chat.requestsLoading && pendingIncoming.length === 0" class="section-placeholder">
          <span class="spinner"></span>
          加载中…
        </div>

        <div v-else-if="pendingIncoming.length === 0" class="section-empty">暂时没有新的好友申请</div>

        <div v-for="request in pendingIncoming" :key="request.id" class="request-card">
          <UserAvatar :src="request.fromAvatar" :name="request.fromNickname" :size="44" />
          <div class="request-main">
            <p class="request-name">{{ request.fromNickname }}</p>
            <p class="request-message">{{ request.message || '请求添加你为好友' }}</p>
            <p class="request-time">{{ formatMessageTime(request.createdAt) }}</p>
          </div>
          <div class="request-actions">
            <button
              class="btn btn-primary btn-sm"
              type="button"
              :disabled="processingId !== null"
              @click="accept(request.id)"
            >
              通过
            </button>
            <button
              class="btn btn-sm"
              type="button"
              :disabled="processingId !== null"
              @click="reject(request.id)"
            >
              拒绝
            </button>
          </div>
        </div>
      </section>

      <section class="request-section">
        <div class="section-head">
          <h3>已发送</h3>
          <span class="section-count">{{ chat.outgoingRequests.length }}</span>
        </div>

        <div v-if="chat.outgoingRequests.length === 0" class="section-empty">你还没有发送过好友申请</div>

        <div v-for="request in chat.outgoingRequests" :key="request.id" class="request-card">
          <!-- 契约中 FriendRequestVO 没有 toAvatar，这里用昵称首字兜底 -->
          <UserAvatar :src="null" :name="request.toNickname" :size="44" />
          <div class="request-main">
            <p class="request-name">{{ request.toNickname }}</p>
            <p class="request-message">{{ request.message || '（未填写附言）' }}</p>
            <p class="request-time">{{ formatMessageTime(request.createdAt) }}</p>
          </div>
          <span class="request-status" :class="`request-status--${request.status.toLowerCase()}`">
            {{ statusText[request.status] }}
          </span>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.requests-panel {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  height: 60px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
}

.back-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-secondary);
}

.back-button:hover {
  background: rgba(0, 0, 0, 0.05);
}

.panel-title {
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
}

.title-badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: var(--radius-pill);
  background: var(--color-danger);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 24px 32px;
  background: var(--color-chat-bg);
}

.request-section {
  max-width: 720px;
  margin: 0 auto 22px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
}

.section-head h3 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
}

.section-count {
  color: var(--color-text-muted);
  font-size: 12px;
}

.section-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.spinner {
  width: 15px;
  height: 15px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.section-empty {
  padding: 26px 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
  text-align: center;
}

.request-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 2px;
  border-bottom: 1px solid var(--color-divider);
}

.request-card:last-child {
  border-bottom: none;
  padding-bottom: 4px;
}

.request-main {
  flex: 1;
  min-width: 0;
}

.request-name {
  margin: 0;
  color: var(--color-text);
  font-size: 13.5px;
  font-weight: 600;
}

.request-message {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  line-height: 1.5;
  word-break: break-word;
}

.request-time {
  margin: 5px 0 0;
  color: var(--color-text-muted);
  font-size: 11px;
}

.request-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 0 0 auto;
}

.request-actions .btn {
  min-width: 66px;
}

.request-status {
  flex: 0 0 auto;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  font-size: 11.5px;
}

.request-status--pending {
  background: rgba(255, 176, 32, 0.14);
  color: #b47500;
}

.request-status--accepted {
  background: rgba(47, 179, 68, 0.12);
  color: var(--color-success);
}

.request-status--rejected {
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-text-muted);
}
</style>
