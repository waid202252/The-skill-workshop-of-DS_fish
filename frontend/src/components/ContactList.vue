<script setup lang="ts">
import { computed } from 'vue'
import type { FriendVO } from '@/types/api'
import { formatUnread, nicknameGroupKey } from '@/utils/format'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{
  friends: FriendVO[]
  pendingCount: number
  loading: boolean
}>()

const emit = defineEmits<{
  select: [friend: FriendVO]
  openRequests: []
  createGroup: []
  searchUsers: []
}>()

interface FriendGroup {
  key: string
  friends: FriendVO[]
}

/** 按昵称首个字符分组，A-Z 优先，# 收尾 */
const groups = computed<FriendGroup[]>(() => {
  const map = new Map<string, FriendVO[]>()
  for (const friend of props.friends) {
    const key = nicknameGroupKey(friend.nickname)
    const bucket = map.get(key)
    if (bucket) bucket.push(friend)
    else map.set(key, [friend])
  }
  return [...map.entries()]
    .map(([key, friends]) => ({
      key,
      friends: [...friends].sort((a, b) => a.nickname.localeCompare(b.nickname, 'zh-Hans-CN')),
    }))
    .sort((a, b) => {
      if (a.key === '#') return 1
      if (b.key === '#') return -1
      return a.key.localeCompare(b.key)
    })
})
</script>

<template>
  <div class="contact-list">
    <div class="contact-actions">
      <button class="contact-action" type="button" @click="emit('openRequests')">
        <span class="action-icon action-icon--friend">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="9" cy="8" r="3.2" />
            <path d="M3.2 19.4c.5-3.4 2.8-5.3 5.8-5.3 1.1 0 2.1.2 3 .7" stroke-linecap="round" />
            <path d="M17.5 13.5v5M15 16h5" stroke-linecap="round" />
          </svg>
        </span>
        <span class="action-label">新的朋友</span>
        <Transition name="dot">
          <span v-if="pendingCount > 0" :key="pendingCount" class="action-badge">
            {{ formatUnread(pendingCount) }}
          </span>
        </Transition>
      </button>

      <button class="contact-action" type="button" @click="emit('createGroup')">
        <span class="action-icon action-icon--group">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="9" cy="8" r="3.2" />
            <path d="M3.2 19.4c.5-3.4 2.8-5.3 5.8-5.3s5.3 1.9 5.8 5.3" stroke-linecap="round" />
            <path d="M16.5 5.5h5M19 3v5" stroke-linecap="round" />
          </svg>
        </span>
        <span class="action-label">发起群聊</span>
      </button>
    </div>

    <div class="contact-scroll">
      <div v-if="loading && friends.length === 0" class="contact-placeholder">
        <span class="spinner"></span>
        加载联系人…
      </div>

      <template v-else>
        <div v-for="group in groups" :key="group.key" class="contact-group">
          <p class="group-label">{{ group.key }}</p>
          <button
            v-for="friend in group.friends"
            :key="friend.userId"
            class="contact-item"
            type="button"
            @click="emit('select', friend)"
          >
            <UserAvatar :src="friend.avatar" :name="friend.nickname" :size="36" :online="friend.online" />
            <span class="contact-name">{{ friend.nickname }}</span>
            <span class="contact-status" :class="{ 'contact-status--online': friend.online }">
              {{ friend.online ? '在线' : '离线' }}
            </span>
          </button>
        </div>

        <div v-if="friends.length === 0" class="contact-empty">
          <p>还没有好友</p>
          <button class="btn btn-primary btn-sm empty-add" type="button" @click="emit('searchUsers')">
            去搜索添加
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.contact-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.contact-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 8px 8px;
  border-bottom: 1px solid var(--color-border);
}

.contact-action {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  transition: background 0.15s ease;
}

.contact-action:hover {
  background: var(--color-bg-subtle);
}

.action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
}

.action-icon--friend {
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.action-icon--group {
  background: rgba(91, 141, 239, 0.12);
  color: #5b8def;
}

.action-label {
  flex: 1;
  color: var(--color-text);
  font-size: 13.5px;
  font-weight: 500;
}

.action-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--color-danger);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
}

.dot-enter-active,
.dot-leave-active {
  transition:
    transform 0.2s ease,
    opacity 0.2s ease;
}

.dot-enter-from,
.dot-leave-to {
  transform: scale(0.4);
  opacity: 0;
}

.contact-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 8px 16px;
}

.group-label {
  margin: 10px 8px 4px;
  color: var(--color-text-muted);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 7px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  transition: background 0.15s ease;
}

.contact-item:hover {
  background: var(--color-bg-subtle);
}

.contact-name {
  flex: 1;
  min-width: 0;
  color: var(--color-text);
  font-size: 13.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-status {
  flex: 0 0 auto;
  color: var(--color-text-muted);
  font-size: 11px;
}

.contact-status--online {
  color: var(--color-success);
}

.contact-placeholder {
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

.contact-empty {
  padding: 48px 12px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

.contact-empty p {
  margin: 0 0 12px;
}

.empty-add {
  margin: 0 auto;
}
</style>
