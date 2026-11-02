<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { userApi } from '@/services/api'
import type { UserVO } from '@/types/api'
import { errorMessage } from '@/utils/error'
import { showToast } from '@/composables/toast'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{ keyword: string }>()
const emit = defineEmits<{ close: [] }>()

const chat = useChatStore()

const keyword = ref(props.keyword)
const results = ref<UserVO[]>([])
const searching = ref(false)
const searched = ref(false)
const activeUserId = ref<number | null>(null)
const draftMessage = ref('')
const submittingId = ref<number | null>(null)

let searchTimer: number | null = null
let requestSeq = 0

const trimmedKeyword = computed(() => keyword.value.trim())

function isFriend(userId: number): boolean {
  return chat.friends.some((friend) => friend.userId === userId)
}

function hasPending(userId: number): boolean {
  return chat.outgoingRequests.some(
    (request) => request.toUserId === userId && request.status === 'PENDING',
  )
}

async function runSearch(): Promise<void> {
  if (searchTimer !== null) {
    window.clearTimeout(searchTimer)
    searchTimer = null
  }
  const query = trimmedKeyword.value
  if (!query) {
    results.value = []
    searched.value = false
    return
  }
  const seq = ++requestSeq
  searching.value = true
  try {
    const list = await userApi.search(query)
    if (seq !== requestSeq) return
    results.value = list
    searched.value = true
  } catch (error) {
    if (seq === requestSeq) showToast(errorMessage(error), 'error')
  } finally {
    if (seq === requestSeq) searching.value = false
  }
}

function scheduleSearch(): void {
  activeUserId.value = null
  if (searchTimer !== null) window.clearTimeout(searchTimer)
  if (!trimmedKeyword.value) {
    results.value = []
    searched.value = false
    return
  }
  searchTimer = window.setTimeout(() => {
    searchTimer = null
    void runSearch()
  }, 320)
}

function openRequestForm(userId: number): void {
  activeUserId.value = activeUserId.value === userId ? null : userId
  draftMessage.value = ''
}

async function submitRequest(userId: number): Promise<void> {
  if (submittingId.value !== null) return
  submittingId.value = userId
  try {
    const ok = await chat.sendFriendRequest(userId, draftMessage.value)
    if (ok) {
      activeUserId.value = null
      draftMessage.value = ''
    }
  } finally {
    submittingId.value = null
  }
}

watch(keyword, scheduleSearch)

watch(
  () => props.keyword,
  (value) => {
    keyword.value = value
    void runSearch()
  },
)

onMounted(() => {
  if (trimmedKeyword.value) void runSearch()
})

onBeforeUnmount(() => {
  if (searchTimer !== null) window.clearTimeout(searchTimer)
})
</script>

<template>
  <section class="add-friend-panel">
    <header class="panel-header">
      <button class="back-button" type="button" title="返回" @click="emit('close')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
          <path d="m14.5 5-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <h2 class="panel-title">添加好友</h2>
    </header>

    <div class="panel-body">
      <div class="search-card">
        <div class="search-field">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9">
            <circle cx="11" cy="11" r="6.5" />
            <path d="m16.2 16.2 4 4" stroke-linecap="round" />
          </svg>
          <input
            v-model="keyword"
            type="text"
            placeholder="输入用户名或昵称搜索用户"
            @keydown.enter.prevent="runSearch"
          />
          <button v-if="keyword" class="clear-button" type="button" @click="keyword = ''">×</button>
        </div>
        <button class="btn btn-primary search-button" type="button" :disabled="searching || !trimmedKeyword" @click="runSearch">
          {{ searching ? '搜索中…' : '搜索' }}
        </button>
      </div>

      <div class="result-card">
        <div v-if="searching && results.length === 0" class="result-placeholder">
          <span class="spinner"></span>
          正在搜索…
        </div>

        <div v-else-if="!searched" class="result-placeholder result-placeholder--hint">
          <p>输入用户名或昵称，找到对方后发送好友申请</p>
        </div>

        <div v-else-if="results.length === 0" class="result-placeholder result-placeholder--hint">
          <p>没有找到相关用户，换个关键字试试</p>
        </div>

        <div v-for="user in results" :key="user.id" class="user-row">
          <UserAvatar :src="user.avatar" :name="user.nickname" :size="44" :online="user.online" />
          <div class="user-main">
            <p class="user-name">{{ user.nickname }}</p>
            <p class="user-account">@{{ user.username }}</p>
            <p v-if="user.signature" class="user-signature">{{ user.signature }}</p>
          </div>

          <span v-if="isFriend(user.id)" class="user-state">已是好友</span>
          <span v-else-if="hasPending(user.id)" class="user-state user-state--pending">已发送申请</span>
          <button v-else class="btn btn-primary btn-sm" type="button" @click="openRequestForm(user.id)">
            加好友
          </button>

          <div v-if="activeUserId === user.id" class="request-form">
            <textarea
              v-model="draftMessage"
              class="request-textarea"
              rows="2"
              maxlength="100"
              placeholder="填写附言（选填，最多 100 字）"
            ></textarea>
            <div class="request-form-actions">
              <button class="btn btn-sm" type="button" @click="activeUserId = null">取消</button>
              <button
                class="btn btn-primary btn-sm"
                type="button"
                :disabled="submittingId !== null"
                @click="submitRequest(user.id)"
              >
                {{ submittingId === user.id ? '发送中…' : '发送申请' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.add-friend-panel {
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

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 24px 32px;
  background: var(--color-chat-bg);
}

.search-card {
  display: flex;
  gap: 10px;
  max-width: 720px;
  margin: 0 auto 16px;
}

.search-field {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fff;
  color: var(--color-text-muted);
}

.search-field:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.search-field input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--color-text);
  font-size: 13.5px;
}

.clear-button {
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.1);
  color: #fff;
  font-size: 12px;
  line-height: 1;
}

.search-button {
  height: 40px;
  min-width: 80px;
}

.result-card {
  max-width: 720px;
  margin: 0 auto;
  padding: 6px 16px;
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.result-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 42px 0;
  color: var(--color-text-muted);
  font-size: 12.5px;
}

.result-placeholder--hint p {
  margin: 0;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.user-row {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 14px 2px;
  border-bottom: 1px solid var(--color-divider);
}

.user-row:last-child {
  border-bottom: none;
}

.user-main {
  flex: 1;
  min-width: 0;
}

.user-name {
  margin: 0;
  color: var(--color-text);
  font-size: 13.5px;
  font-weight: 600;
}

.user-account {
  margin: 2px 0 0;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.user-signature {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-state {
  flex: 0 0 auto;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.05);
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.user-state--pending {
  background: rgba(255, 176, 32, 0.14);
  color: #b47500;
}

.request-form {
  flex: 0 0 100%;
  margin: 2px 0 0 56px;
  padding: 10px;
  border-radius: 8px;
  background: var(--color-side);
}

.request-textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  outline: none;
  resize: none;
  font-size: 12.5px;
  color: var(--color-text);
  line-height: 1.5;
}

.request-textarea:focus {
  border-color: var(--color-primary);
}

.request-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
