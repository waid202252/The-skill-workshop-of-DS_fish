# 架构说明

本文说明微聊的数据模型与关键设计决策，尤其是**即时通讯里最容易做错的那几处**。

---

## 一、分层与依赖方向

```
controller（HTTP 入口、参数校验）
    │  只做「取当前用户 → 调 service → 包信封」
    ▼
service（业务规则、事务边界）
    │  ├── UserService      注册登录、资料
    │  ├── FriendService    好友关系、申请流转
    │  ├── ConversationService  会话与成员管理、已读
    │  ├── MessageService   消息落库、序号分配、历史分页、分发
    │  ├── FileService      上传落盘
    │  └── VoMapper         实体 → VO（集中处理在线态与发送者信息）
    ▼
repository（Spring Data JPA）
    ▼
entity（8 张表）
```

依赖是单向的：`controller → service → repository → entity`。
`ws` 包横切在 service 之上——`service` 回调 `WsHub` 推送，而 `WsHub` **不依赖任何 service**，
这样避免了「service ↔ WebSocket 处理器」的循环依赖，WebSocket 入口和 REST 入口复用同一套 service。

`cluster` 包只被 `ws` 依赖，负责「这个用户在哪些节点上」与「把信封送到另一个节点」：

```
WsHub ──▶ ClusterRuntime ──┬── PresenceStore（Redis Hash / 内存 Map，二选一）
                           └── ClusterBus   （Redis Pub/Sub / 空实现，二选一）
```

`ClusterRuntime` 在启动时探测 Redis 并装配这两个实现，模式的取舍见第五章。

前端按职责拆成 `services（http / ws / token）→ stores（auth / chat）→ views / components`，
组件不直接发请求，所有状态变更收敛在 Pinia store 里。

---

## 二、数据模型

### 表一览

| 表 | 作用 | 关键约束 |
|---|---|---|
| `t_user` | 账号与资料 | `username` 唯一 |
| `t_friend` | 好友关系，**双向各一行** | `(user_id, friend_id)` 唯一 |
| `t_friend_request` | 好友申请 | 按 `(to_user_id, status)` 建索引 |
| `t_conversation` | 会话（单聊/群聊合并一张表） | 维护 `max_seq` 水位 |
| `t_conversation_member` | 会话成员 + 已读位置 | `(conversation_id, user_id)` 唯一 |
| `t_message` | 消息 | `(conversation_id, seq)` 唯一 |
| `t_skill` | GitHub 技能仓库的本地缓存 | `github_id`、`full_name` 唯一 |
| `t_skill_install` | 用户安装（收藏）的技能 | `(user_id, skill_id)` 唯一 |

> 表结构不是 Hibernate 生成的，而是 `backend/src/main/resources/db/migration/` 下的 Flyway 脚本
> （V1 基线 / V2 消息撤回 / V3 技能市场 / V4 单聊去重键），`ddl-auto` 固定为 `validate`。

### 为什么这么设计

**好友关系存双向两行，而不是一条 `(a, b)` 加 `WHERE a=? OR b=?`。**
查询「我的好友」变成一次单表扫 `user_id` 索引，不需要 OR 和去重；
代价是建立关系要写两行，但建关系是低频操作，查好友是高频操作，这个取舍是划算的。

**单聊和群聊共用 `t_conversation`，用 `type` 区分。**
消息表、成员表、未读数、分页逻辑全部复用一套。
如果拆成两张表，每个消息相关的功能都要写两遍。
单聊的「对方」= 成员表里除自己外的那一个，读取时动态算，不在会话表冗余 `peer_id`，
避免用户改昵称后展示不一致。

**成员表带 `last_read_seq`。**
未读数是「每个人看到的不一样」的状态，只能放在成员维度。
放在会话表就是全局一个值，多端/多人都对不上。

---

## 三、关键设计决策

### 1. 会话内单调递增的 `seq`，而不是用主键或时间戳排序

消息表除了自增主键 `id`，每个会话还有一个从 1 开始严格递增的 `seq`。

**为什么不用 `id` 或 `created_at` 排序：**

- 主键 `id` 是多会话共享的自增序列，同一会话内虽然递增，但用它做游标分页会把
  「别的会话插进来的 id」也算进范围，语义混乱，且无法表达「会话内的第几条」。
- `created_at` 精度不够，同一毫秒内多条消息顺序不确定；服务器时间回拨还会直接翻车。

**怎么保证 `seq` 不重号：**
发消息时对会话行加悲观写锁，再读 `max_seq` 自增：

```sql
SELECT * FROM t_conversation WHERE id = ? FOR UPDATE;   -- 锁住这一行
-- seq = max_seq + 1
UPDATE t_conversation SET max_seq = ?, last_message_id = ?, updated_at = ? WHERE id = ?;
```

