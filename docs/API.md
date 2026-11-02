# IM 系统接口契约 v1

后端: Spring Boot 3 + Java 17, 默认端口 `8080`
前端: Vue 3 + Vite, 开发端口 `5173`, `/api` 与 `/ws` 代理到 8080

## 0. 统一约定

### 响应信封
所有 REST 接口返回：

```json
{ "code": 0, "message": "ok", "data": { } }
```

- `code = 0` 表示成功，`data` 为业务数据（可为 `null`）。
- `code != 0` 表示失败，`message` 是可直接展示给用户的中文文案，`data` 为 `null`。
- HTTP 状态码：成功 200；未登录/Token 失效 **401**（前端跳登录页）；参数或业务错误 400/403/404/409，但仍返回上述信封。
- 全局错误码：`40100` 未登录、`40101` Token 过期、`40300` 无权限、`40400` 资源不存在、`40900` 冲突、`42900` 触发限流、`50000` 服务器内部错误。

### 鉴权
除 `/api/auth/**`、`GET /api/community/guides/**`（社区游客只读）外，所有接口需带请求头：
```
Authorization: Bearer <token>
```

### 时间格式
ISO-8601 本地时间字符串，如 `2026-10-30T10:33:03`。

---

## 1. 数据结构（VO）

```ts
type UserVO = {
  id: number; username: string; nickname: string; avatar: string | null;
  signature: string | null; online: boolean;
}

type FriendVO = {
  userId: number; username: string; nickname: string; avatar: string | null;
  signature: string | null; online: boolean;
  conversationId: number | null;   // 与 TA 的单聊会话 id，可能为 null
}

type FriendRequestVO = {
  id: number; fromUserId: number; fromNickname: string; fromAvatar: string | null;
  toUserId: number; toNickname: string; message: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'; createdAt: string;
}

type MemberVO = {
  userId: number; nickname: string; avatar: string | null;
  role: 'OWNER' | 'ADMIN' | 'MEMBER'; online: boolean;
}

type MessageVO = {
  id: number; conversationId: number;
  seq: number;                       // 会话内单调递增序号，用于排序与游标分页
  senderId: number; senderNickname: string; senderAvatar: string | null;
  type: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM';
  content: string;                   // TEXT=正文; IMAGE=图片URL; FILE=文件URL; SYSTEM=系统提示
  fileName: string | null;
  fileSize: number | null;
  createdAt: string;
  clientMsgId: string | null;        // 客户端生成的幂等 id，用于发送回执匹配
  recalled: boolean;                 // 已撤回：content 为空串，前端渲染「已撤回」占位
}

type ConversationVO = {
  id: number;
  type: 'SINGLE' | 'GROUP';
  name: string;                      // 单聊时后端已填成对方昵称，前端可直接展示
  avatar: string | null;             // 单聊时后端已填成对方头像
  ownerId: number | null;
  memberCount: number;
  unread: number;                    // 未读数 = maxSeq - lastReadSeq（自己发的已排除）
  lastReadSeq: number;
  maxSeq: number;
  lastMessage: MessageVO | null;
  peer: UserVO | null;               // 仅 SINGLE 有值
  members: MemberVO[];               // 仅 GROUP 有值（最多返回 200 人）
  updatedAt: string;
}
```

---

## 2. 认证与用户

| 方法 | 路径 | 请求体 | 返回 data |
|---|---|---|---|
| POST | `/api/auth/register` | `{username, password, nickname}` | `{token, user: UserVO}` |
| POST | `/api/auth/login` | `{username, password}` | `{token, user: UserVO}` |
| GET | `/api/users/me` | - | `UserVO` |
| PUT | `/api/users/me` | `{nickname?, avatar?, signature?}` | `UserVO` |
| GET | `/api/users/search?keyword=` | - | `UserVO[]`（最多 20 条，排除自己） |

校验：`username` 4-20 位字母数字下划线；`password` 6-32 位；`nickname` 1-20 位。
注册时 username 重复返回 `40900`，message `"用户名已被占用"`。

