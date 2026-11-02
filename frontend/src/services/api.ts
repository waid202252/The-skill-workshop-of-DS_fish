import type {
  AiStatusVO,
  AuthResult,
  CommunityAskVO,
  CommunityFeedbackVO,
  CommunityGuideCardVO,
  CommunityGuideDetailVO,
  CommunityGuideType,
  ConversationVO,
  CreateGroupPayload,
  FriendRequestVO,
  FriendRequestsResult,
  FriendVO,
  LoginPayload,
  MessageVO,
  MessagesPage,
  RegisterPayload,
  SendFriendRequestPayload,
  SendMessagePayload,
  SkillCategory,
  SkillReadme,
  SkillSearchResult,
  SkillSummaryVO,
  SkillVO,
  UpdateProfilePayload,
  UploadResult,
  UserVO,
} from '@/types/api'
import { api, http } from './http'

/** 认证与当前用户 */
export const authApi = {
  login(payload: LoginPayload): Promise<AuthResult> {
    return api.post<AuthResult>('/auth/login', payload)
  },
  register(payload: RegisterPayload): Promise<AuthResult> {
    return api.post<AuthResult>('/auth/register', payload)
  },
  /** 修改密码：需要原密码，成功后旧 token 仍然有效 */
  changePassword(oldPassword: string, newPassword: string): Promise<null> {
    return api.post<null>('/auth/password', { oldPassword, newPassword })
  },
}

/** 用户资料与搜索 */
export const userApi = {
  me(): Promise<UserVO> {
    return api.get<UserVO>('/users/me')
  },
  updateMe(payload: UpdateProfilePayload): Promise<UserVO> {
    return api.put<UserVO>('/users/me', payload)
  },
  search(keyword: string): Promise<UserVO[]> {
    return api.get<UserVO[]>('/users/search', { params: { keyword } })
  },
}

/** 好友 */
export const friendApi = {
  list(): Promise<FriendVO[]> {
    return api.get<FriendVO[]>('/friends')
  },
  remove(userId: number): Promise<null> {
    return api.delete<null>(`/friends/${userId}`)
  },
  requests(): Promise<FriendRequestsResult> {
    return api.get<FriendRequestsResult>('/friends/requests')
  },
  send(payload: SendFriendRequestPayload): Promise<FriendRequestVO> {
    return api.post<FriendRequestVO>('/friends/requests', payload)
  },
  accept(id: number): Promise<FriendVO> {
    return api.post<FriendVO>(`/friends/requests/${id}/accept`)
  },
  reject(id: number): Promise<null> {
    return api.post<null>(`/friends/requests/${id}/reject`)
  },
}

/** 会话与消息 */
export const conversationApi = {
  list(): Promise<ConversationVO[]> {
    return api.get<ConversationVO[]>('/conversations')
  },
  createSingle(userId: number): Promise<ConversationVO> {
    return api.post<ConversationVO>('/conversations/single', { userId })
  },
  createGroup(payload: CreateGroupPayload): Promise<ConversationVO> {
    return api.post<ConversationVO>('/conversations/group', payload)
  },
  detail(id: number): Promise<ConversationVO> {
    return api.get<ConversationVO>(`/conversations/${id}`)
  },
  messages(id: number, params: { beforeSeq?: number; afterSeq?: number; limit?: number }): Promise<MessagesPage> {
    return api.get<MessagesPage>(`/conversations/${id}/messages`, { params })
  },
  sendMessage(id: number, payload: SendMessagePayload): Promise<MessageVO> {
    return api.post<MessageVO>(`/conversations/${id}/messages`, payload)
  },
  markRead(id: number, seq: number): Promise<null> {
    return api.post<null>(`/conversations/${id}/read`, { seq })
  },
  addMembers(id: number, userIds: number[]): Promise<ConversationVO> {
    return api.post<ConversationVO>(`/conversations/${id}/members`, { userIds })
  },
  removeMember(id: number, userId: number): Promise<null> {
    return api.delete<null>(`/conversations/${id}/members/${userId}`)
  },
  recall(id: number, messageId: number): Promise<MessageVO> {
    return api.post<MessageVO>(`/conversations/${id}/messages/${messageId}/recall`)
  },
}

/** 文件上传 */
export const fileApi = {
  upload(file: File): Promise<UploadResult> {
    const form = new FormData()
    form.append('file', file)
    return api.post<UploadResult>('/files/upload', form)
  },
}

/** AI 伙伴 */
export const aiApi = {
  status(): Promise<AiStatusVO> {
    return api.get<AiStatusVO>('/ai/status')
  },
  openConversation(): Promise<{ conversationId: number; botId: number | null }> {
    return api.post<{ conversationId: number; botId: number | null }>('/ai/conversation')
  },
}

/** 技能市场 */
export const skillApi = {
  search(q: string, page = 1, perPage = 24): Promise<SkillSearchResult> {
    return api.get<SkillSearchResult>('/skills/search', { params: { q, page, perPage } })
  },
  trending(): Promise<SkillVO[]> {
    return api.get<SkillVO[]>('/skills/trending')
  },
  installed(): Promise<SkillVO[]> {
    return api.get<SkillVO[]>('/skills/installed')
  },
  detail(id: number): Promise<SkillVO> {
    return api.get<SkillVO>(`/skills/${id}`)
  },
  install(id: number): Promise<SkillVO> {
    return api.post<SkillVO>(`/skills/${id}/install`)
  },
  uninstall(id: number): Promise<null> {
    return api.delete<null>(`/skills/${id}/install`)
  },
  categories(): Promise<SkillCategory[]> {
    return api.get<SkillCategory[]>('/skills/categories')
  },
  readme(id: number): Promise<SkillReadme> {
    return api.get<SkillReadme>(`/skills/${id}/readme`)
  },
  /** README AI 总结：首次可能要跑本地模型，超时放宽到 150s */
  summary(id: number, refresh = false): Promise<SkillSummaryVO> {
    return api.get<SkillSummaryVO>(`/skills/${id}/summary`, {
      params: { refresh },
      timeout: 150_000,
    })
  },
  /** 下载说明文档：服务端按附件返回，这里触发浏览器保存 */
  async downloadReadme(id: number, fallbackName = 'README.md'): Promise<void> {
    const res = await http.get(`/skills/${id}/readme/download`, { responseType: 'blob' })
    const blob = res.data as Blob
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fallbackName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  },
}

/** 社区：指南/配方 + AI 首答 */
export const communityApi = {
  list(params: { type?: CommunityGuideType; q?: string; limit?: number } = {}): Promise<CommunityGuideCardVO[]> {
    return api.get<CommunityGuideCardVO[]>('/community/guides', { params })
  },
  detail(id: number): Promise<CommunityGuideDetailVO> {
    return api.get<CommunityGuideDetailVO>(`/community/guides/${id}`)
  },
  guidesForSkill(fullName: string): Promise<CommunityGuideCardVO[]> {
    return api.get<CommunityGuideCardVO[]>('/community/guides/by-skill', { params: { fullName } })
  },
  feedback(id: number, helpful: boolean): Promise<CommunityFeedbackVO> {
    return api.post<CommunityFeedbackVO>(`/community/guides/${id}/feedback`, { helpful })
  },
  ask(question: string): Promise<CommunityAskVO> {
    return api.post<CommunityAskVO>('/community/ask', { question })
  },
  questionFeedback(id: number, helpful: boolean): Promise<null> {
    return api.post<null>(`/community/questions/${id}/feedback`, { helpful })
  },
}
