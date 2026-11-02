<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { notificationPermission, requestNotificationPermission } from '@/composables/notification'
import { showToast } from '@/composables/toast'
import UserAvatar from './UserAvatar.vue'
import ProfileEditModal from './ProfileEditModal.vue'
import ChangePasswordModal from './ChangePasswordModal.vue'

type TopbarSection = 'discover' | 'skills' | 'community' | 'chat'

const props = withDefaults(
  defineProps<{
    active: TopbarSection
    modelValue?: string
    searchPlaceholder?: string
    searchDisabled?: boolean
    showSearch?: boolean
  }>(),
  {
    modelValue: '',
    searchPlaceholder: '搜索 AI 技能…',
    searchDisabled: false,
    showSearch: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  input: []
  search: []
  logout: []
}>()

const auth = useAuthStore()
const router = useRouter()

const menuOpen = ref(false)
const menuRef = ref<HTMLDivElement | null>(null)
const showProfile = ref(false)
const showPwd = ref(false)
const notifyPermission = ref(notificationPermission())

const displayName = computed(() => auth.user?.nickname ?? '朋友')
const isAuthed = computed(() => auth.isAuthenticated)
const username = computed(() => (auth.user ? `@${auth.user.username}` : ''))

const navItems: { key: TopbarSection; label: string; to: string }[] = [
  { key: 'discover', label: '发现', to: '/discover' },
  { key: 'skills', label: '技能广场', to: '/skills' },
  { key: 'community', label: '社区', to: '/community' },
  { key: 'chat', label: '聊天', to: '/chat' },
]

function goLogin(): void {
  void router.push({
    path: '/login',
    query: { redirect: router.currentRoute.value.fullPath },
  })
}

function go(to: string): void {
  menuOpen.value = false
  if (router.currentRoute.value.fullPath !== to) void router.push(to)
}

function onSearchInput(event: Event): void {
  const value = (event.target as HTMLInputElement).value
  emit('update:modelValue', value)
  emit('input')
}

function submitSearch(): void {
  if (props.searchDisabled) return
  emit('search')
}

function clearSearch(): void {
  emit('update:modelValue', '')
  emit('input')
}

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

function onDocumentClick(event: MouseEvent): void {
  if (!menuOpen.value) return
  const target = event.target
  if (!(target instanceof Node)) return
  if (menuRef.value?.contains(target)) return
  menuOpen.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') menuOpen.value = false
}

async function enableNotifications(): Promise<void> {
  const granted = await requestNotificationPermission()
  notifyPermission.value = notificationPermission()
  showToast(
    granted ? '已开启桌面通知' : '未获得通知权限，可在浏览器设置里手动允许',
    granted ? 'success' : 'info',
  )
  menuOpen.value = false
}

function openProfile(): void {
  menuOpen.value = false
  showProfile.value = true
}

function openPassword(): void {
  menuOpen.value = false
  showPwd.value = true
}

function handleLogout(): void {
  menuOpen.value = false
  emit('logout')
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <header class="app-topbar">
    <div class="topbar-inner">
      <button class="brand" type="button" aria-label="回到发现页" @click="go('/discover')">
        <img class="brand-mark" src="/mascot-face.png" alt="" />
        <span class="brand-text">
          <strong>大肥鱼的工坊</strong>
          <span>AI SKILL WORKSHOP</span>
        </span>
      </button>

      <nav class="topbar-nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-link"
          :class="{ 'nav-link--active': props.active === item.key }"
          type="button"
          :aria-current="props.active === item.key ? 'page' : undefined"
          @click="go(item.to)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div v-if="props.showSearch" class="topbar-search">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" />
          <path d="m16.2 16.2 4 4" stroke-linecap="round" />
        </svg>
        <input
          :value="props.modelValue"
          type="search"
          :placeholder="props.searchPlaceholder"
          :disabled="props.searchDisabled"
          aria-label="搜索"
          @input="onSearchInput"
          @keydown.enter.prevent="submitSearch"
        />
        <button
          v-if="props.modelValue"
          class="search-clear"
          type="button"
          aria-label="清空搜索"
          @click="clearSearch"
        >
          ×
        </button>
        <button class="search-submit" type="button" :disabled="props.searchDisabled" @click="submitSearch">
          搜索
        </button>
      </div>

      <div v-if="!isAuthed" class="guest-area">
        <button class="login-btn" type="button" @click="goLogin">登录</button>
      </div>

      <div v-else ref="menuRef" class="topbar-user">
        <button class="user-button" type="button" :aria-expanded="menuOpen" @click.stop="toggleMenu">
          <UserAvatar :src="auth.user?.avatar" :name="displayName" :size="28" />
          <span class="user-name">{{ displayName }}</span>
          <svg class="user-chevron" :class="{ 'user-chevron--open': menuOpen }" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <path d="m6 9 6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>

        <Transition name="menu">
          <div v-if="menuOpen" class="user-menu" role="menu">
            <div class="menu-head">
              <UserAvatar :src="auth.user?.avatar" :name="displayName" :size="38" :online="true" />
              <div class="menu-head-text">
                <strong>{{ displayName }}</strong>
                <span>{{ username }}</span>
              </div>
            </div>

            <div class="menu-list">
              <button class="menu-item" type="button" role="menuitem" @click="openProfile">
                个人资料
              </button>
              <button class="menu-item" type="button" role="menuitem" @click="openPassword">
                修改密码
              </button>
              <button
                v-if="notifyPermission === 'default'"
                class="menu-item"
                type="button"
                role="menuitem"
                @click="enableNotifications"
              >
                开启桌面通知
              </button>
            </div>

            <div class="menu-list menu-list--bottom">
              <button class="menu-item menu-item--danger" type="button" role="menuitem" @click="handleLogout">
                退出登录
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </header>

  <ProfileEditModal v-if="showProfile" @close="showProfile = false" />
  <ChangePasswordModal v-if="showPwd" @close="showPwd = false" @done="showPwd = false" />
</template>

<style scoped>
.app-topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  flex: 0 0 auto;
  height: var(--topbar-height);
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
}

.topbar-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  width: 100%;
  max-width: var(--content-max);
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
}

