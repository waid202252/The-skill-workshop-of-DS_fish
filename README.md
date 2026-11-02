# 大肥鱼的工坊 — AI 技能获取社区（附即时通讯）

> **想快速了解设计**：[架构与关键决策](docs/ARCHITECTURE.md) ·
> [接口契约](docs/API.md) · [小体量服务器部署](docs/DEPLOY-SMALL.md)
>
> **想跑起来**：Windows 双击 `start.cmd`；或 `cd deploy && cp .env.example .env && docker compose up -d --build`，
> 打开 <http://localhost:8080>。

一个从 **GitHub 拉取 AI 技能**的全栈应用。主界面是技能广场：搜索、浏览、安装各类 AI 技能
（视频生成、图像生成、Agent 工具等）；聊天是附带的辅助能力，用于围绕技能交流。

后端 Spring Boot 3 + MySQL/H2，前端 Vue 3 + TypeScript，技能数据经 GitHub API 拉取后本地缓存，
聊天走原生 WebSocket。

```
┌──────────────────┐  REST /api/skills/*   ┌──────────────┐   GitHub API
│  Vue 3 SPA       │ ────────────────────► │ Spring Boot  │ ─────────────► api.github.com
│  /skills  主视图  │  REST /api/*  + WS    │  8080        │
│  /chat    辅助    │ ◄──────────────────── │              │
└──────────────────┘                       └──────┬───────┘
                                                  │ JPA
                                             ┌────▼─────┐
                                             │ MySQL/H2 │
                                             └──────────┘
```

---

## 一、功能清单

### 已实现（P1 可用版）

| 模块 | 能力 |
|---|---|
| 账号 | 注册、登录、JWT 鉴权（7 天有效期）、改昵称/头像/签名 |
| 好友 | 搜索用户、发送好友申请（带附言）、通过/拒绝、好友列表、删除好友 |
| 单聊 | 两人会话自动创建（幂等）、实时收发 |
| 群聊 | 创建群、邀请成员、移除成员、退群、群主自动转让、系统消息 |
| 消息 | 文本 / 图片 / 文件 / 系统消息四种类型 |
| 可靠性 | 服务端落库后推送、`clientMsgId` 发送回执与失败重试、多端去重 |
| 历史 | 按 `seq` 游标分页，上拉加载更早消息 |
| 未读 | 会话未读数精确统计，标记已读后归零，已读回执实时推给对方 |
| 在线状态 | 多端连接计数、上下线广播（仅推给有共同会话的联系人） |
| **多实例推送** | 在线状态与消息投递可跨进程：Redis 在线登记表 + 每节点一条 Pub/Sub 通道；没有 Redis 时自动退回单机内存，不影响启动 |
| 文件 | 20MB 上限、白名单扩展名、按年月分目录、UUID 命名防路径穿越 |
| 离线消息 | 断线期间消息落库，重连后通过历史接口补齐 |
| **AI 伙伴** | 内置「大肥鱼」鲸鱼娘人设（社区开源人设安全裁剪版），注册即自动开通会话；聊天与 README 总结共用智谱云端模型 |
| **大肥鱼总结** | 打开技能详情自动总结 README，输出「大肥鱼点评 / 一句话 / 能做什么 / 适合谁 / 怎么开始 / 注意」；点评按当前技能内容生成，不是固定模板；当前走智谱云端 `glm-4-flash`，30 分钟缓存，失败退回规则摘要，可切回本地 Ollama |
| **工坊社区（试验）** | AI 整理的 15 篇入门指南 + 5 个可运行配方；游客可读，登录后可提问/反馈；AI 首答基于社区指南检索 |
| **消息撤回** | 2 分钟内可撤回自己的消息，双方界面同步为「已撤回」，保留行与 seq |
| **防刷限流** | 登录/注册按 IP、发消息按用户，一分钟固定窗口，超限返回 429；多实例部署时计数走 Redis 共享，真正的配额是全局的 |
| **桌面通知** | 页面在后台时，他人消息弹浏览器通知，点击回到对应会话 |

### 明确未做（避免误解为残缺）

语音/视频通话、消息搜索、朋友圈/空间、红包、
多端已读同步的完整实现（当前只推给会话内其他成员）。

### 技能市场（主功能）

**产品定位：推荐 + 下载，不做执行。** 不接各家的模型 API，专注于帮用户发现值得用的技能并拿到手。

| 能力 | 说明 |
|---|---|
| 推荐分类 | 8 个预置分类（AI 视频生成 / 图像生成 / Agent 框架 / 提示词工程 / 语音音频 / 代码助手 / RAG / 工作流），点一下就是一个预设搜索 |
| 搜索技能 | 从 GitHub 搜仓库，按 star 排序，支持任意关键词（`ai video generation`、`agent skill`…） |
| 推荐列表 | 本地缓存里 star 最高的 20 个，**不消耗任何 GitHub 配额** |
| 详情 | 展示描述、topics、star、语言、安装数，并直接拉取仓库的说明文档正文 |
| 安装/卸载 | 每人一份安装关系，幂等；记录全局安装次数 |
| **下载** | 三种方式，见下表 |

