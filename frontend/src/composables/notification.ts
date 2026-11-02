/**
 * 浏览器桌面通知。
 *
 * 几点工程约束：
 * 1. 权限必须由用户手势触发，所以不自动 requestPermission，而是由设置里的按钮调用。
 * 2. 只在页面不可见（document.hidden）时弹通知——用户正盯着聊天窗口时再弹一次是骚扰。
 * 3. 用 tag 去重：同一会话的新消息复用同一个 tag，避免通知中心被刷屏。
 * 4. 浏览器不支持或用户拒绝时静默降级，不能影响聊天主流程。
 */

export type NotifyPermission = NotificationPermission | 'unsupported'

export function notificationSupported(): boolean {
  return typeof window !== 'undefined' && 'Notification' in window
}

export function notificationPermission(): NotifyPermission {
  if (!notificationSupported()) return 'unsupported'
  return Notification.permission
}

/** 申请通知权限，返回是否最终获得授权 */
export async function requestNotificationPermission(): Promise<boolean> {
  if (!notificationSupported()) return false
  if (Notification.permission === 'granted') return true
  if (Notification.permission === 'denied') return false
  try {
    const result = await Notification.requestPermission()
    return result === 'granted'
  } catch {
    return false
  }
}

interface NotifyInput {
  title: string
  body: string
  tag?: string
  onClick?: () => void
}

/** 弹一条桌面通知。页面可见时不打扰，直接跳过。 */
export function showNotification(input: NotifyInput): void {
  if (!notificationSupported() || Notification.permission !== 'granted') return
  if (typeof document !== 'undefined' && !document.hidden) return

  try {
    const notification = new Notification(input.title, {
      body: input.body,
      tag: input.tag,
      // 同一个 tag 复用已有通知，不叠一堆
      silent: false,
    })
    notification.onclick = () => {
      try {
        window.focus()
      } catch {
        // 某些环境下 focus 会被拒绝，忽略
      }
      input.onClick?.()
      notification.close()
    }
  } catch {
    // 部分浏览器（如移动端 Chrome）不允许直接构造 Notification，忽略即可
  }
}

/** 把消息内容压缩成一行摘要，用于通知正文 */
export function summarizeForNotification(type: string, content: string, fileName: string | null): string {
  if (type === 'IMAGE') return '[图片]'
  if (type === 'FILE') return `[文件] ${fileName ?? '未命名'}`
  const text = content.replace(/\s+/g, ' ').trim()
  return text.length > 60 ? `${text.slice(0, 60)}…` : text
}
