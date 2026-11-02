/** token 持久化：localStorage 单点读写，避免各模块散落 key 字符串 */
const TOKEN_KEY = 'weliao.token'

export function getToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null): void {
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_KEY, token)
    } else {
      window.localStorage.removeItem(TOKEN_KEY)
    }
  } catch {
    // localStorage 不可用时静默降级为内存态
  }
}

export function clearToken(): void {
  setToken(null)
}
