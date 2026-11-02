<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import UserAvatar from './UserAvatar.vue'

const emit = defineEmits<{
  close: []
  created: [conversationId: number]
}>()

const chat = useChatStore()
const groupName = ref('')
const selectedIds = ref<number[]>([])
const keyword = ref('')
const submitting = ref(false)

const filteredFriends = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return chat.friends
  return chat.friends.filter(
    (friend) =>
      friend.nickname.toLowerCase().includes(query) || friend.username.toLowerCase().includes(query),
  )
})

const canSubmit = computed(
  () => !submitting.value && groupName.value.trim().length > 0 && selectedIds.value.length > 0,
)

function isSelected(userId: number): boolean {
  return selectedIds.value.includes(userId)
}

function toggle(userId: number): void {
  if (isSelected(userId)) {
    selectedIds.value = selectedIds.value.filter((id) => id !== userId)
  } else {
    selectedIds.value = [...selectedIds.value, userId]
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const conversation = await chat.createGroup(groupName.value.trim(), selectedIds.value)
    if (conversation) emit('created', conversation.id)
  } finally {
    submitting.value = false
  }
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="modal-mask" @click.self="emit('close')">
      <div class="modal-card">
        <header class="modal-header">
          <h2>发起群聊</h2>
          <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="modal-body">
          <label class="field-label" for="group-name">群名称</label>
          <input
            id="group-name"
            v-model="groupName"
            class="text-field"
            type="text"
            maxlength="30"
            placeholder="给群聊起个名字（1-30 字）"
          />

          <div class="select-head">
            <span class="field-label">选择成员</span>
            <span class="selected-count">已选 {{ selectedIds.length }} 人</span>
          </div>

          <div class="member-search">
            <input v-model="keyword" type="text" placeholder="搜索好友" />
          </div>

          <div class="member-list">
            <button
              v-for="friend in filteredFriends"
              :key="friend.userId"
              class="member-item"
              type="button"
              @click="toggle(friend.userId)"
            >
              <span class="checkbox" :class="{ 'checkbox--checked': isSelected(friend.userId) }">
                <svg
                  v-if="isSelected(friend.userId)"
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
              <UserAvatar :src="friend.avatar" :name="friend.nickname" :size="34" :online="friend.online" />
              <span class="member-name">{{ friend.nickname }}</span>
              <span class="member-account">@{{ friend.username }}</span>
            </button>

            <p v-if="filteredFriends.length === 0" class="member-empty">
              {{ chat.friends.length === 0 ? '还没有好友，先去添加好友吧' : '没有匹配的好友' }}
            </p>
          </div>
        </div>

        <footer class="modal-footer">
          <button class="btn" type="button" @click="emit('close')">取消</button>
          <button class="btn btn-primary" type="button" :disabled="!canSubmit" @click="submit">
            {{ submitting ? '创建中…' : '创建群聊' }}
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
  padding: 16px 20px;
}

.field-label {
  display: block;
  margin-bottom: 7px;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  font-weight: 500;
}

.select-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 0 7px;
}

.select-head .field-label {
  margin: 0;
}

.selected-count {
  color: var(--color-primary);
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

.member-list {
  max-height: 230px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-bottom: 1px solid var(--color-divider);
  background: transparent;
  text-align: left;
}

.member-item:last-child {
  border-bottom: none;
}

.member-item:hover {
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
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}

.checkbox--checked {
  border-color: var(--color-primary);
  background: var(--color-primary);
}

.member-name {
  flex: 0 1 auto;
  color: var(--color-text);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.member-account {
  flex: 1;
  min-width: 0;
  color: var(--color-text-muted);
  font-size: 11px;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.member-empty {
  margin: 0;
  padding: 30px 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
  text-align: center;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}

.modal-footer .btn-primary {
  min-width: 92px;
}
</style>