/* ---- 品牌 ---- */
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  object-fit: cover;
  background: var(--color-primary-softer);
  box-shadow: 0 0 0 1px var(--color-border);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-text strong {
  color: var(--color-text);
  font-size: 14.5px;
  font-weight: var(--weight-extrabold);
  letter-spacing: -0.01em;
}

.brand-text span {
  margin-top: 2px;
  color: var(--color-text-muted);
  font-size: 9.5px;
  font-weight: var(--weight-semibold);
  letter-spacing: 1.4px;
}

/* ---- 主导航：参考站的底部下划线激活态 ---- */
.topbar-nav {
  display: flex;
  align-self: stretch;
  align-items: stretch;
  gap: 4px;
  flex: 0 0 auto;
}

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: 100%;
  padding: 0 12px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13.5px;
  font-weight: var(--weight-medium);
  white-space: nowrap;
  transition:
    color var(--transition),
    border-color var(--transition);
}

.nav-link:hover {
  color: var(--color-text);
}

.nav-link--active {
  border-bottom-color: var(--color-primary);
  color: var(--color-text);
  font-weight: var(--weight-semibold);
}

/* ---- 搜索 ---- */
.topbar-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 auto;
  max-width: 460px;
  height: 38px;
  margin: 0 auto;
  padding: 0 6px 0 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: #f1f5f9;
  color: var(--color-text-muted);
  transition:
    background var(--transition),
    border-color var(--transition),
    box-shadow var(--transition);
}

.topbar-search:focus-within {
  border-color: var(--color-primary);
  background: var(--color-panel);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.topbar-search input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
}

.topbar-search input::placeholder {
  color: var(--color-text-muted);
}

.topbar-search input::-webkit-search-cancel-button {
  display: none;
}

.topbar-search input:disabled {
  cursor: not-allowed;
}

.search-clear {
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(100, 116, 139, 0.16);
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1;
}

.search-submit {
  flex: 0 0 auto;
  height: 28px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: var(--color-primary-strong);
  color: #fff;
  font-size: 12px;
  font-weight: var(--weight-semibold);
}

.search-submit:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.search-submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ---- 游客 ---- */
.guest-area {
  flex: 0 0 auto;
  margin-left: auto;
}

.login-btn {
  height: 36px;
  padding: 0 18px;
  border: 1px solid var(--color-primary-strong);
  border-radius: var(--radius-md);
  background: var(--color-primary-strong);
  color: #fff;
  font-size: 12.5px;
  font-weight: var(--weight-semibold);
  transition:
    background var(--transition),
    border-color var(--transition);
}

.login-btn:hover {
  border-color: var(--color-primary-dark);
  background: var(--color-primary-dark);
}

/* ---- 用户菜单 ---- */
.topbar-user {
  position: relative;
  flex: 0 0 auto;
  margin-left: auto;
}

.user-button {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 4px 10px 4px 5px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel);
  color: var(--color-text);
  transition:
    border-color var(--transition),
    background var(--transition);
}

.user-button:hover {
  border-color: var(--color-border-strong);
  background: var(--color-bg-subtle);
}

.user-name {
  max-width: 96px;
  overflow: hidden;
  color: var(--color-text);
  font-size: 12.5px;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-chevron {
  color: var(--color-text-muted);
  transition: transform var(--transition);
}

.user-chevron--open {
  transform: rotate(180deg);
}

.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 120;
  width: 248px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel);
  box-shadow: var(--shadow-lg);
}

.menu-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 12px;
  border-bottom: 1px solid var(--color-divider);
}

.menu-head-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.menu-head-text strong {
  overflow: hidden;
  color: var(--color-text);
  font-size: 13px;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-head-text span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.menu-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 8px;
}

.menu-list--bottom {
  margin-top: 4px;
  border-top: 1px solid var(--color-divider);
}

.menu-item {
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12.5px;
  text-align: left;
  transition:
    background var(--transition),
    color var(--transition);
}

.menu-item:hover {
  background: var(--color-bg-subtle);
  color: var(--color-text);
}

.menu-item--danger {
  color: var(--color-danger);
}

.menu-item--danger:hover {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.menu-enter-active,
.menu-leave-active {
  transition:
    opacity 0.14s ease,
    transform 0.14s ease;
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

@media (max-width: 1040px) {
  .topbar-inner {
    gap: 16px;
  }

  .brand-text span {
    display: none;
  }

  .topbar-search {
    max-width: 360px;
  }
}

@media (max-width: 860px) {
  .topbar-inner {
    gap: 12px;
    padding: 0 16px;
  }

  .topbar-search {
    display: none;
  }

  .nav-link {
    padding: 0 8px;
    font-size: 13px;
  }
}

@media (max-width: 620px) {
  .brand-text {
    display: none;
  }

  .user-name {
    display: none;
  }

  .user-button {
    padding: 4px;
    border: none;
    background: transparent;
  }

  .user-chevron {
    display: none;
  }
}
</style>
