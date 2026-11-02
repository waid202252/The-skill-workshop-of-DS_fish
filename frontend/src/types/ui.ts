import type { MessageType } from './api'

/** 左侧导航模式 */
export type NavMode = 'messages' | 'contacts'

/** 乐观发送中的本地消息状态 */
export type PendingStatus = 'sending' | 'failed'

/** 尚未被服务端确认的本地消息 */
export interface PendingMessage {
  clientMsgId: string
  conversationId: number
  senderId: number
  senderNickname: string
  senderAvatar: string | null
  type: MessageType
  content: string
  fileName: string | null
  fileSize: number | null
  createdAt: string
  status: PendingStatus
}

/** 消息列表渲染使用的统一结构（已确认消息 + 本地待确认消息） */
export interface UIMessage {
  key: string
  id: number | null
  seq: number | null
  senderId: number
  senderNickname: string
  senderAvatar: string | null
  type: MessageType
  content: string
  fileName: string | null
  fileSize: number | null
  createdAt: string
  status: 'sent' | PendingStatus
  clientMsgId: string | null
  /** 已撤回的消息只渲染占位文案 */
  recalled?: boolean
}

/** ChatInput 上传完成后抛给上层的附件载荷 */
export interface AttachmentPayload {
  type: Extract<MessageType, 'IMAGE' | 'FILE'>
  content: string
  fileName: string | null
  fileSize: number | null
}

/** 消息列表中混排的时间分割线 / 消息项 */
export type MessageListItem =
  | { kind: 'time'; key: string; label: string }
  | {
      kind: 'message'
      key: string
      message: UIMessage
      isSelf: boolean
      showName: boolean
    }

export type ToastKind = 'error' | 'success' | 'info'

export interface ToastItem {
  id: number
  kind: ToastKind
  text: string
}