#### 三种下载方式（按用户实际需求分）

| 方式 | 拿到什么 | 实现 |
|---|---|---|
| **复制 clone 命令** | `git clone <repo>.git`，适合开发者 | 纯前端写剪贴板 |
| **下载说明文档** | README.md / SKILL.md 文件本体 | **服务端代理** `GET /api/skills/{id}/readme/download` |
| **下载整包 zip** | GitHub 的 `archive/HEAD.zip` | 交给 GitHub，服务端不代理大文件 |

**为什么说明文档要走服务端代理，而不是让浏览器直连 raw.githubusercontent：**

1. 接口需要 Bearer token，裸 `<a href>` 带不上鉴权头
2. 国内网络直连 raw 经常失败，服务端有 10 分钟缓存可以兜住
3. 顺带做了文件名和 Content-Disposition 处理，下载下来就是正确的 `.md`

**为什么 zip 反而不代理：** 仓库可能有几百 MB，让服务端转发既费带宽又占内存，
直接引导到 GitHub 的归档地址更合理——这是"该代理的代理，不该代理的别碰"。

#### 配额现实

GitHub 未认证配额：**search 10 次/分钟、core 60 次/小时**。所以：

- 服务端对 `(查询词, 页码, 每页数)` 做 5 分钟缓存
- 前端搜索**按回车触发**，不做输入即搜（否则几秒就打满）
- 推荐列表和分类首屏尽量走本地缓存
- 配了 `GITHUB_TOKEN` 提到 30 次/分钟、5000 次/小时

#### 降级

GitHub 限流或断网时接口不报错，退回**已缓存技能的本地模糊搜索**，
响应里带 `degraded=true` 和提示文案，页面照常可用。

这些都在 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 的「扩展路径」里写了怎么加。

---

## 二、快速开始

### 一键启动（最省事，推荐）

前提只有一条：**装了 Docker Desktop 并让它处于运行状态**。不需要装 JDK、Maven、Node、MySQL。

```powershell
# 双击仓库根目录的 start.cmd，或在终端里：
.\start.cmd
```

脚本会依次完成：检查 Docker 引擎 → 启动容器 → 等待服务就绪 → **自动探测局域网地址**，
最后把网址直接打出来：

```
  网站地址
  ------------------------------------------------------
    本机访问 :  http://localhost:8080
    局域网   :  http://10.6.9.182:8080   ← 手机／同事用这个
    网卡     :  WLAN  (10.6.9.182)
  ------------------------------------------------------
```

| 命令 | 作用 |
|---|---|
| `.\start.cmd` | 启动服务并打印访问地址 |
| `.\start.cmd -Status` | 只看当前状态和地址，不做变更（需要 Docker 在运行） |
| `.\start.cmd -Url` | **只读上次写入的地址文件**，不需要 Docker 在运行 |
| `.\start.cmd -Stop` | 停止服务（数据保留在 Docker 卷里） |
| `.\start.cmd -Rebuild` | 改了前后端代码后，重建镜像再启动 |

**地址会落盘**：每次启动/查询都会把访问信息写进 `runtime/last-url.txt`（该目录已在 `.gitignore` 里）。
所以关掉启动窗口后，随时可以用下面这条查回来——即使 Docker 已经关了：

```powershell
.\start.cmd -Url
```

文件内容同时有「人直接读」的说明行和「机器能解析」的 `key=value` 行：

```
port=8080
local_url=http://localhost:8080
lan_ip=10.6.9.182
lan_url=http://10.6.9.182:8080
adapter=WLAN
health_url=http://10.6.9.182:8080/actuator/health
```

> 注意：`-Url` 读的是**上次启动时**的地址。如果换了 WiFi 导致 IP 变了，
> 要用 `.\start.cmd -Status` 重新探测（那个需要 Docker 在运行）。

**地址是怎么探测出来的**：优先取默认路由所在网卡的 IPv4，
并排除 `vEthernet` / `WSL` / `VPN` / `Radmin` / `VMware` 等虚拟网卡——
否则会拿到 `172.27.x.x`（WSL）或 VPN 地址，手机根本连不上。

**手机连不上怎么办**：脚本会自测局域网地址，不通时直接提示防火墙放行命令。
另外确认手机和电脑连的是同一个 WiFi；公司网络若开了 AP 隔离，设备之间无法互访。

