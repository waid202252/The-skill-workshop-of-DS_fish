<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import type { ConversationVO } from '@/types/api'
import type { AttachmentPayload, MessageListItem } from '@/types/ui'
import { formatMessageTime, shouldShowTime } from '@/utils/time'
import UserAvatar from './UserAvatar.vue'
import MessageBubble from './MessageBubble.vue'
import ChatInput from './ChatInput.vue'
import ImagePreview from './ImagePreview.vue'

const props = defineProps<{ conversation: ConversationVO }>()

const emit = defineEmits<{
  'open-members': []
  back: []
}>()

const chat = useChatStore()
const auth = useAuthStore()

const listRef = ref<HTMLDivElement | null>(null)
const nearBottom = ref(true)
const loadingMore = ref(false)
const loadingInitial = ref(false)
const previewUrl = ref<string | null>(null)
let forceScroll = false

/** 对方是否为内置 AI 伙伴账号 */
const isBotPeer = computed(
  () => props.conversation.type === 'SINGLE' && props.conversation.peer?.type === 'BOT',
)

/** 未接入真实模型时明确提示，避免让人误以为在跟真模型对话 */
const aiBadgeText = computed(() => (chat.aiStatus?.mock ? '模拟模式' : 'AI 伙伴'))

const messages = computed(() => chat.buildDisplayMessages(props.conversation.id))
const hasMore = computed(() => chat.hasMore(props.conversation.id))
const loadingOlder = computed(() => chat.isLoadingOlder(props.conversation.id))

/** 按时间间隔（>5 分钟）插入分割线的渲染列表 */
const listItems = computed<MessageListItem[]>(() => {
  const items: MessageListItem[] = []
  let previousTime: string | null = null
  const myId = auth.user?.id ?? null
  for (const message of messages.value) {
    if (shouldShowTime(previousTime, message.createdAt)) {
      items.push({
        kind: 'time',
        key: `time-${message.key}`,
        label: formatMessageTime(message.createdAt),
      })
    }
    previousTime = message.createdAt
    items.push({
      kind: 'message',
      key: message.key,
      message,
      isSelf: message.senderId === myId,
      showName: props.conversation.type === 'GROUP' && message.type !== 'SYSTEM' && message.senderId !== myId,
    })
  }
  return items
})

const headerStatus = computed(() => {
  if (props.conversation.type === 'SINGLE') {
    return props.conversation.peer?.online ? '在线' : '离线'
  }
  const members = Array.isArray(props.conversation.members) ? props.conversation.members : []
  const onlineCount = members.filter((member) => member.online).length
  return `${props.conversation.memberCount} 位成员 · ${onlineCount} 人在线`
})

const isOnline = computed(() => {
  if (props.conversation.type === 'SINGLE') return props.conversation.peer?.online === true
  const members = Array.isArray(props.conversation.members) ? props.conversation.members : []
  return members.some((member) => member.online)
})

function scrollToBottom(smooth = false): void {
  const el = listRef.value
  if (!el) return
  el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  nearBottom.value = true
}

function updateNearBottom(): void {
  const el = listRef.value
  if (!el) return
  nearBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 120
}

/** 上拉加载更早消息：用 scrollHeight 差值维持视觉锚点 */
async function loadMore(): Promise<void> {
  if (loadingMore.value || !hasMore.value) return
  const el = listRef.value
  if (!el) return
  loadingMore.value = true
  const previousHeight = el.scrollHeight
  const previousTop = el.scrollTop
  try {
    await chat.loadOlderMessages(props.conversation.id)
    await nextTick()
    el.scrollTop = previousTop + (el.scrollHeight - previousHeight)
    updateNearBottom()
  } finally {
    loadingMore.value = false
  }
}

function handleScroll(): void {
  updateNearBottom()
  const el = listRef.value
  if (el && el.scrollTop <= 24) void loadMore()
}

/** 图片加载完成后，如果用户本来就在底部则保持贴底 */
function handleMediaLoad(): void {
  if (!nearBottom.value) return
  void nextTick(() => scrollToBottom())
}

/** 会话切换：加载消息 + 滚到底部 + 清未读 */
watch(
  () => props.conversation.id,
  async (conversationId) => {
    previewUrl.value = null
    loadingInitial.value = true
    forceScroll = true
    try {
      await chat.ensureMessages(conversationId)
      await nextTick()
      if (props.conversation.id !== conversationId) return
      scrollToBottom()
      await chat.markRead(conversationId)
    } finally {
      loadingInitial.value = false
      forceScroll = false
    }
  },
  { immediate: true },
)

/** 消息数量变化：用户贴底或自己发消息时才自动滚动 */
watch(
  () => messages.value.length,
  async (next, previous) => {
    if (next <= previous) return
    await nextTick()
    if (forceScroll || nearBottom.value) scrollToBottom()
    forceScroll = false
  },
)

function handleSend(text: string): void {
  forceScroll = true
  void chat.sendText(props.conversation.id, text)
}

function handleAttachment(payload: AttachmentPayload): void {
  forceScroll = true
  void chat.sendAttachment(props.conversation.id, payload)
}

function handleRetry(clientMsgId: string): void {
  forceScroll = true
  chat.retryPending(clientMsgId)
}

/** 撤回：确认框在 MessageBubble 里处理，这里只负责发起 */
function handleRecall(messageId: number): void {
  void chat.recallMessage(props.conversation.id, messageId)
}

