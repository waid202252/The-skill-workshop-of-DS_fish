/** 生成客户端消息幂等 id：c-时间戳-随机串 */
export function createClientMsgId(): string {
  const random = Math.random().toString(36).slice(2, 8)
  return `c-${Date.now()}-${random}`
}