> `start.cmd` 会自动优先用 PowerShell 7（`pwsh`），没有则回退到 Windows PowerShell 5.1。
> `scripts/start.ps1` 带 UTF-8 BOM，5.1 下中文也不会乱码。
>
> **维护提醒：`start.cmd` 必须保持纯 ASCII。**
> cmd.exe 按系统代码页（中文 Windows 是 GBK）逐字节解析批处理文件；
> 一旦文件里同时出现 UTF-8 中文和 `chcp` 切换，字节偏移就会错位，
> 脚本被从中间劈开、输出碎片、窗口瞬间闪退。
> 所有中文输出都放在 `scripts/start.ps1` 里，那个文件有 BOM 是安全的。

---

### 或者：分步手动启动（改代码时用）

### 环境要求

- **JDK 17+**、**Maven 3.9+**
- **Node 20+**、**pnpm**
- 数据库：**无需安装**，默认用内嵌 H2 文件库（开箱即跑）

### 1. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端起在 `http://127.0.0.1:8080`，首次启动由 **Flyway** 自动执行迁移脚本建表。
健康检查：`curl http://127.0.0.1:8080/actuator/health` → `{"status":"UP"}`。
H2 控制台：`http://127.0.0.1:8080/h2-console`（JDBC URL `jdbc:h2:file:./data/imdb`，用户 `sa`，密码空）。

### 2. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

打开 `http://localhost:5173`。

`vite.config.ts` 已配置代理：`/api`、`/ws`、`/uploads` 全部转发到 `127.0.0.1:8080`，
所以浏览器只跟 5173 打交道，不存在跨域问题。

### 3. 试一下

同一浏览器开两个无痕窗口，或一个普通窗口 + 一个无痕窗口：

1. 各自注册一个账号
2. A 搜索 B 的用户名 → 加好友
3. B 在「新的朋友」里通过
4. 点好友开始聊天，消息实时到达

---

## 三、切换到 MySQL（生产）

MySQL 未做默认，因为不想让「跑起来」依赖外部服务。切换只需一个 profile：

```bash
# 1. 建库
mysql -uroot -p -e "CREATE DATABASE weliao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 用 prod profile 启动（表结构由 Flyway 自动创建）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=prod
# 或打包后：
java -jar target/im-server-1.0.0.jar --spring.profiles.active=prod
```

连接参数用环境变量覆盖，不要写死在代码里：

| 变量 | 默认值 |
|---|---|
| `MYSQL_HOST` | `127.0.0.1` |
| `MYSQL_PORT` | `3306` |
| `MYSQL_DB` | `weliao` |
| `MYSQL_USER` | `root` |
| `MYSQL_PASSWORD` | `root` |
| `IM_JWT_SECRET` | 内置默认值（**生产必须覆盖**，至少 32 字节） |

实体层对 H2 / MySQL 完全一致，没有方言相关的坑。

### 用 XAMPP 自带的 MariaDB（最省事）

XAMPP 自带的是 **MariaDB**，不是 Oracle MySQL，所以单独给了一个 `xampp` profile。

本机实测环境：`MariaDB 10.4.32`，端口 **3316**（默认 3306 被另一个 MySQL 占了，XAMPP 改过端口），
`root` 空密码。**先确认端口**——用 `netstat -ano | findstr mysqld` 或看 XAMPP 面板，
端口不对改了 `XAMPP_PORT` 环境变量即可。

```bash
# 1. 建库（只需一次）
mysql -h127.0.0.1 -P3316 -uroot -e "CREATE DATABASE IF NOT EXISTS weliao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 用 xampp profile 启动（表结构由 Flyway 自动创建）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=xampp
# 或
java -jar target/im-server-1.0.0.jar --spring.profiles.active=xampp
```

可覆盖的环境变量：`XAMPP_HOST`（默认 127.0.0.1）、`XAMPP_PORT`（默认 3316）、
`XAMPP_USER`（默认 root）、`XAMPP_PASSWORD`（默认空）、`MYSQL_DB`（默认 weliao）。

> **注意**：`xampp` profile 用的是 `MariaDBDialect`。如果哪天你切回 Oracle MySQL，
> 用 `prod` profile（`MySQLDialect`）即可，别混用。

---

## 四、目录结构

