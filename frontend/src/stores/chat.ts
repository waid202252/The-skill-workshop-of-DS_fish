import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  AiStatusVO,
  ConversationVO,
  FriendRequestVO,
  FriendVO,
  MessageType,
  MessageVO,
  SendMessagePayload,
  ServerEvent,
  WsSendData,
} from '@/types/api'
import type { AttachmentPayload, PendingMessage, UIMessage } from '@/types/ui'
import { aiApi, conversationApi, friendApi } from '@/services/api'
import { ApiError } from '@/services/http'
import { ws, type WsStatus } from '@/services/ws'
import { showToast } from '@/composables/toast'
import { showNotification, summarizeForNotification } from '@/composables/notification'
import { createClientMsgId } from '@/utils/id'
import { errorMessage } from '@/utils/error'
import { useAuthStore } from './auth'

/** WS 发送后等待回执的最长时间，超时标记失败允许重发 */
const ACK_TIMEOUT = 20_000

function sortByUpdatedAt(list: ConversationVO[]): void {
  list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
}

function upsertById(list: ConversationVO[], conversation: ConversationVO): void {
  const index = list.findIndex((item) => item.id === conversation.id)
  if (index >= 0) list[index] = conversation
  else list.push(conversation)
}

/**
 * 聊天域状态：会话 / 消息 / 好友 / 好友申请 / 乐观发送 / WS 事件处理。
 */