---

## 3. 好友

| 方法 | 路径 | 请求体 | 返回 data |
|---|---|---|---|
| GET | `/api/friends` | - | `FriendVO[]` |
| DELETE | `/api/friends/{userId}` | - | `null` |
| GET | `/api/friends/requests` | - | `{incoming: FriendRequestVO[], outgoing: FriendRequestVO[]}` |
| POST | `/api/friends/requests` | `{toUserId, message?}` | `FriendRequestVO` |
| POST | `/api/friends/requests/{id}/accept` | - | `FriendVO` |
| POST | `/api/friends/requests/{id}/reject` | - | `null` |

规则：已是好友返回 `40900`「你们已经是好友了」；已有待处理申请返回 `40900`「已发送过申请，请等待对方处理」；不能加自己 `40000`。

---

## 4. 会话与消息

| 方法 | 路径 | 请求体 | 返回 data |
|---|---|---|---|
| GET | `/api/conversations` | - | `ConversationVO[]`（按 updatedAt 倒序） |
| POST | `/api/conversations/single` | `{userId}` | `ConversationVO`（已存在则直接返回，幂等） |
| POST | `/api/conversations/group` | `{name, memberIds: number[]}` | `ConversationVO` |
| GET | `/api/conversations/{id}` | - | `ConversationVO` |
| GET | `/api/conversations/{id}/messages?beforeSeq=&afterSeq=&limit=20` | - | `{messages: MessageVO[], hasMore: boolean}` |
| POST | `/api/conversations/{id}/messages` | `{clientMsgId?, type, content, fileName?, fileSize?}` | `MessageVO`（REST 兜底发送） |
| POST | `/api/conversations/{id}/messages/{messageId}/recall` | - | `MessageVO`（已撤回版本，`recalled=true`、`content=""`） |
| POST | `/api/conversations/{id}/read` | `{seq}` | `null` |
| POST | `/api/conversations/{id}/members` | `{userIds: number[]}` | `ConversationVO` |
| DELETE | `/api/conversations/{id}/members/{userId}` | - | `null` |

分页语义（两个方向二选一，结果**一律按 seq 升序**）：

- **往前翻**（上拉加载历史）：`beforeSeq` 缺省取当前 `maxSeq + 1`，返回 `< beforeSeq` 的最近 `limit` 条。
  `hasMore` 表示更早的消息是否还有。上拉加载更早 = 传当前列表第一条的 `seq`。
- **往后补**（断线重连追上积压消息）：传 `afterSeq`，返回 `> afterSeq` 的最早 `limit` 条。
  `hasMore` 表示还有更新的消息。配合循环调用可完整补齐，不会因为一页只有 20 条而漏消息。
  同时传两个时 `afterSeq` 优先。

非成员访问会话返回 `40300`「你不在该会话中」。

---

## 5. 文件上传

| 方法 | 路径 | 请求体 | 返回 data |
|---|---|---|---|
| POST | `/api/files/upload` | `multipart/form-data`，字段名 `file` | `{url, name, size}` |

- 单文件上限 20MB。
- `url` 为相对路径，如 `/uploads/2026/10/uuid.png`，前端可直接用于 `<img src>`（走 Vite 代理）。
- 允许扩展名：png jpg jpeg gif webp bmp pdf doc docx xls xlsx ppt pptx txt md zip rar 7z mp3 mp4。

---

## 6. WebSocket

连接：`ws://127.0.0.1:5173/ws?token=<JWT>`（Vite 已代理 `/ws`）。Token 非法 -> 服务端直接关闭连接。

### 消息信封
```json
{ "type": "send", "data": { } }
```

### 客户端 -> 服务端
```json
{ "type": "ping" }
{ "type": "send", "data": {
    "conversationId": 1,
    "clientMsgId": "c-1723456789-ab12",
    "type": "TEXT",
    "content": "你好",
    "fileName": null,
    "fileSize": null
} }
{ "type": "read", "data": { "conversationId": 1, "seq": 42 } }
{ "type": "recall", "data": { "conversationId": 1, "messageId": 88 } }
```

