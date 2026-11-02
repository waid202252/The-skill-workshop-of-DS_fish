import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { ApiEnvelope } from '@/types/api'
import { clearToken, getToken } from './token'

/** 后端业务错误：message 可直接展示 */
export class ApiError extends Error {
  readonly code: number
  readonly status: number | null

  constructor(message: string, code: number, status: number | null = null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

let unauthorizedHandler: (() => void) | null = null

/** 注册 401 处理（由 main.ts 注入，避免 services 依赖 router/store） */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof (value as { code: unknown }).code === 'number'
  )
}

function fallbackMessage(status: number | null): string {
  if (status === 400) return '请求参数有误'
  if (status === 403) return '没有操作权限'
  if (status === 404) return '请求的资源不存在'
  if (status === 409) return '操作发生冲突'
  if (status !== null && status >= 500) return '服务器开小差了，请稍后重试'
  return '网络异常，请稍后重试'
}

/** 登录/注册接口自身返回 401 时不能当成「会话失效」处理 */
function isAuthEndpoint(url: string | undefined): boolean {
  if (!url) return false
  return url.startsWith('/auth/') || url.includes('/api/auth/')
}

/** 统一的 axios 实例：baseURL 使用相对路径以走 Vite 代理 */
export const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 20_000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const envelope: unknown = response.data
    if (isEnvelope(envelope) && envelope.code !== 0) {
      // 部分实现可能在 HTTP 200 里返回全局鉴权错误码，这里一并兜底
      if (
        (envelope.code === 40100 || envelope.code === 40101) &&
        !isAuthEndpoint(response.config.url)
      ) {
        clearToken()
        unauthorizedHandler?.()
      }
      return Promise.reject(new ApiError(envelope.message || '请求失败', envelope.code, response.status))
    }
    return response
  },
  (error: unknown) => {
    const axiosError = error as AxiosError<unknown>
    const status = axiosError.response?.status ?? null
    const payload = axiosError.response?.data

    if (status === 401 && !isAuthEndpoint(axiosError.config?.url)) {
      clearToken()
      unauthorizedHandler?.()
      const message = isEnvelope(payload) ? payload.message : '登录已失效，请重新登录'
      return Promise.reject(new ApiError(message || '登录已失效，请重新登录', 40100, 401))
    }

    if (isEnvelope(payload)) {
      return Promise.reject(new ApiError(payload.message || fallbackMessage(status), payload.code, status))
    }

    return Promise.reject(new ApiError(fallbackMessage(status), -1, status))
  },
)

type ExtraConfig = Omit<AxiosRequestConfig, 'url' | 'method' | 'data'>

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T>>(config)
  return response.data.data
}

/** 泛型请求封装：自动拆信封，失败统一抛 ApiError */
export const api = {
  get<T>(url: string, config?: ExtraConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'GET' })
  },
  post<T>(url: string, data?: unknown, config?: ExtraConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown, config?: ExtraConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'PUT', data })
  },
  delete<T>(url: string, config?: ExtraConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'DELETE' })
  },
}
