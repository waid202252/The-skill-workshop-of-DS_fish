<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { ws } from '@/services/ws'
import { getToken } from '@/services/token'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import type { NavMode } from '@/types/ui'
import AppTopbar from '@/components/AppTopbar.vue'
import SidePanel from '@/components/SidePanel.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import ConversationInfo from '@/components/ConversationInfo.vue'
import EmptyState from '@/components/EmptyState.vue'
import FriendRequestsPanel from '@/components/FriendRequestsPanel.vue'
import AddFriendPanel from '@/components/AddFriendPanel.vue'
import GroupCreateModal from '@/components/GroupCreateModal.vue'
import GroupMembersModal from '@/components/GroupMembersModal.vue'

type RightPanel = 'chat' | 'requests' | 'addFriend'

const auth = useAuthStore()
const chat = useChatStore()
const router = useRouter()

const mode = ref<NavMode>('messages')
const rightPanel = ref<RightPanel>('chat')
const addFriendKeyword = ref('')
const showGroupModal = ref(false)
const showMembers = ref(false)

function openConversation(conversationId: number): void {
  chat.setActive(conversationId)
  rightPanel.value = 'chat'
}

function openRequests(): void {
  rightPanel.value = 'requests'
}

function openAddFriend(keyword: string): void {
  addFriendKeyword.value = keyword
  rightPanel.value = 'addFriend'
}

function handleGroupCreated(conversationId: number): void {
  showGroupModal.value = false
  chat.setActive(conversationId)
  mode.value = 'messages'
  rightPanel.value = 'chat'
}

function handleLogout(): void {
  ws.disconnect()
  chat.reset()
  auth.clearSession()
  void router.replace('/login')
}

/** 切换会话时关闭上一个会话的成员弹窗，避免内容串台 */
watch(
  () => chat.activeConversationId,
  () => {
    showMembers.value = false
  },
)

onMounted(() => {
  // 先绑定 WS 事件，再发起连接
  chat.initRealtime()
  const token = getToken()
  if (token) ws.connect(token)
  void chat.bootstrap().catch((error: unknown) => {
    showToast(errorMessage(error), 'error')
  })
})
</script>

<template>
  <div class="chat-page">
    <AppTopbar active="chat" :show-search="false" @logout="handleLogout" />

    <div class="chat-body" :class="{ 'chat-body--active': chat.activeConversation || rightPanel !== 'chat' }">
      <SidePanel
        :mode="mode"
        @update:mode="mode = $event"
        @select-conversation="openConversation"
        @open-requests="openRequests"
        @open-add-friend="openAddFriend"
        @create-group="showGroupModal = true"
      />

      <main class="main-area">
        <FriendRequestsPanel v-if="rightPanel === 'requests'" @close="rightPanel = 'chat'" />

        <AddFriendPanel
          v-else-if="rightPanel === 'addFriend'"
          :keyword="addFriendKeyword"
          @close="rightPanel = 'chat'"
        />

        <ChatPanel
          v-else-if="chat.activeConversation"
          :key="`${chat.activeConversation.id}-${chat.syncVersion}`"
          :conversation="chat.activeConversation"
          @open-members="showMembers = true"
          @back="chat.setActive(null)"
        />

        <EmptyState v-else />
      </main>

      <ConversationInfo
        v-if="chat.activeConversation && rightPanel === 'chat'"
        :conversation="chat.activeConversation"
        @open-members="showMembers = true"
      />
    </div>

    <GroupCreateModal v-if="showGroupModal" @close="showGroupModal = false" @created="handleGroupCreated" />

    <GroupMembersModal
      v-if="showMembers && chat.activeConversation"
      :conversation="chat.activeConversation"
      @close="showMembers = false"
      @left="showMembers = false"
    />
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-chat-bg);
}

.chat-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--color-chat-bg);
}

.main-area {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  height: 100%;
}

/* 三栏在中等宽度下先收起信息栏，保证消息区可读 */
@media (max-width: 1200px) {
  .chat-body :deep(.conv-info) {
    display: none;
  }
}

@media (max-width: 760px) {
  .chat-body > :deep(.side-panel) {
    flex: 1;
    width: 100%;
  }

  .main-area {
    display: none;
  }

  .chat-body--active .main-area {
    display: flex;
  }

  .chat-body--active > :deep(.side-panel) {
    display: none;
  }
}
</style>