### 服务端 -> 客户端
```json
{ "type": "pong", "data": { "serverTime": "..." } }
{ "type": "ready", "data": { "userId": 1 } }                       // 连接建立后第一帧
{ "type": "ack",  "data": { "clientMsgId": "c-...", "message": MessageVO } }
{ "type": "message", "data": { "message": MessageVO } }            // 收到新消息（含自己发的，便于多端同步）
{ "type": "read", "data": { "conversationId": 1, "userId": 2, "seq": 42 } }
{ "type": "presence", "data": { "userId": 2, "online": true } }
{ "type": "friend_request", "data": FriendRequestVO }
{ "type": "friend_accepted", "data": FriendVO }
{ "type": "conversation_update", "data": { "conversationId": 1 } }  // 群成员变动等，前端据此刷新
{ "type": "message_recalled", "data": { "message": MessageVO } }   // 撤回：message.recalled=true，content 为空
{ "type": "error", "data": { "clientMsgId": "c-...", "message": "你不在该会话中" } }
```

### 限流

登录/注册按 **IP** 限流、发送消息按 **用户** 限流，均为一分钟固定窗口。触发时返回：

```
HTTP 429
Retry-After: 56
{ "code": 42900, "message": "操作太频繁，请稍后再试", "data": null }
```

WebSocket 通道的发送也受同一配额约束，超限时下发 `error` 帧而不是 HTTP 429。

### 客户端行为要求
- 连接后每 25s 发一次 `ping`；60s 内没收到任何帧则判定断线并重连（指数退避 1s→2s→4s→8s，上限 15s）。
- 发送消息时先本地插入「发送中」气泡（用 `clientMsgId` 定位），收到 `ack` 后替换为真实 `message`；收到 `error` 则标记失败并允许重试。
- 收到 `message_recalled` 时按 `message.id` 替换本地缓存的那条，渲染「已撤回」占位。
- 页面不可见时收到他人消息，应弹一条桌面通知（同一会话用固定 `tag` 去重）。
- 移动端/HMR 场景下重连后需重新拉取 `/api/conversations` 补齐未读。

---

## 7. 在线状态
用户任意一个 WebSocket 连接存活即视为在线。登录/退出不改变在线态（以连接为准）。
`presence` 事件仅推给与该用户有共同会话的联系人。

---

## 8. AI 伙伴

| 方法 | 路径 | 请求体 | 返回 data |
|---|---|---|---|
| POST | `/api/ai/conversation` | - | `{conversationId, botId}` |
| GET | `/api/ai/status` | - | `{botId, provider, mock}` |

- AI 是一个普通用户（`UserVO.type = "BOT"`），与它的会话就是普通 `SINGLE` 会话，
  消息收发、历史、未读全部走既有接口，**不需要任何 AI 专用消息接口**。
- 用户注册时会自动开通与 AI 的会话，并收到一条欢迎语（未读 +1）。
- 用户向含 AI 的会话发送 `TEXT` 消息后，AI 会异步回复。回复延迟取决于模型，
  前端应把它当作普通新消息处理（走 `message` 推送帧）。
- `mock = true` 表示当前是本地模拟模式，前端可在标题栏给出"模拟模式"提示。

`UserVO` 新增 `type` 字段，取值 `HUMAN` / `BOT`。

---

## 9. 社区（指南 / 配方 / AI 首答）

### 9.1 接口

| 方法 | 路径 | 鉴权 | 请求 | 返回 data |
|---|---|---|---|---|
| GET | `/api/community/guides` | 公开 | `type?=GUIDE\|RECIPE`, `q?`, `limit?=12` | `CommunityGuideCardVO[]` |
| GET | `/api/community/guides/{id}` | 公开 | - | `CommunityGuideVO` |
| GET | `/api/community/guides/by-skill` | 公开 | `fullName` | `CommunityGuideCardVO[]` |
| POST | `/api/community/guides/{id}/feedback` | 登录 | `{helpful: boolean}` | `{helpfulCount, notHelpfulCount, myFeedback}` |
| POST | `/api/community/ask` | 登录 | `{question: string}` | `{questionId, answer, mock, relatedGuides}` |
| POST | `/api/community/questions/{id}/feedback` | 登录 | `{helpful: boolean}` | `null` |

