<script setup lang="ts">
import type { UIMessage } from '@/types/ui'
import { computed } from 'vue'
import { formatFileSize } from '@/utils/format'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{
  message: UIMessage
  isSelf: boolean
  showName: boolean
}>()

const emit = defineEmits<{
  preview: [url: string]
  retry: [clientMsgId: string]
  recall: [messageId: number]
}>()

/** 只有自己发的、已落库、未撤回、且是文本/图片/文件的消息才能撤回 */
const recallable = computed(
  () =>
    props.isSelf &&
    props.message.id !== null &&
    props.message.status === 'sent' &&
    props.message.recalled !== true &&
    props.message.type !== 'SYSTEM',
)

function handleRecall(): void {
  if (props.message.id === null) return
  if (!window.confirm('撤回后对方将看不到这条消息，确定撤回？')) return
  emit('recall', props.message.id)
}

function handleRetry(clientMsgId: string | null): void {
  if (clientMsgId) emit('retry', clientMsgId)
}
</script>

<template>
  <div v-if="message.type === 'SYSTEM'" class="system-message">
    <span>{{ message.content }}</span>
  </div>

  <div v-else class="message-row" :class="{ 'message-row--self': isSelf }">
    <UserAvatar
      :src="message.senderAvatar"
      :name="message.senderNickname"
      :size="36"
      class="message-avatar"
    />

    <div class="message-body">
      <p v-if="showName" class="sender-name">{{ message.senderNickname }}</p>

      <div v-if="message.recalled" class="recalled-line">
        <span>{{ isSelf ? '你撤回了一条消息' : `${message.senderNickname} 撤回了一条消息` }}</span>
      </div>

      <div v-else class="bubble-wrap">
        <div
          class="bubble"
          :class="{
            'bubble--self': isSelf,
            'bubble--image': message.type === 'IMAGE',
            'bubble--file': message.type === 'FILE',
          }"
        >
          <template v-if="message.type === 'TEXT'">
            <span class="text-content">{{ message.content }}</span>
          </template>

          <template v-else-if="message.type === 'IMAGE'">
            <button class="image-button" type="button" @click="emit('preview', message.content)">
              <img :src="message.content" alt="图片消息" loading="lazy" />
            </button>
          </template>

          <template v-else-if="message.type === 'FILE'">
            <a
              class="file-card"
              :href="message.content"
              :download="message.fileName ?? '文件'"
              target="_blank"
              rel="noopener"
            >
              <span class="file-icon">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6">
                  <path d="M6 3h8l4 4v14H6V3Z" stroke-linejoin="round" />
                  <path d="M14 3v4h4" stroke-linejoin="round" />
                </svg>
              </span>
              <span class="file-meta">
                <span class="file-name">{{ message.fileName || '未命名文件' }}</span>
                <span class="file-size">{{ formatFileSize(message.fileSize) }}</span>
              </span>
            </a>
          </template>
        </div>

        <span v-if="isSelf && message.status === 'sending'" class="status-sending" title="发送中">
          <span class="sending-spinner"></span>
        </span>
        <button
          v-else-if="isSelf && message.status === 'failed'"
          class="status-failed"
          type="button"
          title="发送失败，点击重发"
          @click="handleRetry(message.clientMsgId)"
        >
          !
        </button>
        <button
          v-if="recallable"
          class="recall-btn"
          type="button"
          title="撤回这条消息"
          @click="handleRecall"
        >
          撤回
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.system-message {
  display: flex;
  justify-content: center;
  margin: 8px 0;
}

.system-message span {
  max-width: 70%;
  padding: 4px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.72);
  color: var(--color-text-muted);
  font-size: 10.5px;
  line-height: 1.5;
  text-align: center;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-bottom: 14px;
}

.message-row--self {
  flex-direction: row-reverse;
}

.message-avatar {
  margin-top: 1px;
}

.message-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: min(62%, 560px);
}

.message-row--self .message-body {
  align-items: flex-end;
}

.sender-name {
  margin: 0 0 4px 2px;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.bubble-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.message-row--self .bubble-wrap {
  flex-direction: row-reverse;
}

.bubble {
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: 4px 14px 14px 14px;
  background: var(--color-panel);
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.55;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  word-break: break-word;
}

.bubble--self {
  border-color: transparent;
  border-radius: 14px 4px 14px 14px;
  background: var(--color-primary-strong);
  color: #fff;
}

.text-content {
  white-space: pre-wrap;
}

.bubble--image {
  padding: 4px;
  background: transparent;
  box-shadow: none;
}

.bubble--image.bubble--self {
  background: transparent;
}

.bubble--file {
  padding: 0;
  overflow: hidden;
}

.image-button {
  display: block;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  overflow: hidden;
  line-height: 0;
}

.image-button img {
  display: block;
  max-width: 240px;
  max-height: 240px;
  min-width: 60px;
  border-radius: 8px;
  object-fit: cover;
  background: #e7ebf0;
}

.file-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 208px;
  padding: 10px 12px;
  color: var(--color-text);
}

.bubble--self .file-card {
  color: #fff;
}

.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  flex: 0 0 auto;
}

.bubble--self .file-icon {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

.file-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}

.file-size {
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.62;
}

.status-sending {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
}

.sending-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(0, 0, 0, 0.12);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.status-failed {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--color-danger);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

/* 撤回后的占位：居中的灰色小字，不占气泡 */
.recalled-line {
  display: flex;
  margin: 2px 0;
}

.recalled-line span {
  padding: 2px 9px;
  border-radius: 9px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--color-text-muted);
  font-size: 11.5px;
  font-style: italic;
}

/* 撤回入口：默认隐藏，悬停消息行时才出现，避免界面噪音 */
.recall-btn {
  padding: 1px 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.15s ease;
  flex: 0 0 auto;
}

.message-row:hover .recall-btn {
  opacity: 1;
}

.recall-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--color-danger);
}
</style>
