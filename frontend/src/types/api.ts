/**
 * 与《API.md》接口契约严格一一对应的 TypeScript 类型定义。
 * 所有 REST 响应统一由 ApiEnvelope 包裹，WebSocket 事件见文件末尾。
 */

/** 统一响应信封 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/** 用户 */
export interface UserVO {
  id: number
  username: string
  nickname: string
  avatar: string | null
  signature: string | null
  online: boolean
  /** HUMAN | BOT；BOT 是内置 AI 伙伴（老数据可能没有该字段） */
  type?: 'HUMAN' | 'BOT'
}

/** AI 伙伴链路状态 */
export interface AiStatusVO {
  botId: number | null
  provider: string
  /** true 表示本地模拟模式（未接入真实模型） */
  mock: boolean
}

/** 好友 */
export interface FriendVO {
  userId: number
  username: string
  nickname: string
  avatar: string | null
  signature: string | null
  online: boolean
  /** 与 TA 的单聊会话 id，可能为 null */
  conversationId: number | null
}

export type FriendRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'

/** 好友申请 */
export interface FriendRequestVO {
  id: number
  fromUserId: number
  fromNickname: string
  fromAvatar: string | null
  toUserId: number
  toNickname: string
  message: string | null
  status: FriendRequestStatus
  createdAt: string
}

export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER'

/** 群成员 */
export interface MemberVO {
  userId: number
  nickname: string
  avatar: string | null
  role: MemberRole
  online: boolean
}

export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM'

/** 消息 */
export interface MessageVO {
  id: number
  conversationId: number
  /** 会话内单调递增序号，用于排序与游标分页 */
  seq: number
  senderId: number
  senderNickname: string
  senderAvatar: string | null
  type: MessageType
  /** TEXT=正文；IMAGE=图片 URL；FILE=文件 URL；SYSTEM=系统提示 */
  content: string
  fileName: string | null
  fileSize: number | null
  createdAt: string
  /** 客户端生成的幂等 id，用于发送回执匹配 */
  clientMsgId: string | null
  /** 已撤回：此时 content 为空串，前端渲染「已撤回」占位 */
  recalled?: boolean
}

export type ConversationType = 'SINGLE' | 'GROUP'

/** 会话 */
export interface ConversationVO {
  id: number
  type: ConversationType
  /** 单聊时后端已填成对方昵称 */
  name: string
  /** 单聊时后端已填成对方头像 */
  avatar: string | null
  ownerId: number | null
  memberCount: number
  /** 未读数 = maxSeq - lastReadSeq（自己发的已排除） */
  unread: number
  lastReadSeq: number
  maxSeq: number
  lastMessage: MessageVO | null
  /** 仅 SINGLE 有值 */
  peer: UserVO | null
  /** 仅 GROUP 有值（最多返回 200 人） */
  members: MemberVO[]
  updatedAt: string
}

// ---------------------------------------------------------------------------
// 请求体
// ---------------------------------------------------------------------------

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  password: string
  nickname: string
}

export interface AuthResult {
  token: string
  user: UserVO
}

export interface UpdateProfilePayload {
  nickname?: string
  avatar?: string
  signature?: string
}

export interface SendFriendRequestPayload {
  toUserId: number
  message?: string
}

export interface FriendRequestsResult {
  incoming: FriendRequestVO[]
  outgoing: FriendRequestVO[]
}

export interface CreateGroupPayload {
  name: string
  memberIds: number[]
}

/** REST 发送消息请求体（clientMsgId 可选） */
export interface SendMessagePayload {
  clientMsgId?: string
  type: MessageType
  content: string
  fileName?: string | null
  fileSize?: number | null
}

/** 消息分页结果 */
export interface MessagesPage {
  messages: MessageVO[]
  hasMore: boolean
}

/** 文件上传结果 */
export interface UploadResult {
  url: string
  name: string
  size: number
}

// ---------------------------------------------------------------------------
// WebSocket
// ---------------------------------------------------------------------------