```
.
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/weliao/im/
│       ├── common/                 # 统一信封 R、业务异常、全局异常处理、事务回调工具
│       ├── config/                 # 安全、WebSocket、静态资源、配置属性
│       ├── security/               # JWT 签发/校验、鉴权过滤器、当前用户
│       ├── entity/                 # 8 张表的 JPA 实体 + 枚举
│       ├── repository/             # Spring Data 仓储
│       ├── dto/                    # 请求体（带 Bean Validation）
│       ├── vo/                     # 响应视图对象
│       ├── service/                # 业务逻辑（用户/好友/会话/消息/文件/VO 转换）
│       ├── controller/             # REST 接口
│       └── ws/                     # WebSocket 端点、会话登记、在线状态、推送
├── frontend/
│   └── src/
│       ├── services/               # http 封装、WebSocket 客户端、token 管理
│       ├── stores/                 # Pinia：auth、chat
│       ├── views/                  # LoginView、ChatView
│       ├── components/             # 会话列表、消息气泡、输入框、好友面板等 16 个组件
│       ├── types/                  # 与接口契约一一对应的 TS 类型
│       └── utils/                  # 时间格式化、错误处理、id 生成
├── docs/
│   ├── API.md                      # 接口契约（REST + WebSocket 帧）
│   └── ARCHITECTURE.md             # 数据模型与关键设计决策
└── scripts/
    ├── e2e.mjs                     # 后端端到端测试（48 项断言）
    └── proxy-check.mjs             # Vite 代理链路测试（12 项断言）
```

---

## 五、测试

两个脚本都是**真实调用**，不是 mock：

```bash
# 后端必须已启动在 8080
node scripts/e2e.mjs
```

覆盖：注册登录与参数校验、401/403/409 错误码、好友申请全流程、WebSocket 建连与
`ready/ack/message/pong/read/presence/friend_request` 全部帧、未读数增减、已读回执、
游标分页、越权访问、群系统消息、在线状态广播、文件上传与读取、非法扩展名拦截、错误帧。

```bash
# 前后端都要启动（后端 8080 + pnpm dev 5173）
node scripts/proxy-check.mjs
```

覆盖：页面加载、`/api` 代理转发与鉴权、`/ws` 代理下的完整消息往返、`/uploads` 代理读取。

每次运行都会注册随机用户，可反复执行。

---

## 六、设计要点速览

完整说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，这里只列最容易踩坑的四点：

1. **消息顺序**：会话表维护 `maxSeq` 水位，发消息时 `SELECT ... FOR UPDATE` 锁住会话行再自增。
   保证同一会话内 `seq` 严格递增且不重号，游标分页和未读数才有意义。
   如果只靠自增主键或时间戳排序，在并发下一定会乱序或重复。
2. **推送时机**：消息先落库，事务 **提交后** 才推 WebSocket。
   否则客户端收到推送立刻回查历史，可能查不到（读到未提交数据）。
3. **离线消息**：不单独做离线队列。消息本来就在表里，重连后客户端按 `lastReadSeq` 拉历史即可，
   少一套中间件和一套一致性维护成本。
4. **未读数**：`count(seq > lastReadSeq AND sender != me)`，不是 `maxSeq - lastReadSeq`，
   因为自己发的消息不该算未读。
5. **重连补齐**：历史接口除了 `beforeSeq`（往前翻）还支持 `afterSeq`（往后补）。
   断线期间不管积压多少条，客户端都能循环调用补齐，不会因为「一页只有 20 条」而漏消息。
   如果只有 `beforeSeq`，重连时只能整页重拉最新一页，超出一页的部分就永久丢了。

### 多实例部署（可选，需要 Redis）

单实例不需要 Redis。要起两个以上后端实例时，长连接分布在多个进程里，
「A 连在实例 1、B 连在实例 2」就必须让实例 1 知道 B 在哪台机器上——这部分已经实现：

- **在线登记表**：`im:presence:<userId>` 这个 Redis Hash 记着该用户在哪些节点上，字段值带心跳时间戳
- **投递总线**：每个实例只订阅自己的一条通道 `im:ws:<nodeId>`，发送方按登记表精确投递
- 详细设计与取舍见 [ARCHITECTURE.md 第五章](docs/ARCHITECTURE.md)

**开关只有一行**（`app.cluster.mode` / 环境变量 `CLUSTER_MODE`）：

| 值 | 行为 |
|---|---|
| `auto`（默认） | 启动时 ping 一次 Redis，通了就跨节点，不通自动退回单机内存。**没装 Redis 时和以前完全一样** |
| `local` | 强制单机，不碰 Redis |
| `redis` | 强制 Redis，连不上直接启动失败（生产用，问题暴露在部署阶段） |

**本地 5 分钟验证（Windows / PowerShell）**：

```powershell
# 1. 起一个 Redis（不挂卷，纯在线状态用，重启丢数据是设计如此）
docker run -d --name weliao-redis -p 6379:6379 redis:7-alpine

# 2. 两个终端各起一个实例。H2 文件库要加 AUTO_SERVER 才能被两个进程共享
#    （生产环境两个实例连同一个 MySQL，没有这个限制）
$env:CLUSTER_NODE_ID='ws-1'
java -jar backend/target/im-server-1.0.0.jar --server.port=18081 `
  '--spring.datasource.url=jdbc:h2:file:./backend/data/imdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE'

