/** 展示层格式化工具 */

/** 文件大小：B / KB / MB */
export function formatFileSize(bytes: number | null): string {
  if (bytes === null || bytes === undefined || bytes <= 0) return '未知大小'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** 会话列表最后一条消息摘要 */
export function formatLastMessage(
  type: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM',
  content: string,
  fileName: string | null,
): string {
  switch (type) {
    case 'IMAGE':
      return '[图片]'
    case 'FILE':
      return `[文件] ${fileName ?? ''}`.trim()
    case 'SYSTEM':
      return content
    case 'TEXT':
    default:
      return content
  }
}

/** 未读数展示：超过 99 显示 99+ */
export function formatUnread(count: number): string {
  return count > 99 ? '99+' : String(count)
}

/** 大数字缩写：1.2k / 12.3k，用于 Star、收藏数等 */
export function formatCompact(value: number): string {
  if (!Number.isFinite(value)) return '0'
  if (value >= 10000) return `${(value / 1000).toFixed(1)}k`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return String(value)
}

/** 昵称首字符分组键：A-Z 归入对应字母，其余归入 # */
export function nicknameGroupKey(nickname: string): string {
  const first = nickname.trim().charAt(0).toUpperCase()
  return /^[A-Z]$/.test(first) ? first : '#'
}

/** 头像兜底文字 */
export function avatarLetter(name: string): string {
  const trimmed = name.trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

const AVATAR_COLORS = [
  '#12b7f5',
  '#5b8def',
  '#7c6cf5',
  '#b06cf5',
  '#ef6fa8',
  '#f58f6c',
  '#f5b93f',
  '#3fbf8f',
  '#3fa9bf',
  '#6b7a90',
]

/** 根据名字稳定地挑一个头像底色 */
export function avatarColor(name: string): string {
  let hash = 0
  for (let i = 0; i < name.length; i += 1) {
    hash = (hash * 31 + name.charCodeAt(i)) % 100_000
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}