### 9.2 数据结构

```ts
type CommunitySkillVO = {
  fullName: string; name: string; owner: string; htmlUrl: string;
}

type CommunityGuideCardVO = {
  id: number; type: 'GUIDE' | 'RECIPE';
  title: string; summary: string; emoji: string | null;
  tags: string[]; difficulty: string | null;
  duration: string | null; cost: string | null;
  aiGenerated: boolean; helpfulCount: number;
  publishedAt: string | null;
  relatedSkills: CommunitySkillVO[];
}

type CommunityGuideVO = CommunityGuideCardVO & {
  content: string; promptText: string | null;
  reviewStatus: string; reviewedBy: string | null;
  notHelpfulCount: number; viewCount: number;
  myFeedback: boolean | null;   // 游客为 null
}
```

### 9.3 行为约定

- 社区 GET 允许游客访问；提问、反馈需要登录。
- 同一用户对同一篇指南重复反馈只更新态度，不重复计数。
- `ask` 会保存问题与首答；`mock = true` 表示当前没有真实模型，答案由指南检索生成。
- 首次启动且 `t_community_guide` 为空时，服务端导入 20 篇 AI 整理的种子内容（15 指南 + 5 配方），并标记 `aiGenerated=true`。运营复核前，前端统一显示“AI 整理 · 试验版”。

---

## 10. 大肥鱼总结（技能 README）

| 方法 | 路径 | 鉴权 | 请求 | 返回 data |
|---|---|---|---|---|
| GET | `/api/skills/{id}/summary` | 登录 | `refresh?=false` | `SkillSummaryVO` |

```ts
type SkillSummaryVO = {
  skillId: number;
  summary: string;        // Markdown
  aiGenerated: boolean;   // false = 规则摘要/模型降级
  model: string;          // 例如 openai-compatible(glm-4-flash)
  generatedAt: string;
  notice: string | null;
}
```

行为：

- 只使用当前技能的 README + 仓库元信息，不混入其他仓库内容。
- 默认缓存 30 分钟；`refresh=true` 强制重新生成。
- 模型不可用、超时或返回空时，返回规则摘要并把 `aiGenerated` 设为 `false`，`notice` 说明原因。
- 总结人格由 `prompts/whale-*.md` 控制：先写一段按当前 README 内容变化的“大肥鱼点评”，再保持技术编辑结构；不使用固定开场，正文不卖萌刷屏。

### 免费云端（当前默认）：智谱 GLM-4-Flash

当前生产配置使用智谱云端 `glm-4-flash`（官方定价页标为免费文本模型，国内直连，OpenAI 兼容）：

```env
AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
AI_API_KEY=<你的智谱 API Key>
AI_MODEL=glm-4-flash
AI_TIMEOUT_SECONDS=120
```

- API Key 入口：<https://bigmodel.cn/usercenter/proj-mgmt/apikeys>
- 后端已兼容智谱的 `/api/paas/v4/chat/completions` 路径（base 以 `/v4` 结尾时不再追加 `/v1`）。
- `glm-4.7-flash` 虽然也标为免费，但实测高峰期会返回 `429 该模型当前访问量过大`，所以默认用更稳的 `glm-4-flash`；需要更强能力时可手动切换并做重试。
- “免费”是平台当前定价，后续可能调整；如果额度/价格变化，把 `.env` 切回 Ollama 即可。

### 本地 Ollama（备选/离线）

本机保留了 `qwen2.5:3b`，需要离线或云端不可用时改回：

```env
AI_BASE_URL=http://host.docker.internal:11434/v1
AI_API_KEY=ollama
AI_MODEL=qwen2.5:3b
```

Ollama 必须监听 `0.0.0.0:11434`，否则容器内的后端无法访问。