$env:CLUSTER_NODE_ID='ws-2'
java -jar backend/target/im-server-1.0.0.jar --server.port=18082 `
  '--spring.datasource.url=jdbc:h2:file:./backend/data/imdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE'

# 3. 确认两个实例都进了多实例模式
(Invoke-RestMethod http://127.0.0.1:18081/api/system/info).data.cluster
#   mode = multi-node(redis)，nodeId = ws-1
(Invoke-RestMethod http://127.0.0.1:18081/api/system/info).data.rateLimit
#   store = redis      ← 限流计数也共享了；显示 memory 说明没连上 Redis

# 4. 随便观察在线状态：登录前端后执行
docker exec weliao-redis -it redis-cli --scan --pattern 'im:presence:*'
docker exec weliao-redis redis-cli HGETALL im:presence:1
#   -> "ws-1" : 1761800000000   ← 用户 1 连在 ws-1 上
```

浏览器验证：两个窗口分别登录两个账号（前端代理指向不同端口即可），
**只要两人不在同一个实例上**还能互发消息，就说明跨节点投递生效了。
这里有个反直觉的点：如果两人恰好连在同一实例，走的是本地投递，看不出效果——
所以日志里要看得到 `跨节点投递已启动：本节点订阅 im:ws:ws-2` 之类的行。

嫌手工点太麻烦，也可以一条命令跑完上面全部步骤（它就是这些步骤的自动化版本）：

```powershell
./scripts/cluster-check.ps1 -PortA 18081 -PortB 18082
# 会自动建两个用户、加好友、建会话、各连一个实例、发一条消息并断言对端收到
# 退出码 0 = 通过，1 = 失败，可以直接接进 CI
```

停止后清理：`docker rm -f weliao-redis`

---

## 七、常见问题

**启动报 `Database may be already in use` / `The database is read only`**

开发用的 H2 文件库在进程被**强制杀死**（任务管理器结束进程、`Stop-Process -Force`、断电）时来不及正常关闭，
数据文件会处于不一致状态，下次启动 H2 会拒绝写入。

```powershell
# 停掉进程，删掉开发库，重启即可（只有本地测试数据会丢）
Remove-Item backend/data -Recurse -Force
```

正常用 `Ctrl+C` 停止服务就不会出现这个问题。生产环境用 MySQL，不存在该限制。

**`pnpm build` 报 `spawn EPERM`**

这是当前沙箱环境禁止子进程管道通信导致的，与代码无关；在普通开发机上 `pnpm build` 正常。

---

## 八、已知限制

- H2 文件库适合单机开发/小规模使用，**多实例部署必须切 MySQL**（多进程同时开 H2 文件库需要 `AUTO_SERVER=TRUE`）。
- 跨节点推送已实现（Redis 在线状态 + Pub/Sub），但 Redis **不是必需的**：没装就自动退回单机内存。
  代价是实例被 `kill -9` 时，它登记的在线状态要等 `presence-ttl-seconds`（默认 90s）才失效。
- 在线状态基于连接存活 + 心跳续期；进程正常退出会立刻清记录，异常退出靠 TTL 兜底。
- 限流是固定窗口，窗口边界可能出现双倍突发（59.9s 打满 60 次，60.0s 又能打满 60 次）；
  要消除得换滑动窗口（Redis ZSET 记时间戳），代价是每个请求多一次 ZREMRANGEBYSCORE，当前不值得。
  多实例时计数已走 Redis 共享（`im:rl:*`），Redis 挂掉会降级成本机计数并熔断 5 秒，宁可限得松也不打死接口。
- 上传文件存本地磁盘，多实例部署需要换成对象存储。
- 好友/会话列表是逐会话查询组装，会话数量到几百个时需要改为批量聚合查询；
  多实例模式下在线状态是逐个用户查 Redis，同理需要改批量（pipeline）。

---

## 十、数据库迁移（Flyway）

表结构不再是 Hibernate 运行时"猜"出来的，而是版本化脚本。这是"demo"和"真实项目"最硬的一条分界线。

```
backend/src/main/resources/db/migration/
├── V1__baseline_schema.sql     # 6 张表（基线）
└── V4__single_conversation_key.sql  # 单聊去重键
```

配置（`application.yml`）：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true     # 已有表但没有历史记录的库，以 V1 为基线
    validate-on-migrate: true
  jpa:
    hibernate:
      ddl-auto: validate           # Hibernate 只校验，不再改库