锁的粒度是**单个会话**，不同会话互不阻塞。
如果不加锁，两个并发请求读到同一个 `max_seq` 就会产生重复 `seq`，
而 `(conversation_id, seq)` 上有唯一约束，后到的那个会直接插入失败。

### 2. 先落库、提交后再推送

```
事务开始
  ├─ 校验成员身份
  ├─ 锁会话 → 分配 seq → insert message → 更新会话水位
  ├─ 注册 afterCommit 回调（推送动作）
事务提交  ──────────────────────────► 触发推送
```

如果边写库边推送，客户端可能在事务提交前就收到 WebSocket 帧，
立刻回查历史时读到的是旧数据（甚至查不到这条消息），表现为「消息闪一下就消失」。

实现用 `TransactionSynchronizationManager.registerSynchronization`，
把推送挂在 `afterCommit` 上，见 `common/Tx.java`。

### 3. 离线消息不建离线队列

常见做法是「用户离线 → 消息塞进离线队列 → 上线后拉队列并删除」。
这套东西要额外维护：队列存储、投递确认、重复投递、队列与消息表的一致性。

这里不这么干：**消息本来就在 `t_message` 里，且会话有 `max_seq`、成员有 `last_read_seq`。**
用户重连后：

- 拉会话列表，`unread > 0` 的会话就是有未读的
- 对当前打开的会话，用 `afterSeq = 本地最大 seq` 增量补齐

少了一套中间件和一套一致性维护，效果等价。
代价是「补齐」依赖客户端记录本地游标——这对浏览器/App 都是天然具备的。

### 4. 分页支持两个方向

`GET /{id}/messages` 同时支持：

| 参数 | 语义 | 用途 |
|---|---|---|
| `beforeSeq` | 取 `seq < beforeSeq` 的最近 N 条（倒序取、反转成升序） | 上拉加载历史 |
| `afterSeq` | 取 `seq > afterSeq` 的最早 N 条（升序） | 断线重连补齐 |

两者都多取一条来判断 `hasMore`，避免额外的 count 查询。

只有 `beforeSeq` 是很多 IM 实现的通病：重连后客户端只能整页重拉「最新一页」，
离线期间新增超过一页的消息就永久丢了。

### 5. 未读数用 count 而不是减法

```sql
SELECT count(*) FROM t_message
WHERE conversation_id = ? AND seq > ?/*last_read_seq*/ AND sender_id <> ?/*me*/;
```

不用 `maxSeq - lastReadSeq`，因为自己发的消息不应该算未读。
群聊里如果按减法算，自己连发 10 条，未读数会变成 10，明显是错的。
代价是每次列表刷新要按会话查一次 count——会话数在几十个量级时完全可接受，
量级上去后可以改为「会话表冗余未读数 + 读时清零」的写扩散方案。

### 6. 多端在线：一个用户对多个连接

`WsHub` 维护 `userId → Set<WebSocketSession>`，并同步维护 `sessionId → userId` 反向索引。

- 连接数 `0 → 1` 才算「上线」，推 `presence online`
- 连接数 `1 → 0` 才算「离线」，推 `presence offline`
- 推送消息时遍历该用户的所有连接，多端天然同步

会话对象用 `ConcurrentWebSocketSessionDecorator` 包了一层，
因为原生 `WebSocketSession` 不是线程安全的，多线程同时推送会抛
`IllegalStateException: The remote endpoint was in state [TEXT_FULL_WRITING]`。

`afterConnectionClosed` 里按 `session.getId()` 注销——装饰器会代理 `getId()`，
所以注册时的 id 和关闭时的 id 一致，不会泄漏。

### 7. 在线状态只推给有共同会话的人

```sql
SELECT DISTINCT m2.user_id FROM t_conversation_member m1, t_conversation_member m2
WHERE m1.conversation_id = m2.conversation_id
  AND m1.user_id = ?          -- 我
  AND m2.user_id <> ?;        -- 和我同处任一会话的所有人
```

不做全站广播：用户量大时全站广播是 O(在线用户数) 的开销，
而这套系统里「陌生人上线」本来就没有展示场景。

### 8. 认证：无状态 JWT + 自定义过滤器

- 登录签发 HS256 JWT，`sub` = 用户 id，默认 7 天
- `JwtAuthFilter` 解析后把 `userId` 直接放进 `SecurityContext` 的 principal
- 未登录/过期由 `AuthenticationEntryPoint` 返回 `40100` 信封，不是 Spring 默认的 HTML 错误页
- WebSocket 不能在 handshake 带自定义 header，所以 token 走 query：`/ws?token=xxx`，
  在 `HandshakeInterceptor` 里校验，非法直接拒绝握手

