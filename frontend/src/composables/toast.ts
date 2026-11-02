import { ref } from 'vue'
import type { ToastItem, ToastKind } from '@/types/ui'

/** 全局轻提示队列（模块级单例，不依赖 Pinia） */
const toastItems = ref<ToastItem[]>([])
let seed = 0

export function showToast(text: string, kind: ToastKind = 'info', duration = 3000): void {
  seed += 1
  const id = seed
  toastItems.value.push({ id, kind, text })
  window.setTimeout(() => dismissToast(id), duration)
}

export function dismissToast(id: number): void {
  toastItems.value = toastItems.value.filter((item) => item.id !== id)
}

export function useToastItems(): typeof toastItems {
  return toastItems
}
