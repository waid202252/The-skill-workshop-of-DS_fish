<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { conversationApi } from '@/services/api'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import type { ConversationVO } from '@/types/api'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{ conversation: ConversationVO }>()

const emit = defineEmits<{
  close: []
  left: []
}>()

const auth = useAuthStore()
const chat = useChatStore()

const members = computed(() => (Array.isArray(props.conversation.members) ? props.conversation.members : []))
const myId = computed(() => auth.user?.id ?? null)
const myRole = computed(() => members.value.find((member) => member.userId === myId.value)?.role ?? null)
/** 群主与管理员才能邀请/移除他人 */
const canManageOthers = computed(() => myRole.value === 'OWNER' || myRole.value === 'ADMIN')

const inviting = ref(false)
const inviteKeyword = ref('')
const selectedIds = ref<number[]>([])
const busy = ref(false)

/** 还不是群成员的好友 */
const inviteCandidates = computed(() => {
  const existing = new Set(members.value.map((member) => member.userId))
  const query = inviteKeyword.value.trim().toLowerCase()
  return chat.friends
    .filter((friend) => !existing.has(friend.userId) && friend.userId !== myId.value)
    .filter(
      (friend) =>
        !query ||
        friend.nickname.toLowerCase().includes(query) ||
        friend.username.toLowerCase().includes(query),
    )
})

const roleLabel = (role: string): string => {
  if (role === 'OWNER') return '群主'
  if (role === 'ADMIN') return '管理员'
  return ''
}

function toggleSelected(userId: number): void {
  selectedIds.value = selectedIds.value.includes(userId)
    ? selectedIds.value.filter((id) => id !== userId)
    : [...selectedIds.value, userId]
}

async function addSelected(): Promise<void> {
  if (busy.value || selectedIds.value.length === 0) return
  busy.value = true
  try {
    await conversationApi.addMembers(props.conversation.id, selectedIds.value)
    await chat.refreshConversation(props.conversation.id)
    showToast(`已邀请 ${selectedIds.value.length} 人加入群聊`, 'success')
    selectedIds.value = []
    inviting.value = false
  } catch (error) {
    showToast(errorMessage(error), 'error')
  } finally {
    busy.value = false
  }
}

async function removeMember(userId: number, nickname: string): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`确定把「${nickname}」移出群聊吗？`)) return
  busy.value = true
  try {
    await conversationApi.removeMember(props.conversation.id, userId)
    await chat.refreshConversation(props.conversation.id)
    showToast(`已将 ${nickname} 移出群聊`, 'success')
  } catch (error) {
    showToast(errorMessage(error), 'error')
  } finally {
    busy.value = false
  }
}

