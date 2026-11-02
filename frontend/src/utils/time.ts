/** 时间格式化相关的纯函数 */

const pad = (value: number): string => String(value).padStart(2, '0')

/** 后端返回 ISO-8601 本地时间字符串（无时区），直接按本地时区解析 */
export function parseTime(value: string): Date {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? new Date() : date
}

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

/** 与当前时间相差的自然日天数（今天 0，昨天 1） */
function dayDiff(date: Date, now: Date): number {
  return Math.round((startOfDay(now) - startOfDay(date)) / 86_400_000)
}

function clock(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * 会话列表时间：今天 HH:mm，昨天「昨天」，更早 MM-DD。
 */
export function formatConversationTime(value: string, now: Date = new Date()): string {
  const date = parseTime(value)
  const diff = dayDiff(date, now)
  if (diff <= 0) return clock(date)
  if (diff === 1) return '昨天'
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * 消息分割线时间：今天 HH:mm，昨天「昨天 HH:mm」，今年 MM-DD HH:mm，更早 YYYY-MM-DD HH:mm。
 */
export function formatMessageTime(value: string, now: Date = new Date()): string {
  const date = parseTime(value)
  const diff = dayDiff(date, now)
  if (diff <= 0) return clock(date)
  if (diff === 1) return `昨天 ${clock(date)}`
  if (date.getFullYear() === now.getFullYear()) {
    return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${clock(date)}`
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${clock(date)}`
}

/** 两条消息是否应插入时间分割线（间隔超过 5 分钟） */
export function shouldShowTime(previous: string | null, current: string): boolean {
  if (!previous) return true
  const gap = parseTime(current).getTime() - parseTime(previous).getTime()
  return gap > 5 * 60 * 1000
}