function handleResize(): void {
  if (nearBottom.value) scrollToBottom()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <section class="chat-panel">
    <header class="chat-header">
      <button class="mobile-back" type="button" aria-label="返回会话列表" @click="emit('back')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="m14.5 5-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>

      <UserAvatar
        :src="conversation.avatar"
        :name="conversation.name"
        :size="38"
        :square="conversation.type === 'GROUP'"
        :online="conversation.type === 'SINGLE' ? (conversation.peer?.online ?? null) : null"
      />
      <div class="chat-title">
        <h2 class="chat-name">{{ conversation.name }}</h2>
        <p class="chat-status">
          <span class="status-dot" :class="{ 'status-dot--online': isOnline }"></span>
          {{ headerStatus }}
        </p>
      </div>
      <span class="header-spacer"></span>
      <span v-if="isBotPeer" class="bot-chip" :title="`模型链路：${chat.aiStatus?.provider ?? '未知'}`">
        {{ aiBadgeText }}
      </span>
      <span v-if="chat.wsStatus !== 'open'" class="reconnect-chip">
        <span class="reconnect-spinner"></span>
        {{ chat.wsStatus === 'connecting' || chat.wsStatus === 'idle' ? '连接中…' : '连接已断开' }}
      </span>
      <button
        v-if="conversation.type === 'GROUP'"
        class="members-btn"
        type="button"
        title="查看和管理群成员"
        @click="emit('open-members')"
      >
        群成员 {{ conversation.memberCount }}
      </button>
    </header>

    <div ref="listRef" class="message-list" @scroll.passive="handleScroll" @load.capture="handleMediaLoad">
      <div class="history-slot">
        <div v-if="loadingMore || loadingOlder" class="history-indicator">
          <span class="history-spinner"></span>
          加载更早的消息…
        </div>
        <div v-else-if="hasMore && messages.length > 0" class="history-hint">上滑加载更早的消息</div>
        <div v-else-if="messages.length > 0" class="history-hint history-hint--end">已经到会话的起点了</div>
      </div>

      <template v-for="item in listItems" :key="item.key">
        <div v-if="item.kind === 'time'" class="time-divider">
          <span>{{ item.label }}</span>
        </div>
        <MessageBubble
          v-else
          :message="item.message"
          :is-self="item.isSelf"
          :show-name="item.showName"
          @preview="previewUrl = $event"
          @retry="handleRetry"
          @recall="handleRecall"
        />
      </template>

      <div v-if="!loadingInitial && messages.length === 0" class="chat-empty">
        <p>还没有消息</p>
        <p class="chat-empty-hint">发个消息，和 TA 打个招呼吧</p>
      </div>
    </div>

    <ChatInput @send="handleSend" @attachment="handleAttachment" />

    <ImagePreview v-if="previewUrl" :url="previewUrl" @close="previewUrl = null" />
  </section>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  height: 100%;
  background: var(--color-chat-bg);
}

/* ---------------- 会话头 ---------------- */
.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  height: 60px;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-panel);
}

.mobile-back {
  display: none;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-left: -4px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
}

.mobile-back:hover {
  background: var(--color-bg-subtle);
}

.chat-title {
  min-width: 0;
}

.chat-name {
  margin: 0;
  overflow: hidden;
  color: var(--color-text);
  font-size: 15px;
  font-weight: var(--weight-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-status {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 3px 0 0;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c3c9d2;
}

.status-dot--online {
  background: var(--color-success);
}

.header-spacer {
  flex: 1;
  min-width: 0;
}

.reconnect-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  background: var(--color-warning-soft);
  color: #9a580a;
  font-size: 10.5px;
}

.reconnect-spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid rgba(154, 88, 10, 0.25);
  border-top-color: #9a580a;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.bot-chip {
  flex: 0 0 auto;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  background: rgba(124, 58, 237, 0.1);
  color: #7c3aed;
  font-size: 10.5px;
  font-weight: var(--weight-medium);
  white-space: nowrap;
}

.members-btn {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-panel);
  color: var(--color-text-secondary);
  font-size: 11.5px;
  white-space: nowrap;
  transition:
    border-color var(--transition),
    color var(--transition);
}

.members-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
}

/* ---------------- 消息列表 ---------------- */
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 22px 18px;
  background:
    radial-gradient(80% 50% at 50% 0%, rgba(18, 183, 245, 0.035) 0%, rgba(18, 183, 245, 0) 70%),
    var(--color-chat-bg);
  overscroll-behavior: contain;
}

.history-slot {
  display: flex;
  justify-content: center;
  min-height: 24px;
  padding: 6px 0 10px;
}

.history-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.history-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(100, 116, 139, 0.18);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.history-hint {
  color: var(--color-text-muted);
  font-size: 10.5px;
  opacity: 0.85;
}

.history-hint--end {
  opacity: 0.55;
}

.time-divider {
  display: flex;
  justify-content: center;
  margin: 12px 0;
}

.time-divider span {
  padding: 3px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.8);
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 100%;
  min-height: 220px;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.chat-empty p {
  margin: 0;
}

.chat-empty-hint {
  opacity: 0.75;
}

@media (max-width: 760px) {
  .chat-header {
    gap: 8px;
    padding: 0 12px;
  }

  .mobile-back {
    display: inline-flex;
  }

  .chat-name {
    font-size: 14px;
  }

  .members-btn,
  .bot-chip {
    display: none;
  }

  .message-list {
    padding: 8px 14px 14px;
  }
}
</style>