```

### 改表结构怎么做

**不要改实体让它自动同步**，新增一个脚本：

```sql
-- V2__message_recall.sql
ALTER TABLE t_message ADD COLUMN recalled TINYINT NOT NULL DEFAULT 0;
```

然后重启，Flyway 会按版本号顺序执行，并在 `flyway_schema_history` 表里留痕。

### 为什么这件事重要

判据只有一句：**别人 clone 你的仓库，能不能重建出一模一样的库？**

- 之前不能（结构在第一次启动时被动态生成）
- 现在能（有 V1 脚本 + 版本历史）

### 已有的旧库怎么迁

如果数据库里已经有表、但没有 `flyway_schema_history`，启动会失败。

> **`baseline-on-migrate` 已默认关闭**（`application.yml`）。
> 它原来的行为是：发现非空库但没有历史表时，把现状当作"已在 V1"并**跳过 V1**。
> 可旧库的结构未必和 V1 一致（例如缺 `t_user.type`），随后 `validate` 就会失败，报错还很难懂。
> 关掉之后 Flyway 直接报 `Found non-empty schema ... without schema history table`，失败更早也更清楚。
> 只有确实要接管一个结构已与 V1 完全一致的老库时，才临时打开它。

本地遇到这类问题，用重置脚本（**只删数据库卷，保留上传文件**）：

```powershell
.\scripts\reset-db.ps1            # 会要求输入 YES 确认
.\scripts\reset-db.ps1 -Force     # 跳过确认
```

它做的是 `compose down`（不删卷）→ 精确 `docker volume rm weliao_mysql-data` → `compose up -d`。
**不是** `docker compose down -v`——那会把 `uploads` 卷一起删掉。

### 备份

```powershell
.\scripts\backup.ps1
```

产物落在 `backups/`（已 gitignore）：

| 产物 | 内容 |
|---|---|
| `mysql-<时间>.sql` | 全库导出（含 `flyway_schema_history`，恢复后迁移状态一致） |
| `uploads-<时间>.tar.gz` | 用户上传的图片与文件 |

实现上用的是 `cmd /c` 重定向而不是 PowerShell 管道——PowerShell 5.1 没有 `-AsByteStream`，
而 `Out-File -Encoding utf8` 会给 SQL 加 BOM 并把换行改成 CRLF。cmd 的重定向是字节级的。

恢复：

```powershell
cmd /c "docker compose -f deploy\docker-compose.yml exec -T mysql mysql -uroot -p<pw> < backups\mysql-xxx.sql"
docker compose -f deploy\docker-compose.yml cp backups\uploads-xxx.tar.gz backend:/tmp/
docker compose -f deploy\docker-compose.yml exec -T backend tar xzf /tmp/uploads-xxx.tar.gz -C /app
```

---

### 查看数据库

库跑在 Docker 容器里，`mysql` 没有对宿主机暴露端口。三种方式任选：

| 方式 | 命令 | 说明 |
|---|---|---|
| 网页控制台 | `.\scripts\db.ps1` | 打开 Adminer（http://localhost:8082），点表就能看数据 |
| 快速查询 | `.\scripts\db.ps1 -Tables`<br>`.\scripts\db.ps1 -Sql "SELECT * FROM t_user LIMIT 5"` | 结果直接打印到终端 |
| 交互终端 | `.\scripts\db.ps1 -Shell` | 输入 `exit` 退出 |
| 连接信息 | `.\scripts\db.ps1 -Info` | 地址 / 账号 / 密码 |

Adminer 登录：服务器填 `mysql`，用户名 `root`，密码见 `deploy\.env` 的 `MYSQL_ROOT_PASSWORD`。
它只绑定 `127.0.0.1`，局域网里其他人连不上（里面有 root 账号）。

**忘记密码 / 密码不对：**

```powershell
.\scripts\db.ps1 -User waid -NewPassword 你的新密码
```

> 原理：数据库里存的是 BCrypt 加盐哈希，没有明文，也不能直接写明文。
> 脚本会先注册一个临时账号（由后端生成正确哈希），把它的 `password` 列复制给目标用户，
> 删掉临时账号，最后用新密码真实登录一次做验证。

---

## 十一、Docker 一键部署

```
deploy/
├── docker-compose.yml    # mysql + backend + frontend(nginx)
└── .env.example          # 复制成 .env 填密钥
backend/Dockerfile        # 多阶段构建，非 root 运行，带 healthcheck
frontend/Dockerfile       # 构建 dist → nginx 托管
frontend/nginx/default.conf  # SPA 回退 + /api /ws /uploads 反代
```

只有 `frontend` 对外暴露端口，`backend` 和 `mysql` 只在容器网络内可见。

```bash
cd deploy
cp .env.example .env        # 填写 MYSQL_ROOT_PASSWORD 和 IM_JWT_SECRET
docker compose up -d --build
# 打开 http://localhost:8080
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

### 几个设计点

