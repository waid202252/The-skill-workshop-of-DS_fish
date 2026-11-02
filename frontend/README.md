# 微聊 WeLiao · 前端

Vue 3 + TypeScript + Vite + Pinia + vue-router + axios + 原生 WebSocket 实现的类 QQ 即时通讯前端。

## 运行

```bash
pnpm install
pnpm dev      # http://127.0.0.1:5173
pnpm build    # vue-tsc 类型检查 + vite 打包
```

开发服务器把 `/api`、`/uploads` 代理到 `http://127.0.0.1:8080`，`/ws` 以 WebSocket 方式代理到 `ws://127.0.0.1:8080`。

## 目录

```
src/
├── main.ts                 # 应用入口、401 全局处理、Pinia/Router 装配
├── App.vue                 # 根组件（路由出口 + 全局轻提示）
├── router/index.ts         # 路由与登录守卫
├── types/api.ts            # 与 docs/API.md 一一对应的契约类型
├── types/ui.ts             # 渲染层类型（待确认消息、列表项、Toast 等）
├── services/
│   ├── http.ts             # axios 实例、信封拆包、ApiError、401 处理
│   ├── api.ts              # 所有 REST 接口封装
│   ├── ws.ts               # WebSocket 单例（心跳 / 断线判定 / 指数退避重连）
│   └── token.ts            # token 的 localStorage 读写
├── stores/
│   ├── auth.ts             # 登录态与当前用户
│   └── chat.ts             # 会话、消息、好友、申请、乐观发送、WS 事件
├── views/
│   ├── LoginView.vue       # 登录 / 注册
│   └── ChatView.vue        # 三栏主界面
├── components/             # 导航、会话列表、联系人、聊天区、申请面板等
├── composables/toast.ts    # 全局轻提示
├── utils/                  # 时间、格式化、clientMsgId、错误文案
└── styles/main.css         # 全局变量与基础样式
```

## 实现要点

- **WebSocket**：`services/ws.ts` 单例，25s 心跳、60s 无帧判死、1s/2s/4s/8s/15s 指数退避重连，`onMessage` 订阅。
- **乐观发送**：本地先插入 `clientMsgId` 临时气泡，`ack` 用真实 `MessageVO` 替换，`error` 标红可重试；WS 不可用时自动走 REST 兜底。
- **去重**：消息按 `id`、`seq`、`clientMsgId` 三重判重，`ack` 与 `message` 重复投递不会渲染两次。
- **滚动锚点**：加载更早消息前后记录 `scrollHeight` 差值并恢复 `scrollTop`；只有用户贴底时才自动滚到最新消息。
- **重连补齐**：收到 `ready` 后重新拉取会话 / 好友 / 申请；若当前会话有离线未读，重建聊天区并拉取最新一页消息。