export const useChatStore = defineStore('chat', () => {
  // ---------------- state ----------------
  const conversations = ref<ConversationVO[]>([])
  const friends = ref<FriendVO[]>([])
  const incomingRequests = ref<FriendRequestVO[]>([])
  const outgoingRequests = ref<FriendRequestVO[]>([])

  const activeConversationId = ref<number | null>(null)
  const pendingMessages = ref<PendingMessage[]>([])

  /** conversationId -> 已确认消息（按 seq 升序） */
  const messagesMap = reactive<Record<number, MessageVO[]>>({})
  /** conversationId -> 是否还有更早消息 */
  const hasMoreMap = reactive<Record<number, boolean>>({})
  /** conversationId -> 是否已经加载过 */
  const loadedMap = reactive<Record<number, boolean>>({})
  /** conversationId -> 是否正在加载更早消息 */
  const loadingOlderMap = reactive<Record<number, boolean>>({})
  /** conversationId -> 对方/其他成员已读到的 seq（来自 read 事件） */
  const peerReadMap = reactive<Record<number, number>>({})
  /** conversationId -> 已上报过的 read seq，避免重复请求 */
  const readSentMap = reactive<Record<number, number>>({})

  const conversationsLoading = ref(false)
  const friendsLoading = ref(false)
  const requestsLoading = ref(false)
  const wsStatus = ref<WsStatus>(ws.currentStatus)
  /** AI 伙伴链路状态（provider / 是否模拟模式），加载失败为 null */
  const aiStatus = ref<AiStatusVO | null>(null)
  /** 重连补齐消息后自增，ChatView 用它强制重建 ChatPanel 以拉取最新消息 */
  const syncVersion = ref(0)

  // ---------------- getters ----------------
  const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + item.unread, 0))
  const pendingRequestCount = computed(
    () => incomingRequests.value.filter((item) => item.status === 'PENDING').length,
  )
  const activeConversation = computed<ConversationVO | null>(
    () => conversations.value.find((item) => item.id === activeConversationId.value) ?? null,
  )

  function hasMore(conversationId: number): boolean {
    return hasMoreMap[conversationId] === true
  }

  function isLoadingOlder(conversationId: number): boolean {
    return loadingOlderMap[conversationId] === true
  }

  function peerReadSeq(conversationId: number): number {
    return peerReadMap[conversationId] ?? 0
  }

  /** 合并已确认消息与本地待确认消息，供消息列表渲染 */
  function buildDisplayMessages(conversationId: number): UIMessage[] {
    const confirmed = messagesMap[conversationId] ?? []
    const result: UIMessage[] = confirmed.map((message) => ({
      key: `m-${message.id}`,
      id: message.id,
      seq: message.seq,
      senderId: message.senderId,
      senderNickname: message.senderNickname,
      senderAvatar: message.senderAvatar,
      type: message.type,
      content: message.content,
      fileName: message.fileName,
      fileSize: message.fileSize,
      createdAt: message.createdAt,
      status: 'sent',
      clientMsgId: message.clientMsgId,
      recalled: message.recalled === true,
    }))

    for (const pending of pendingMessages.value) {
      if (pending.conversationId !== conversationId) continue
      result.push({
        key: `p-${pending.clientMsgId}`,
        id: null,
        seq: null,
        senderId: pending.senderId,
        senderNickname: pending.senderNickname,
        senderAvatar: pending.senderAvatar,
        type: pending.type,
        content: pending.content,
        fileName: pending.fileName,
        fileSize: pending.fileSize,
        createdAt: pending.createdAt,
        status: pending.status,
        clientMsgId: pending.clientMsgId,
      })
    }
    return result
  }

  // ---------------- 基础数据加载 ----------------
  async function loadConversations(): Promise<void> {
    conversationsLoading.value = true
    try {
      const list = await conversationApi.list()
      sortByUpdatedAt(list)
      conversations.value = list
    } finally {
      conversationsLoading.value = false
    }
  }

  async function loadFriends(): Promise<void> {
    friendsLoading.value = true
    try {
      friends.value = await friendApi.list()
    } finally {
      friendsLoading.value = false
    }
  }

  async function loadRequests(): Promise<void> {
    requestsLoading.value = true
    try {
      const result = await friendApi.requests()
      incomingRequests.value = result.incoming
      outgoingRequests.value = result.outgoing
    } finally {
      requestsLoading.value = false
    }
  }

  async function bootstrap(): Promise<void> {
    await Promise.all([loadConversations(), loadFriends(), loadRequests(), loadAiStatus()])
  }

  /** 读取 AI 链路状态，用于在对话头部提示「模拟模式 / 已接入模型」 */
  async function loadAiStatus(): Promise<void> {
    try {
      aiStatus.value = await aiApi.status()
    } catch {
      // AI 未启用或接口异常都不影响主流程
      aiStatus.value = null
    }
  }

  /** 重连后补齐会话与未读、好友申请 */
  async function resync(): Promise<void> {
    try {
      await Promise.all([loadConversations(), loadFriends(), loadRequests()])
    } catch (error) {
      showToast(errorMessage(error), 'error')
    }
  }

  function setActive(conversationId: number | null): void {
    activeConversationId.value = conversationId
  }

  function findConversation(conversationId: number): ConversationVO | undefined {
    return conversations.value.find((item) => item.id === conversationId)
  }

  // ---------------- 消息加载 ----------------
  async function ensureMessages(conversationId: number): Promise<void> {
    if (loadedMap[conversationId]) return
    try {
      const page = await conversationApi.messages(conversationId, { limit: 20 })
      // 与拉取过程中通过 WS 到达的新消息合并，避免覆盖或丢失
      const existing = messagesMap[conversationId] ?? []
      const merged = [...page.messages]
      for (const message of existing) {
        if (!merged.some((item) => item.id === message.id || item.seq === message.seq)) merged.push(message)
      }
      merged.sort((a, b) => a.seq - b.seq)
      messagesMap[conversationId] = merged
      hasMoreMap[conversationId] = page.hasMore
      loadedMap[conversationId] = true
    } catch (error) {
      hasMoreMap[conversationId] = false
      showToast(`加载消息失败：${errorMessage(error)}`, 'error')
    }
  }

  /** 上拉加载更早消息（滚动位置恢复由 ChatPanel 负责） */
  async function loadOlderMessages(conversationId: number): Promise<boolean> {
    if (!hasMoreMap[conversationId] || loadingOlderMap[conversationId]) return false
    const current = messagesMap[conversationId] ?? []
    const beforeSeq = current.length > 0 ? current[0].seq : undefined
    loadingOlderMap[conversationId] = true
    try {
      const page = await conversationApi.messages(conversationId, { beforeSeq, limit: 20 })
      const merged = [...page.messages, ...current]
      const seenIds = new Set<number>()
      const seenSeqs = new Set<number>()
      messagesMap[conversationId] = merged.filter((message) => {
        if (seenIds.has(message.id) || seenSeqs.has(message.seq)) return false
        seenIds.add(message.id)
        seenSeqs.add(message.seq)
        return true
      })
      hasMoreMap[conversationId] = page.hasMore
      return page.messages.length > 0
    } catch (error) {
      showToast(`加载历史消息失败：${errorMessage(error)}`, 'error')
      return false
    } finally {
      loadingOlderMap[conversationId] = false
    }
  }

  function sendReadReceipt(conversationId: number, seq: number): void {
    if (seq <= (readSentMap[conversationId] ?? 0)) return
    readSentMap[conversationId] = seq
    if (ws.send({ type: 'read', data: { conversationId, seq } })) return
    void conversationApi.markRead(conversationId, seq).catch(() => undefined)
  }

  /** 打开会话时把未读清零并上报已读位置 */
  async function markRead(conversationId: number, seq?: number): Promise<void> {
    const conversation = findConversation(conversationId)
    const target = seq ?? conversation?.maxSeq ?? 0
    if (conversation) {
      conversation.unread = 0
      conversation.lastReadSeq = Math.max(conversation.lastReadSeq, target)
    }
    if (target > 0) sendReadReceipt(conversationId, target)
  }

  // ---------------- 乐观发送 ----------------
  function findPending(clientMsgId: string): PendingMessage | undefined {
    return pendingMessages.value.find((item) => item.clientMsgId === clientMsgId)
  }

  function removePending(clientMsgId: string): void {
    pendingMessages.value = pendingMessages.value.filter((item) => item.clientMsgId !== clientMsgId)
  }

  async function transmit(clientMsgId: string): Promise<void> {
    const pending = findPending(clientMsgId)
    if (!pending) return
    pending.status = 'sending'

    const data: WsSendData = {
      conversationId: pending.conversationId,
      clientMsgId: pending.clientMsgId,
      type: pending.type,
      content: pending.content,
      fileName: pending.fileName,
      fileSize: pending.fileSize,
    }

    // 优先走 WebSocket；未连接时使用 REST 兜底
    if (ws.send({ type: 'send', data })) {
      window.setTimeout(() => {
        const current = findPending(clientMsgId)
        if (current && current.status === 'sending') {
          current.status = 'failed'
        }
      }, ACK_TIMEOUT)
      return
    }

    try {
      const payload: SendMessagePayload = {
        clientMsgId: pending.clientMsgId,
        type: pending.type,
        content: pending.content,
        fileName: pending.fileName,
        fileSize: pending.fileSize,
      }
      const message = await conversationApi.sendMessage(pending.conversationId, payload)
      removePending(clientMsgId)
      receiveMessage(message)
    } catch (error) {
      pending.status = 'failed'
      showToast(`发送失败：${errorMessage(error)}`, 'error')
    }
  }

  async function enqueue(
    conversationId: number,
    type: MessageType,
    content: string,
    fileName: string | null,
    fileSize: number | null,
  ): Promise<void> {
    const auth = useAuthStore()
    const me = auth.user
    if (!me) {
      showToast('登录状态已失效，请重新登录', 'error')
      return
    }
    const pending: PendingMessage = {
      clientMsgId: createClientMsgId(),
      conversationId,
      senderId: me.id,
      senderNickname: me.nickname,
      senderAvatar: me.avatar,
      type,
      content,
      fileName,
      fileSize,
      createdAt: new Date().toISOString(),
      status: 'sending',
    }
    pendingMessages.value.push(pending)
    await transmit(pending.clientMsgId)
  }

  async function sendText(conversationId: number, text: string): Promise<void> {
    const content = text.trim()
    if (!content) return
    await enqueue(conversationId, 'TEXT', content, null, null)
  }

  async function sendAttachment(conversationId: number, attachment: AttachmentPayload): Promise<void> {
    await enqueue(conversationId, attachment.type, attachment.content, attachment.fileName, attachment.fileSize)
  }

  function retryPending(clientMsgId: string): void {
    const pending = findPending(clientMsgId)
    if (!pending || pending.status === 'sending') return
    void transmit(clientMsgId)
  }

  // ---------------- WS 事件 ----------------
  function insertMessage(message: MessageVO): void {
    let list = messagesMap[message.conversationId]
    if (!list) {
      // 只缓存当前打开会话的实时消息，避免为所有会话常驻内存
      if (activeConversationId.value !== message.conversationId) return
      list = []
      messagesMap[message.conversationId] = list
    }
    const index = list.findIndex(
      (item) =>
        item.id === message.id ||
        item.seq === message.seq ||
        (message.clientMsgId !== null && item.clientMsgId === message.clientMsgId),
    )
    if (index >= 0) list[index] = message
    else list.push(message)
    list.sort((a, b) => a.seq - b.seq)
  }

  /** 收到新消息 / ack：更新会话摘要与未读，并按 id + clientMsgId 去重 */
  function receiveMessage(message: MessageVO): void {
    const myId = useAuthStore().user?.id
    const isMine = message.senderId === myId
    const conversation = findConversation(message.conversationId)

    if (!conversation) {
      // 可能是新会话（别人发起单聊），拉一次列表
      void loadConversations().catch(() => undefined)
      void loadRequests().catch(() => undefined)
      return
    }

    if (!conversation.lastMessage || message.seq >= conversation.lastMessage.seq) {
      conversation.lastMessage = message
      conversation.updatedAt = message.createdAt
    }
    conversation.maxSeq = Math.max(conversation.maxSeq, message.seq)

    if (activeConversationId.value === message.conversationId) {
      conversation.unread = 0
      conversation.lastReadSeq = Math.max(conversation.lastReadSeq, message.seq)
      if (!isMine) sendReadReceipt(message.conversationId, message.seq)
    } else if (!isMine) {
      conversation.unread += 1
    }

    insertMessage(message)
    if (message.clientMsgId) removePending(message.clientMsgId)
    sortByUpdatedAt(conversations.value)

    // 页面在后台时补一条桌面通知（页面可见时 showNotification 内部会跳过）
    if (!isMine) {
      showNotification({
        title: conversation.type === 'GROUP' ? `${conversation.name} · ${message.senderNickname}` : conversation.name,
        body: summarizeForNotification(message.type, message.content, message.fileName),
        tag: `conv-${message.conversationId}`,
        onClick: () => setActive(message.conversationId),
      })
    }
  }

  function failPending(clientMsgId: string, message: string): void {
    const pending = findPending(clientMsgId)
    if (pending) pending.status = 'failed'
    if (message) showToast(message, 'error')
  }

  function applyPresence(userId: number, online: boolean): void {
    const friend = friends.value.find((item) => item.userId === userId)
    if (friend) friend.online = online
    for (const conversation of conversations.value) {
      if (conversation.peer && conversation.peer.id === userId) {
        conversation.peer.online = online
      }
      // 契约里 members 仅 GROUP 有值，这里做一次容错，避免 null 时打断遍历
      const members = Array.isArray(conversation.members) ? conversation.members : []
      for (const member of members) {
        if (member.userId === userId) member.online = online
      }
    }
  }

  function receiveFriendRequest(request: FriendRequestVO): void {
    if (!incomingRequests.value.some((item) => item.id === request.id)) {
      incomingRequests.value = [request, ...incomingRequests.value]
    }
    showToast(`${request.fromNickname} 请求添加你为好友`, 'info')
  }

  /**
   * 用 afterSeq 增量补齐断线期间积压的消息。
   * 只拉「本地最后一条之后」的部分，因此不管离线期间来了多少条都不会有缺口，
   * 也不会像整页重拉那样把已经读过的历史重复拉一遍。
   */
  async function catchUpMessages(conversationId: number): Promise<boolean> {
    const list = messagesMap[conversationId]
    if (!list || list.length === 0) return false
    let cursor = list[list.length - 1].seq
    try {
      // 每页 100 条，最多 50 轮（5000 条）兜底，防止异常情况下死循环
      for (let round = 0; round < 50; round++) {
        const page = await conversationApi.messages(conversationId, { afterSeq: cursor, limit: 100 })
        for (const message of page.messages) insertMessage(message)
        if (page.messages.length === 0) break
        cursor = page.messages[page.messages.length - 1].seq
        if (!page.hasMore) break
      }
      return true
    } catch {
      return false
    }
  }

  /**
   * 收到 ready（首次连接或断线重连后）：
   * 1. 重新拉取会话 / 好友 / 申请，补齐离线期间的未读与状态；
   * 2. 当前打开的会话若有离线期间的新消息：
   *    - 本地已有消息缓存时，用 afterSeq 增量补齐（不会漏，也不重复拉历史）；
   *    - 缓存为空或补齐失败时，退回「清缓存 + 重建面板重拉最新一页」。
   */
  async function handleReady(): Promise<void> {
    await resync()
    const active = activeConversation.value
    if (!active || active.unread <= 0) return

    const cached = messagesMap[active.id]
    if (cached && cached.length > 0) {
      const caught = await catchUpMessages(active.id)
      if (caught) {
        await markRead(active.id, active.maxSeq)
        return
      }
    }

    delete messagesMap[active.id]
    delete hasMoreMap[active.id]
    delete loadedMap[active.id]
    syncVersion.value += 1
  }

  /** 撤回成功后把本地缓存里的那条消息替换为「已撤回」版本 */
  function applyRecall(message: MessageVO): void {
    const list = messagesMap[message.conversationId]
    if (list) {
      const index = list.findIndex((item) => item.id === message.id)
      if (index >= 0) list[index] = message
    }
    const conversation = findConversation(message.conversationId)
    if (conversation?.lastMessage?.id === message.id) {
      conversation.lastMessage = message
    }
  }

  /** 撤回自己的消息：优先走 WS，未连接时退回 REST */
  async function recallMessage(conversationId: number, messageId: number): Promise<void> {
    try {
      if (ws.send({ type: 'recall', data: { conversationId, messageId } })) {
        // WS 路径：等服务端 message_recalled 帧回来再更新界面
        return
      }
      const updated = await conversationApi.recall(conversationId, messageId)
      applyRecall(updated)
    } catch (error) {
      showToast(errorMessage(error), 'error')
    }
  }

  function handleServerEvent(event: ServerEvent): void {    switch (event.type) {
      case 'pong':
        break
      case 'ready':
        void handleReady()
        break
      case 'ack':
        receiveMessage(event.data.message)
        removePending(event.data.clientMsgId)
        break
      case 'message':
        receiveMessage(event.data.message)
        break
      case 'message_recalled':
        applyRecall(event.data.message)
        break
      case 'read':
        peerReadMap[event.data.conversationId] = Math.max(
          peerReadMap[event.data.conversationId] ?? 0,
          event.data.seq,
        )
        break
      case 'presence':
        applyPresence(event.data.userId, event.data.online)
        break
      case 'friend_request':
        receiveFriendRequest(event.data)
        break
      case 'friend_accepted':
        showToast(`${event.data.nickname} 已通过你的好友申请`, 'success')
        void loadFriends().catch(() => undefined)
        void loadConversations().catch(() => undefined)
        void loadRequests().catch(() => undefined)
        break
      case 'conversation_update':
        void refreshConversation(event.data.conversationId).catch(() => undefined)
        break
      case 'error':
        failPending(event.data.clientMsgId, event.data.message)
        break
      default:
        break
    }
  }

  let realtimeBound = false

  /** 只在 ChatView 中调用一次，绑定额外的 WS 订阅 */
  function initRealtime(): void {
    if (realtimeBound) return
    realtimeBound = true
    ws.onMessage(handleServerEvent)
    ws.onStatus((status) => {
      wsStatus.value = status
    })
  }

  // ---------------- 好友 & 会话操作 ----------------
  async function refreshConversation(conversationId: number): Promise<void> {
    let conversation: ConversationVO
    try {
      conversation = await conversationApi.detail(conversationId)
    } catch (error) {
      // 被移出群聊 / 会话已不存在：从列表中移除
      if (error instanceof ApiError && (error.code === 40300 || error.code === 40400)) {
        conversations.value = conversations.value.filter((item) => item.id !== conversationId)
        if (activeConversationId.value === conversationId) activeConversationId.value = null
        return
      }
      throw error
    }
    upsertById(conversations.value, conversation)
    sortByUpdatedAt(conversations.value)
  }

  /** 与好友开始单聊：没有会话时创建（后端幂等） */
  async function startConversationWithFriend(friend: FriendVO): Promise<number | null> {
    if (friend.conversationId !== null && findConversation(friend.conversationId)) {
      return friend.conversationId
    }
    try {
      const conversation = await conversationApi.createSingle(friend.userId)
      upsertById(conversations.value, conversation)
      sortByUpdatedAt(conversations.value)
      friend.conversationId = conversation.id
      return conversation.id
    } catch (error) {
      showToast(`打开会话失败：${errorMessage(error)}`, 'error')
      return null
    }
  }

  async function createGroup(name: string, memberIds: number[]): Promise<ConversationVO | null> {
    try {
      const conversation = await conversationApi.createGroup({ name, memberIds })
      upsertById(conversations.value, conversation)
      sortByUpdatedAt(conversations.value)
      showToast('群聊创建成功', 'success')
      return conversation
    } catch (error) {
      showToast(`创建群聊失败：${errorMessage(error)}`, 'error')
      return null
    }
  }

  async function sendFriendRequest(toUserId: number, message: string): Promise<boolean> {
    try {
      const request = await friendApi.send({ toUserId, message: message.trim() || undefined })
      if (!outgoingRequests.value.some((item) => item.id === request.id)) {
        outgoingRequests.value = [request, ...outgoingRequests.value]
      }
      showToast('好友申请已发送', 'success')
      return true
    } catch (error) {
      showToast(errorMessage(error), 'error')
      return false
    }
  }

  async function acceptFriendRequest(requestId: number): Promise<void> {
    try {
      await friendApi.accept(requestId)
      showToast('已通过好友申请', 'success')
      await Promise.all([loadFriends(), loadConversations(), loadRequests()])
    } catch (error) {
      showToast(errorMessage(error), 'error')
    }
  }

  async function rejectFriendRequest(requestId: number): Promise<void> {
    try {
      await friendApi.reject(requestId)
      showToast('已拒绝好友申请', 'info')
      await loadRequests()
    } catch (error) {
      showToast(errorMessage(error), 'error')
    }
  }

  // ---------------- 重置 ----------------
  function clearRecord(record: Record<number, unknown>): void {
    for (const key of Object.keys(record)) {
      delete record[Number(key)]
    }
  }

  /** 退出登录时清空所有内存态 */
  function reset(): void {
    conversations.value = []
    friends.value = []
    incomingRequests.value = []
    outgoingRequests.value = []
    activeConversationId.value = null
    pendingMessages.value = []
    clearRecord(messagesMap)
    clearRecord(hasMoreMap)
    clearRecord(loadedMap)
    clearRecord(loadingOlderMap)
    clearRecord(peerReadMap)
    clearRecord(readSentMap)
    wsStatus.value = 'idle'
  }

  return {
    // state
    conversations,
    friends,
    incomingRequests,
    outgoingRequests,
    activeConversationId,
    pendingMessages,
    conversationsLoading,
    friendsLoading,
    requestsLoading,
    wsStatus,
    aiStatus,
    syncVersion,
    // getters
    totalUnread,
    pendingRequestCount,
    activeConversation,
    // helpers
    hasMore,
    isLoadingOlder,
    peerReadSeq,
    buildDisplayMessages,
    findConversation,
    setActive,
    // loading
    bootstrap,
    resync,
    loadConversations,
    loadFriends,
    loadRequests,
    ensureMessages,
    loadOlderMessages,
    markRead,
    // sending
    sendText,
    sendAttachment,
    retryPending,
    recallMessage,
    // ws
    initRealtime,
    receiveMessage,
    handleServerEvent,
    // friends & conversations
    refreshConversation,
    startConversationWithFriend,
    createGroup,
    sendFriendRequest,
    acceptFriendRequest,
    rejectFriendRequest,
    // lifecycle
    reset,
  }
})