- **前后端同源**：浏览器只访问 nginx，`/api`、`/ws` 由 nginx 转发，所以前端代码里的相对路径直接可用，不存在跨域，也不需要 CORS 配置
- **WebSocket 反代**：nginx 必须显式转发 `Upgrade`/`Connection` 头，并把 `proxy_read_timeout` 调到 3600s，否则长连接会被切断
- **表结构**：容器里 `ddl-auto=validate`，建表由 Flyway 完成，第一次启动自动建好
- **数据持久化**：`mysql-data` 和 `uploads` 两个命名卷，容器重建不丢数据
- **密钥**：全部走环境变量，`deploy/.env` 已在 `.gitignore` 里，仓库只留 `.env.example`

> **验证状态**：已在本机 Docker（Engine 29.9.2 / Linux / 20 核 16G）实测通过。
> 三个容器全部 `healthy`，`/actuator/health` 经 nginx 返回 `UP`，
> `scripts/e2e.mjs` 52 项 + `scripts/ai-check.mjs` 12 项全过；
> MySQL 8.4 内共 7 张表（6 张业务表 + `flyway_schema_history`），迁移版本 v1。

### 实测踩到的两个坑（已修，值得记住）

**1. nginx 的 upstream 名不能带下划线。**

最初写成 `upstream weliao_backend`，而 `/actuator/health` 那条 location 又漏了 `proxy_set_header Host`，
nginx 就把 upstream 名当 Host 头发给后端，Tomcat 直接拒绝：

```
java.lang.IllegalArgumentException: The character [_] is never valid in a domain name.
→ HTTP 400 Bad Request
```

修法：upstream 改名为 `weliao-backend`（连字符），并给每条 location 都补上 `proxy_set_header Host $host`。
**这类问题只在真实代理链路上才暴露，本地直连后端永远测不出来。**

**2. 容器内 Maven 直连 Central 会卡死。**

`dependency:go-offline` 跑十多分钟没进展。现在构建阶段用 `backend/docker/m2-settings.xml` 指向阿里云镜像；
海外环境删掉 Dockerfile 里那行 `COPY docker/m2-settings.xml` 即可恢复默认源。

### 国内网络前置：配镜像加速器

Docker Hub 直连不通，需要在 `%USERPROFILE%\.docker\daemon.json` 里加：

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.m.daocloud.io",
    "https://docker.1panel.live"
  ]
}
```

改完重启 Docker Desktop 生效。这是机器级配置、不属于项目，所以没有放进仓库。



---

## 九、AI 伙伴（IM × AI）

AI 不是一个外挂页面，而是**会话系统里的一个普通账号**（`t_user.type = BOT`）。
所以它天然拥有消息落库、历史分页、未读数、多端同步、断线补齐——这些一行特殊逻辑都不用写。

### 怎么用

注册完账号，会话列表里就会出现「大肥鱼」，点进去直接说话即可。

### 零配置可跑：本地模拟模式

不填任何密钥时，AI 回落到 `MockAiProvider`——规则生成回复，并**明确告诉用户当前是模拟模式**。
这是刻意的取舍：宁可诚实，也不要假装成真模型。演示时被问"这是真 AI 吗"可以直接指着代码解释。

### 接入真实模型

任何 **OpenAI 协议兼容**的平台都行（DeepSeek、通义、各类聚合 API 服务），只改三个环境变量：

```bash
export AI_BASE_URL=https://api.deepseek.com
export AI_API_KEY=sk-xxxx
export AI_MODEL=deepseek-chat
mvn spring-boot:run -Dspring-boot.run.profiles=xampp
```

`AI_BASE_URL` 写到域名或写到 `/v1` 都可以，代码会自动补全 `chat/completions` 路径。

### 换模型为什么不用改代码

`AiProvider` 是唯一抽象点：

```
AiProvider (接口)
├── MockAiProvider              未配置密钥时使用
└── OpenAiCompatibleProvider    任意 OpenAI 兼容平台
```

`AiConfig` 根据配置决定装配哪个实现，业务代码只依赖接口。

### 链路设计

```
用户发消息 → 落库 → 事务提交 → 发布 MessageCreatedEvent
                                      │
                                      ├─► WebSocket 推送全部成员（原有逻辑）
                                      └─► AiReplyService（独立线程池异步）
                                            ├─ 只回应用户的文本消息（不回应自己，防死循环）
                                            ├─ 取最近 N 条消息拼上下文
                                            ├─ 调用 AiProvider
                                            └─ 以 AI 账号身份 send() 一条回复
```

用应用事件而不是直接调用，是因为 `MessageService → AiService → MessageService` 会形成循环依赖；
拆开后，将来要加"敏感词审核""消息投递第三方"也只是再加一个监听器。

### 相关接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/conversation` | 打开（必要时创建）与 AI 的会话 |
| GET | `/api/ai/status` | 返回 `{botId, provider, mock}`，前端据此提示是否模拟模式 |

