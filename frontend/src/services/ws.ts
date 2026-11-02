import type { ClientEvent, ServerEvent } from '@/types/api'

export type WsStatus = 'idle' | 'connecting' | 'open' | 'closed'

type ServerEventListener = (event: ServerEvent) => void
type StatusListener = (status: WsStatus) => void

/** 心跳间隔 25s */
const HEARTBEAT_INTERVAL = 25_000
/** 60s 内没有收到任何帧则判定连接已死 */
const DEAD_TIMEOUT = 60_000
/** 重连退避上限 15s */
const MAX_BACKOFF = 15_000

/**
 * 把未知的 JSON 帧解析为带有 type 的服务端事件。
 * 只做结构入口校验，字段细节由契约保证；不做 any 兜底。
 */
function parseServerEvent(raw: string): ServerEvent | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return null
  }
  if (typeof parsed !== 'object' || parsed === null) return null
  const record = parsed as Record<string, unknown>
  if (typeof record.type !== 'string') return null
  if (record.data !== undefined && (typeof record.data !== 'object' || record.data === null)) return null
  return parsed as ServerEvent
}

/**
 * 原生 WebSocket 单例客户端。
 * - 指数退避自动重连：1s / 2s / 4s / 8s / 15s（封顶）
 * - 25s 发送心跳 ping
 * - 60s 未收到任何帧判定断线并重连
 * - 通过 onMessage 订阅服务端事件
 */
class WsClient {
  private socket: WebSocket | null = null
  private token: string | null = null
  private status: WsStatus = 'idle'
  private reconnectAttempt = 0
  private heartbeatTimer: number | null = null
  private deadTimer: number | null = null
  private reconnectTimer: number | null = null
  private manualClose = false

  private readonly messageListeners = new Set<ServerEventListener>()
  private readonly statusListeners = new Set<StatusListener>()

  get currentStatus(): WsStatus {
    return this.status
  }

  get isOpen(): boolean {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN
  }

  /** 建立连接（重复调用安全；token 变化时重建） */
  connect(token: string): void {
    if (!token) return
    const socketBusy =
      this.socket !== null &&
      (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)
    if (this.token === token && socketBusy) return

    this.stopReconnect()
    this.teardownSocket()
    this.token = token
    this.manualClose = false
    this.reconnectAttempt = 0
    this.openSocket()
  }

  /** 主动断开（退出登录 / 401），不再自动重连 */
  disconnect(): void {
    this.manualClose = true
    this.token = null
    this.reconnectAttempt = 0
    this.stopReconnect()
    this.teardownSocket()
    this.setStatus('idle')
  }

  /** 发送客户端事件；连接未就绪返回 false，便于上层走 REST 兜底 */
  send(event: ClientEvent): boolean {
    if (!this.isOpen || this.socket === null) return false
    try {
      this.socket.send(JSON.stringify(event))
      return true
    } catch {
      return false
    }
  }

  /** 订阅服务端事件，返回取消订阅函数 */
  onMessage(listener: ServerEventListener): () => void {
    this.messageListeners.add(listener)
    return () => {
      this.messageListeners.delete(listener)
    }
  }

  /** 订阅连接状态，订阅时立即回调一次当前状态 */
  onStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener)
    listener(this.status)
    return () => {
      this.statusListeners.delete(listener)
    }
  }

  private openSocket(): void {
    if (this.manualClose || this.token === null) return
    this.setStatus('connecting')

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/ws?token=${encodeURIComponent(this.token)}`

    let socket: WebSocket
    try {
      socket = new WebSocket(url)
    } catch {
      this.scheduleReconnect()
      return
    }
    this.socket = socket

    socket.onopen = () => {
      if (this.socket !== socket) return
      this.reconnectAttempt = 0
      this.setStatus('open')
      this.startHeartbeat()
      this.armDeadTimer()
    }

    socket.onmessage = (event: MessageEvent) => {
      if (this.socket !== socket) return
      // 任何帧都证明连接存活
      this.armDeadTimer()
      if (typeof event.data !== 'string') return
      const parsed = parseServerEvent(event.data)
      if (!parsed) return
      for (const listener of this.messageListeners) listener(parsed)
    }

    socket.onclose = () => {
      if (this.socket !== socket) return
      this.socket = null
      this.stopTimers()
      this.setStatus('closed')
      if (!this.manualClose) this.scheduleReconnect()
    }

    socket.onerror = () => {
      // 触发 onclose 走统一的重连逻辑
      socket.close()
    }
  }

  private scheduleReconnect(): void {
    if (this.manualClose || this.token === null) return
    if (this.reconnectTimer !== null) return
    const delay = Math.min(1000 * 2 ** this.reconnectAttempt, MAX_BACKOFF)
    this.reconnectAttempt += 1
    this.setStatus('connecting')
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.openSocket()
    }, delay)
  }

  private startHeartbeat(): void {
    if (this.heartbeatTimer !== null) window.clearInterval(this.heartbeatTimer)
    this.heartbeatTimer = window.setInterval(() => {
      this.send({ type: 'ping' })
    }, HEARTBEAT_INTERVAL)
  }

  private armDeadTimer(): void {
    if (this.deadTimer !== null) window.clearTimeout(this.deadTimer)
    this.deadTimer = window.setTimeout(() => {
      this.deadTimer = null
      const socket = this.socket
      if (socket === null) return
      // 关闭后由 onclose 触发自动重连
      socket.close()
    }, DEAD_TIMEOUT)
  }

  private stopTimers(): void {
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
    if (this.deadTimer !== null) {
      window.clearTimeout(this.deadTimer)
      this.deadTimer = null
    }
  }

  private stopReconnect(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private teardownSocket(): void {
    this.stopTimers()
    const socket = this.socket
    this.socket = null
    if (socket === null) return
    socket.onopen = null
    socket.onmessage = null
    socket.onclose = null
    socket.onerror = null
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
      socket.close()
    }
  }

  private setStatus(status: WsStatus): void {
    if (this.status === status) return
    this.status = status
    for (const listener of this.statusListeners) listener(status)
  }
}

/** 全局单例 */
export const ws = new WsClient()