`spring.autoconfigure.exclude` 关掉了 `UserDetailsServiceAutoConfiguration`，
否则启动时会打印一个无用的随机密码。

### 9. 推送失败不能影响消息本身

`fanout` 整体包了 try/catch：某个连接发送异常只关掉那个连接，
消息已经落库，客户端重连后会通过 `afterSeq` 补齐。
**推送是尽力而为的加速手段，不是可靠性来源。**

---

## 四、一次发消息的完整时序

```
Alice 前端                    Server                          Bob 前端
    │                            │                                │
    │ ws.send({type:'send',      │                                │
    │   data:{conversationId,    │                                │
    │         clientMsgId,       │                                │
    │         type:'TEXT',       │                                │
    │         content:'hi'}})    │                                │
    │ ──────────────────────────►│                                │
    │                            │ 1. 校验 Alice 是会话成员        │
    │                            │ 2. SELECT ... FOR UPDATE 锁会话 │
    │                            │ 3. seq = max_seq + 1           │
    │                            │ 4. INSERT message              │
    │                            │ 5. UPDATE 会话水位              │
    │                            │ 6. COMMIT                      │
    │                            │ 7. afterCommit → 广播           │
    │ ◄── {type:'ack', data:{   │                                │
    │       clientMsgId, message}} ◄──────────────────────────────┤
    │     （用 clientMsgId 把      │ ──► {type:'message',           │
    │      本地气泡替换成真消息）  │        data:{message}}         │
```

- Alice 本地先插入一条 `clientMsgId` 对应的「发送中」气泡，收到 `ack` 再替换，用户体验上零延迟
- Alice 也会收到 `message` 广播（多端同步），前端按 `id` / `seq` / `clientMsgId` 三重去重
- 3 秒内没收到 `ack` 就标记失败，允许点击重发

---

## 五、多实例（跨节点推送）

单实例时所有连接都在一个 JVM 里，`WsHub` 的内存会话表就够了。多实例之后这张表不够用——
A 连在实例 1、B 连在实例 2，实例 1 再把消息推给 B 就推不到。这部分已经实现，不是待办。

### 5.1 两层结构

```
① 在线登记表（Redis Hash，带 TTL）
   im:presence:<userId> = { "<nodeId>" : <lastHeartbeatMillis> , ... }

② 投递总线（Redis Pub/Sub，精确通道）
   im:ws:<nodeId>   ←  每个实例只订阅自己的一条
```

发送路径：

```
WsHub.sendToAll(成员)
  ├─ deliverLocal(user)          本机有连接就直接推
  └─ forwardRemote(user)         查 im:presence:<user>，对每个≠本机的节点 PUBLISH im:ws:<node>
                                     └─ 目标节点收到 → deliverRaw → deliverLocal（到此为止，不再转发）
```

### 5.2 几个刻意的设计选择

| 选择 | 为什么 |
|---|---|
| Hash 存「用户 → 节点集合」，不是「(用户,节点) 一个 key」 | 一个 key 才能一次 HGETALL 拿到该用户所有节点；拆成多个 key 就必须 SCAN 全库才能反查 |
| 字段里带时间戳，key 再配 TTL | 实例被 `kill -9` 时来不及删自己的字段，靠时间戳过滤把它判成离线；key 的 TTL 保证用户彻底下线后不留空 Hash |
| 精确通道 `im:ws:<node>`，不是全体广播 | 广播通道下每台机器都要收全站消息、再各自判断是否相关，扇出是 N 倍；精确通道的成本只和目标用户所在的节点数有关 |
| 接收侧只做本地投递 | 一旦接收侧也走 `sendToAll`，两个实例会来回反弹，一条消息被无限复制 |
| 本地命中仍然继续转发 | 一个用户可能同时挂在多个实例上（多端），本地推成功不代表别的节点不用推 |
| Redis Pub/Sub 而非 Stream/MQ | 在线状态这类推送可丢（丢了客户端重连靠 `afterSeq` 补齐，见第三章第 4 条）；换成 MQ 要额外承担顺序与重试，当前收益不抵成本 |

### 5.3 降级与开关

`app.cluster.mode`（环境变量 `CLUSTER_MODE`）三档：

- `auto`（默认）：启动时 `PING` 一次 Redis，通了走跨节点，不通退回 `InMemoryPresenceStore` + `NoopClusterBus`。
  **本机没装 Redis 时行为和改造前完全一致**，本地开发零外部依赖这条底线保住了。
- `local`：强制单机，连探测都不做。
- `redis`：强制 Redis，连不上直接启动失败——生产用它，把配置错误暴露在部署阶段，
  而不是等上线后某个用户在另一台实例上收不到消息才发现。