### 验证

```bash
node scripts/ai-check.mjs   # 12 项断言
```

覆盖：AI 账号初始化、注册即开通会话、欢迎语落库、异步回复、防自触发死循环、真人间对话不受影响。

---

## 十二、前端入口补全（改资料 / 群管理 / AI 标记）

之前后端接口写完了但前端没有入口，用户实际摸不到——这是「看起来没实用性」的最主要原因。
现在三处都接上了：

| 入口 | 位置 | 能力 |
|---|---|---|
| **编辑资料** | 左下角头像 → 个人信息卡 → 「编辑资料」 | 改昵称、签名、上传/移除头像 |
| **群成员管理** | 群聊标题栏右侧「群成员 N」 | 查看成员与角色、邀请好友入群、移出成员、自己退群 |
| **AI 模拟模式标记** | AI 会话标题栏 | 显示「模拟模式」或「AI 伙伴」，悬停可看当前模型链路 |

### 权限与边界

- 只有**群主/管理员**能邀请和移除他人；普通成员只能自己退群
- 群主不能被移出（后端校验），退出群主身份时会把群主转给最早加入的成员
- 被移除/退群后，该群会从对方会话列表消失
- 头像走 `/api/files/upload`，复用已有的上传白名单与 20MB 限制
- 头像传空字符串表示清除，后端落库为 `null`

### 一个契约细节

`UpdateProfilePayload.avatar` 是**可选字符串**，不是 `string | null`，所以清除头像要传空串 `''`。
前端已按此处理，后端 `UserService.updateProfile` 把空白视为清除。

### 验证

```bash
node scripts/ui-api-check.mjs   # 17 项断言
```

覆盖：改昵称/签名/头像（含空串清空）、群主邀请、移除成员、被邀请方可见性、
被移除方可见性、普通成员邀请/移除被拒 403、自己退群、AI 状态与 `type=BOT`。

---

## 十三、撤回、限流、桌面通知

### 消息撤回

```
POST /api/conversations/{id}/messages/{messageId}/recall
```

**不物理删除**：保留行、保留 `seq`，只把 `content` 清空并打 `recalled` 标记。

这个取舍解决了三个麻烦：

| 如果物理删除 | 现在的做法 |
|---|---|
| `seq` 出现空洞，游标分页要处理跳号 | `seq` 连续，分页逻辑不变 |
| 未读数要重算（对方到底算不算收到过） | 未读数不变——你确实收到过一条，只是内容不可见 |
| 无法审计、无法做「已撤回」占位 | 行还在，前端渲染占位 |

规则：只能撤回**自己的**消息、**2 分钟内**（`app.message.recall-window-minutes`）、系统消息不可撤回、重复撤回幂等。
撤回后 `fileName` / `fileSize` 也一并清空，否则「已撤回」旁边还挂着原始文件名。

前端悬停在自己的消息上会出现「撤回」按钮；撤回结果通过 `message_recalled` 帧同步给所有成员。

### 防刷限流

零依赖的**固定窗口计数器**（`common/RateLimiter`），不引 Redis、不引 Bucket4j。

| 目标 | 维度 | 默认配额 |
|---|---|---|
| 登录 / 注册 | 每 IP | 30 次/分钟 |
| 发送消息 | 每用户 | 60 条/分钟 |

超限返回 `HTTP 429` + `Retry-After` + 信封码 `42900`。WebSocket 发消息走同一配额，超限下发 `error` 帧。

**一个容易忽略的安全点**：`X-Forwarded-For` 默认**不信任**（`trust-forwarded-for: false`），
否则客户端伪造该头就能换 IP 绕过限流。只有前面确实有 nginx 时（`prod` profile）才打开。

### 桌面通知

`composables/notification.ts`，三条约束：

1. 权限必须由**用户手势**触发，所以做成信息卡里的「开启桌面通知」按钮，不自动弹
2. 只在 `document.hidden` 时提示——用户正盯着窗口时再弹是骚扰
3. 同一会话用固定 `tag` 去重，避免通知中心被刷屏

不支持或被拒绝时静默降级，不影响聊天。

### 验证

```bash
node scripts/ui-api-check.mjs   # 22 项断言（含 5 项撤回）
```

限流实测（容器环境，prod profile）：

```
总请求 34 → 第 31 次开始 429，被拦截 4 次，Retry-After: 56s，信封码 42900
```

### 迁移记录

撤回功能对应 **V2 迁移**，并且在**已有数据的库上**增量执行成功：

```
Migrating schema `weliao` to version "2 - message recall"
Successfully applied 1 migration ... now at version v2
```

这是 Flyway 从 V1 基线之后第一次真正被用到——`ALTER TABLE` 加列、保留历史数据、旧版本库自动升级。