/** 客户端 -> 服务端：发送消息 */
export interface WsSendData {
  conversationId: number
  clientMsgId: string
  type: MessageType
  content: string
  fileName: string | null
  fileSize: number | null
}

/** 客户端 -> 服务端事件 */
export type ClientEvent =
  | { type: 'ping' }
  | { type: 'send'; data: WsSendData }
  | { type: 'read'; data: { conversationId: number; seq: number } }
  | { type: 'recall'; data: { conversationId: number; messageId: number } }

/** 服务端 -> 客户端事件 */
export type ServerEvent =
  | { type: 'pong'; data: { serverTime: string } }
  | { type: 'ready'; data: { userId: number } }
  | { type: 'ack'; data: { clientMsgId: string; message: MessageVO } }
  | { type: 'message'; data: { message: MessageVO } }
  | { type: 'read'; data: { conversationId: number; userId: number; seq: number } }
  | { type: 'presence'; data: { userId: number; online: boolean } }
  | { type: 'friend_request'; data: FriendRequestVO }
  | { type: 'friend_accepted'; data: FriendVO }
  | { type: 'conversation_update'; data: { conversationId: number } }
  | { type: 'message_recalled'; data: { message: MessageVO } }
  | { type: 'error'; data: { clientMsgId: string; message: string } }

export type ServerEventType = ServerEvent['type']

// ---------------------------------------------------------------------------
// 技能市场
// ---------------------------------------------------------------------------

/** 技能卡片（来自 GitHub 仓库，本地缓存后返回） */
export interface SkillVO {
  id: number
  githubId: number
  /** owner/repo */
  fullName: string
  owner: string
  ownerAvatar: string | null
  name: string
  description: string | null
  stars: number
  forks: number
  language: string | null
  topics: string[]
  htmlUrl: string
  homepage: string | null
  /** 当前用户是否已安装 */
  installed: boolean
  installCount: number
}

/** 技能分类（预置推荐维度） */
export interface SkillCategory {
  key: string
  label: string
  /** 对应的 GitHub 搜索语句 */
  query: string
}

/** 技能说明文档 */
export interface SkillReadme {
  skillId: number
  fileName: string
  markdown: string
  sourceUrl: string
  truncated: boolean
}

export interface SkillSummaryVO {
  skillId: number
  summary: string
  aiGenerated: boolean
  model: string
  generatedAt: string
  notice: string | null
}

/** 技能搜索结果 */
export interface SkillSearchResult {
  items: SkillVO[]
  total: number
  page: number
  perPage: number
  /** 本次结果来自本地缓存，没有消耗 GitHub 配额 */
  cached: boolean
  /** GitHub 不可用，已降级为本地缓存搜索 */
  degraded: boolean
  notice: string | null
}

// ---------------------------------------------------------------------------
// 社区：指南 / 配方 / AI 首答
// ---------------------------------------------------------------------------

export type CommunityGuideType = 'GUIDE' | 'RECIPE'

/** 指南关联技能的快照，不依赖本地技能是否已缓存 */
export interface CommunitySkillVO {
  fullName: string
  name: string
  owner: string
  htmlUrl: string
}

export interface CommunityGuideCardVO {
  id: number
  type: CommunityGuideType
  title: string
  summary: string
  emoji: string | null
  tags: string[]
  difficulty: string | null
  duration: string | null
  cost: string | null
  aiGenerated: boolean
  helpfulCount: number
  publishedAt: string | null
  relatedSkills: CommunitySkillVO[]
}

export interface CommunityGuideDetailVO extends CommunityGuideCardVO {
  content: string
  promptText: string | null
  reviewStatus: string
  reviewedBy: string | null
  notHelpfulCount: number
  viewCount: number
  myFeedback: boolean | null
}

export interface CommunityAskVO {
  questionId: number
  answer: string
  mock: boolean
  relatedGuides: CommunityGuideCardVO[]
}

export interface CommunityFeedbackVO {
  helpfulCount: number
  notHelpfulCount: number
  myFeedback: boolean
}
