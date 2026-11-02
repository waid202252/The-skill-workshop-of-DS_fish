import { ApiError } from '@/services/http'

/** 把任意异常转成可直接展示的中文文案 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return '网络异常，请稍后重试'
}
