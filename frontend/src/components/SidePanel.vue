<script setup lang="ts">
import { computed, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { formatUnread } from '@/utils/format'
import type { ConversationVO, FriendVO } from '@/types/api'
import type { NavMode } from '@/types/ui'
import ConversationList from './ConversationList.vue'
import ContactList from './ContactList.vue'

const props = defineProps<{ mode: NavMode }>()

const emit = defineEmits<{
  'update:mode': [mode: NavMode]
  selectConversation: [conversationId: number]
  openRequests: []
  openAddFriend: [keyword: string]
  createGroup: []
}>()

const chat = useChatStore()
const keyword = ref('')

const trimmedKeyword = computed(() => keyword.value.trim().toLowerCase())

const filteredConversations = computed<ConversationVO[]>(() => {
  const query = trimmedKeyword.value
  if (!query) return chat.conversations
  return chat.conversations.filter((conversation) => {
    if (conversation.name.toLowerCase().includes(query)) return true
    const last = conversation.lastMessage
    return last !== null && last.content.toLowerCase().includes(query)
  })
})

const filteredFriends = computed<FriendVO[]>(() => {
  const query = trimmedKeyword.value
  if (!query) return chat.friends
  return chat.friends.filter(
    (friend) =>
      friend.nickname.toLowerCase().includes(query) || friend.username.toLowerCase().includes(query),
  )
})

function handleConversationSelect(conversation: ConversationVO): void {
  emit('selectConversation', conversation.id)
}

function handleFriendSelect(friend: FriendVO): void {
  void chat.startConversationWithFriend(friend).then((conversationId) => {
    if (conversationId !== null) emit('selectConversation', conversationId)
  })
}

function openAddFriend(): void {
  emit('openAddFriend', keyword.value.trim())
}

function clearKeyword(): void {
  keyword.value = ''
}
</script>

<template>
  <aside class="side-panel">
    <header class="side-head">
      <h2>聊天</h2>
      <span class="side-total">{{ chat.conversations.length }} 个会话</span>
    </header>

    <div class="mode-tabs" role="tablist" aria-label="聊天分类">
      <button
        class="mode-tab"
        :class="{ 'mode-tab--active': props.mode === 'messages' }"
        type="button"
        role="tab"
        :aria-selected="props.mode === 'messages'"
        @click="emit('update:mode', 'messages')"
      >
        消息
        <span v-if="chat.totalUnread > 0" class="mode-badge">{{ formatUnread(chat.totalUnread) }}</span>
      </button>
      <button
        class="mode-tab"
        :class="{ 'mode-tab--active': props.mode === 'contacts' }"
        type="button"
        role="tab"
        :aria-selected="props.mode === 'contacts'"
        @click="emit('update:mode', 'contacts')"
      >
        联系人
        <span v-if="chat.pendingRequestCount > 0" class="mode-badge">{{ formatUnread(chat.pendingRequestCount) }}</span>
      </button>
    </div>

    <div class="side-search">
      <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true">
        <circle cx="11" cy="11" r="6.5" />
        <path d="m16.2 16.2 4 4" stroke-linecap="round" />
      </svg>
      <input
        v-model="keyword"
        type="text"
        :placeholder="props.mode === 'messages' ? '搜索会话' : '搜索联系人'"
        aria-label="搜索"
      />
      <button v-if="keyword" class="clear-btn" type="button" aria-label="清空搜索" @click="clearKeyword">×</button>
    </div>

    <button v-if="trimmedKeyword" class="search-user-entry" type="button" @click="openAddFriend">
      <span class="search-entry-icon">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <circle cx="10" cy="8.4" r="3.2" />
          <path d="M4 19.2c.5-3.3 2.8-5.1 6-5.1 1 0 2 .2 2.8.6" stroke-linecap="round" />
          <path d="M17.6 13.4v5.2M15 16h5.2" stroke-linecap="round" />
        </svg>
      </span>
      <span class="search-entry-text">搜索用户「{{ keyword.trim() }}」并加好友</span>
      <span class="search-entry-arrow">›</span>
    </button>

    <ConversationList
      v-if="props.mode === 'messages'"
      :conversations="filteredConversations"
      :active-id="chat.activeConversationId"
      :loading="chat.conversationsLoading"
      @select="handleConversationSelect"
    />

    <ContactList
      v-else
      :friends="filteredFriends"
      :pending-count="chat.pendingRequestCount"
      :loading="chat.friendsLoading"
      @select="handleFriendSelect"
      @open-requests="emit('openRequests')"
      @create-group="emit('createGroup')"
      @search-users="openAddFriend"
    />
  </aside>
</template>

<style scoped>
.side-panel {
  display: flex;
  flex-direction: column;
  flex: 0 0 300px;
  width: 300px;
  min-height: 0;
  border-right: 1px solid var(--color-border);
  background: var(--color-panel);
}

.side-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  height: 60px;
  padding: 0 18px;
}

.side-head h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 15px;
  font-weight: var(--weight-bold);
}

.side-total {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.mode-tabs {
  display: flex;
  gap: 4px;
  flex: 0 0 auto;
  padding: 0 12px 10px;
}

.mode-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex: 1;
  height: 32px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  font-weight: var(--weight-medium);
  transition:
    background var(--transition),
    color var(--transition),
    border-color var(--transition);
}

.mode-tab:hover {
  background: var(--color-bg-subtle);
  color: var(--color-text);
}

.mode-tab--active {
  border-color: rgba(18, 183, 245, 0.28);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-weight: var(--weight-semibold);
}

.mode-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-danger);
  color: #fff;
  font-size: 9.5px;
  font-weight: var(--weight-bold);
  line-height: 1;
}

.side-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  height: 36px;
  margin: 0 12px 10px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
  color: var(--color-text-muted);
  transition:
    background var(--transition),
    border-color var(--transition),
    box-shadow var(--transition);
}

.side-search:focus-within {
  border-color: var(--color-primary);
  background: var(--color-panel);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.side-search input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--color-text);
  font-size: 12.5px;
}

.side-search input::placeholder {
  color: var(--color-text-muted);
}

.clear-btn {
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(100, 116, 139, 0.16);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1;
}

.search-user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  margin: 0 12px 8px;
  padding: 8px 10px;
  border: 1px dashed rgba(18, 183, 245, 0.45);
  border-radius: var(--radius-md);
  background: var(--color-primary-softer);
  text-align: left;
  transition: background var(--transition);
}

.search-user-entry:hover {
  background: var(--color-primary-soft);
}

.search-entry-icon {
  display: inline-flex;
  color: var(--color-primary-strong);
}

.search-entry-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--color-primary-strong);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-entry-arrow {
  color: var(--color-primary-strong);
  font-size: 15px;
  line-height: 1;
}

@media (max-width: 980px) {
  .side-panel {
    flex: 0 0 270px;
    width: 270px;
  }
}

@media (max-width: 760px) {
  .side-panel {
    flex: 1;
    width: 100%;
    border-right: none;
  }
}
</style>