⚠️ 顺带一个坑：`spring-boot-starter-data-redis` 在 classpath 上时，Actuator 默认会加一个
Redis 健康检查，Redis 不在就让 `/actuator/health` 变 DOWN、连带容器 healthcheck 失败。
所以 `management.health.redis.enabled` 被显式关掉了。

### 5.4 已知取舍

- `WsHub.isOnline()` 每次查一次 Redis（好友列表/群成员列表会逐个调用）。当前量级下是
  N 次 HGETALL，真实高压场景应该改成一次 pipeline，或把在线状态下推到网关层本地缓存。
- 实例被强杀时，它的在线记录要等 `presence-ttl-seconds`（默认 90s）才失效，
  这段时间内别的实例仍会把消息投给一个已经不存在的节点，消息在 Pub/Sub 里丢掉。
  这是「TTL 兜底」换来的代价，属于 5.2 表里最后一条的同一类取舍。

---

### 5.5 限流计数也必须共享

长连接共享了、限流却各自数的话，多实例只做了一半：两个实例各放行 60 次/分钟，
实际放行 120 次——脚本刷两下就突破。所以计数跟着同一份 Redis 走：

```
im:rl:auth:1.2.3.4:1793432880000  =  3      ← 值 = 本窗口内已请求次数
                        └ 窗口起始毫秒        ← TTL 到窗口结束自动消失
```

核心只有三行 Lua：

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return count
```

**三个必须讲清楚的细节**：

1. **为什么必须用 Lua 包住 INCR + PEXPIRE**：分两次发命令，如果 INCR 成功而 PEXPIRE 没执行
   （进程崩、网络断），这个 key 就永不过期。后果是每分钟泄漏一个 key，几天后把 Redis 写满，
   而且该维度的计数永久卡死——那个 IP 的用户再也登不进来。
2. **为什么只在 `count == 1` 时设过期**：每次 INCR 都 PEXPIRE 会把窗口不断往后推，
   等价于永不过期，计数只增不减。这是分布式限流最经典的坑。
3. **为什么 key 里要带窗口起始时间**：换窗口 = 换 key，不需要扫描重置旧 key，也没有
   「重置动作失败导致窗口错乱」这种状态；旧 key 靠 TTL 自然消失。

**降级是两层的，缺一不可**：

| 层 | 行为 | 为什么 |
|---|---|---|
| 调用失败 → 本机计数 | 仍然拦，只是多实例各自算 | 比 fail-open（直接放行）安全；限得松一点好过形同虚设 |
| 失败后熔断 5 秒 | 期间不再碰 Redis，直接本机计数 | 否则每个请求都要白等一次 2s 超时，等于用另一种方式把服务打死 |

实测（Redis 中途挂掉，40 个并发请求打登录接口）：**30 放行 / 10 拦截，总耗时 3s**——
熔断前那批请求各等了 2s 超时，熔断后不再等待。

---

## 六、扩展路径

当前是**可水平扩展的单体架构**（多实例靠 Redis 打通长连接），下面是继续往生产规模走时
还需要动的点，按优先级排列：

| 目标 | 改法 |
|---|---|
| 推送不丢 | 推送改走 MQ（Kafka/RocketMQ），消费端负责推送与重试；`afterSeq` 补齐作为兜底 |
| 文件服务 | `FileService` 换成对象存储（OSS/S3），返回 CDN 地址；大文件走分片上传 |
| 已读回执精确到消息 | 用 `(conversation_id, user_id, max_read_seq)` 已足够；如需「某人读到第几条」的群已读，需要额外一张 `read_receipt` 表 |
| 消息搜索 | 引入 Elasticsearch，用 outbox 模式同步；不要直接双写 |
| 会话列表性能 | 当前是逐会话组装（N+1），会话上千时改为一次批量聚合查询，或把 `unread`/`last_message` 冗余到成员表做写扩散 |
| 消息撤回 | 加 `recalled` 标记 + `recall_at`，推送 `message_recalled` 帧，前端替换气泡；不要物理删除 |
| 水平分表 | `t_message` 按 `conversation_id` 哈希分表，会话维度天然聚簇，跨会话查询本来就没有 |

---

## 七、为什么不直接抄 QQ 的架构

QQ/微信的服务端是数万人规模团队二十年演进的结果，涉及自研协议、多机房、单元化、
海量长连接网关、消息序号全局唯一分配服务等。以一个人或一个小团队的项目体量去复刻它，
得到的一定是「抄了形状、跑不起来」。

这个项目的选择是：**保留 IM 的核心正确性问题**（顺序、不丢、不重、未读准确、多端一致），
用能讲清楚的最简方案实现，并把扩展点留在文末这张表里。
单实例时是「一个 JVM + 一个数据库」；需要扩容时把 `CLUSTER_MODE` 交给 Redis，
长连接就能跨实例互通——不是一步到位做成微服务，而是每个阶段都留有可验证的退路。
