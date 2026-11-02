import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AuthResult, RegisterPayload, UserVO } from '@/types/api'
import { authApi, userApi } from '@/services/api'
import { clearToken, getToken, setToken } from '@/services/token'

/**
 * 认证状态：token 持久化在 localStorage，用户信息在登录/注册/刷新时获取。
 * 不直接操作路由与 WebSocket，避免循环依赖（由调用方处理副作用）。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<UserVO | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => token.value !== null && token.value.length > 0)

  function applyAuth(result: AuthResult): void {
    token.value = result.token
    user.value = result.user
    setToken(result.token)
  }

  async function login(username: string, password: string): Promise<void> {
    loading.value = true
    try {
      applyAuth(await authApi.login({ username, password }))
    } finally {
      loading.value = false
    }
  }

  async function register(payload: RegisterPayload): Promise<void> {
    loading.value = true
    try {
      applyAuth(await authApi.register(payload))
    } finally {
      loading.value = false
    }
  }

  /** 用已有 token 拉取当前用户（页面刷新 / 路由守卫使用） */
  async function fetchMe(): Promise<UserVO | null> {
    if (!token.value) return null
    const me = await userApi.me()
    user.value = me
    return me
  }

  /** 仅清理本地会话（401 与主动退出共用） */
  function clearSession(): void {
    token.value = null
    user.value = null
    clearToken()
  }

  return {
    token,
    user,
    loading,
    isAuthenticated,
    login,
    register,
    fetchMe,
    clearSession,
  }
})