async function leaveGroup(): Promise<void> {
  const me = myId.value
  if (busy.value || me === null) return
  if (!window.confirm('确定退出该群聊吗？退出后需要别人重新邀请才能加入。')) return
  busy.value = true
  try {
    await conversationApi.removeMember(props.conversation.id, me)
    await chat.loadConversations()
    chat.setActive(null)
    showToast('已退出群聊', 'info')
    emit('left')
    emit('close')
  } catch (error) {
    showToast(errorMessage(error), 'error')
  } finally {
    busy.value = false
  }
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="modal-mask" @click.self="emit('close')">
      <div class="modal-card">
        <header class="modal-header">
          <h2>群成员（{{ members.length }}）</h2>
          <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="modal-body">
          <div class="member-list">
            <div v-for="member in members" :key="member.userId" class="member-row">
              <UserAvatar :src="member.avatar" :name="member.nickname" :size="34" :online="member.online" />
              <span class="member-name">
                {{ member.nickname }}
                <span v-if="member.userId === myId" class="me-tag">我</span>
              </span>
              <span v-if="roleLabel(member.role)" class="role-tag">{{ roleLabel(member.role) }}</span>
              <button
                v-if="canManageOthers && member.userId !== myId && member.role !== 'OWNER'"
                class="danger-link"
                type="button"
                :disabled="busy"
                @click="removeMember(member.userId, member.nickname)"
              >
                移出
              </button>
            </div>
          </div>

          <template v-if="canManageOthers">
            <div class="invite-head">
              <button class="link-btn" type="button" @click="inviting = !inviting">
                {{ inviting ? '收起邀请' : '＋ 邀请好友入群' }}
              </button>
              <span v-if="inviting" class="selected-count">已选 {{ selectedIds.length }} 人</span>
            </div>

            <template v-if="inviting">
              <div class="member-search">
                <input v-model="inviteKeyword" type="text" placeholder="搜索好友" />
              </div>
              <div class="candidate-list">
                <button
                  v-for="friend in inviteCandidates"
                  :key="friend.userId"
                  class="candidate-item"
                  type="button"
                  @click="toggleSelected(friend.userId)"
                >
                  <span class="checkbox" :class="{ 'checkbox--checked': selectedIds.includes(friend.userId) }">
                    <svg
                      v-if="selectedIds.includes(friend.userId)"
                      viewBox="0 0 24 24"
                      width="12"
                      height="12"
                      fill="none"
                      stroke="#fff"
                      stroke-width="3"
                    >
                      <path d="m5 12.5 4.5 4.5L19 7" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                  <UserAvatar :src="friend.avatar" :name="friend.nickname" :size="30" :online="friend.online" />
                  <span class="candidate-name">{{ friend.nickname }}</span>
                  <span class="candidate-account">@{{ friend.username }}</span>
                </button>
                <p v-if="inviteCandidates.length === 0" class="list-empty">没有可邀请的好友（都已在群里）</p>
              </div>
            </template>
          </template>

          <p v-else class="manage-hint">只有群主或管理员可以邀请、移除成员</p>
        </div>

        <footer class="modal-footer">
          <button class="btn btn-danger" type="button" :disabled="busy" @click="leaveGroup">退出群聊</button>
          <span class="footer-spacer"></span>
          <button class="btn" type="button" @click="emit('close')">关闭</button>
          <button
            v-if="canManageOthers && inviting"
            class="btn btn-primary"
            type="button"
            :disabled="busy || selectedIds.length === 0"
            @click="addSelected"
          >
            {{ busy ? '处理中…' : '邀请入群' }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(17, 24, 39, 0.42);
  animation: fade-in 0.16s ease;
}

.modal-card {
  display: flex;
  flex-direction: column;
  width: 440px;
  max-height: min(620px, 86vh);
  border-radius: var(--radius-lg);
  background: #fff;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h2 {
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
}

.modal-close {
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 20px;
  line-height: 1;
}

.modal-close:hover {
  background: rgba(0, 0, 0, 0.05);
}

.modal-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 20px 18px;
}

.member-list {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-divider);
}

.member-row:last-child {
  border-bottom: none;
}

.member-name {
  flex: 1;
  min-width: 0;
  color: var(--color-text);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.me-tag {
  margin-left: 5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--color-side);
  color: var(--color-text-muted);
  font-size: 10.5px;
}

.role-tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(18, 183, 245, 0.12);
  color: var(--color-primary);
  font-size: 10.5px;
  flex: 0 0 auto;
}

.danger-link {
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-danger);
  font-size: 12px;
  flex: 0 0 auto;
}

.danger-link:hover {
  background: rgba(245, 72, 59, 0.08);
}

.invite-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 8px;
}

.link-btn {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: 12.5px;
}

.selected-count {
  color: var(--color-text-muted);
  font-size: 12px;
}

.member-search {
  margin-bottom: 8px;
}

.member-search input {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  outline: none;
  background: var(--color-side);
  font-size: 12.5px;
}

.member-search input:focus {
  border-color: var(--color-primary);
  background: #fff;
}

.candidate-list {
  max-height: 190px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.candidate-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-bottom: 1px solid var(--color-divider);
  background: transparent;
  text-align: left;
}

.candidate-item:last-child {
  border-bottom: none;
}

.candidate-item:hover {
  background: var(--color-bg-subtle);
}

.checkbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 1.5px solid #c8cdd5;
  border-radius: 4px;
  flex: 0 0 auto;
}

.checkbox--checked {
  border-color: var(--color-primary);
  background: var(--color-primary);
}

.candidate-name {
  flex: 0 1 auto;
  color: var(--color-text);
  font-size: 12.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.candidate-account {
  flex: 1;
  min-width: 0;
  color: var(--color-text-muted);
  font-size: 11px;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-empty,
.manage-hint {
  margin: 0;
  padding: 22px 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
  text-align: center;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}

.footer-spacer {
  flex: 1;
}

.btn-danger {
  border-color: rgba(245, 72, 59, 0.3);
  color: var(--color-danger);
}

.btn-danger:hover {
  background: rgba(245, 72, 59, 0.06);
}
</style>
