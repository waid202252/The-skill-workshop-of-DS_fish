# 小体量服务器部署

> 目标：一台便宜的小机器 + 一个 IP，面试官打开浏览器就能用。
> 下面按「买机器 → 装环境 → 传镜像 → 起服务 → 灌数据 → 验收」六步走。

---

## 第 0 步：选机器（10 分钟）

| 项 | 建议 | 说明 |
|---|---|---|
| 规格 | **2 核 4G** | 2 核 2G 也能跑，但要按 `docker-compose.small.yml` 末尾的注释压内存 |
| 系统 | Ubuntu 22.04 / 24.04 | 教程最多，Docker 最好装 |
| 磁盘 | 40G 起 | 镜像 + 数据卷；Docker 日志不轮转会吃满盘，配置文件里已经加了轮转 |
| 带宽 | 3–5M | 聊天够用；上传 20MB 图片会慢一点 |
| 备案 | **优先用 IP 直接访问** | 国内机器绑域名必须备案（1–2 周），只绑 IP 不需要 |

成本参考：国内云厂商「轻量应用服务器」2 核 4G 大约 ¥100–150/月，新用户/学生机更便宜。演示周期短就买 1 个月。

> 境外机器（香港/新加坡）免备案、随开随用，但国内访问延迟 100ms+，
> 长连接体验会差一点；如果需要给面试官看「实时性」，还是国内机器 + IP 更好。

**云控制台安全组/防火墙**：只放行 `22`、`80`、`443` 三个端口。
MySQL(3306)、Redis(6379)、后端(8080) 在这套编排里都不映射到宿主机，公网访问不到。

---

## 第 1 步：服务器初始化

```bash
# 登录
ssh root@<你的IP>

# 更新 + 基础工具
apt update && apt upgrade -y
apt install -y curl ufw

# 防火墙（云控制台也要放行，两边都要）
ufw allow 22 && ufw allow 80 && ufw allow 443 && ufw --force enable

# Docker（国内网络用阿里云镜像源，否则官方脚本会卡住）
curl -fsSL https://get.docker.com | sh -s -- --mirror Aliyun
systemctl enable --now docker
docker version
```

---

## 第 2 步：本地打包镜像（在你自己的电脑上做）

**不要在服务器上 `docker compose build`**：Maven 打包峰值要吃 1.5G 内存，2 核小机器很容易 OOM，而且每次改代码都要重下依赖。

```powershell
cd deploy

# 打两个镜像（名字必须和 docker-compose.small.yml 里的 image: 对得上）
docker build -t weliao-backend:local  ../backend
docker build -t weliao-frontend:local ../frontend

# 导出成 tar（已实测：后端 224MB，前端 21MB）
# 注意：不要用 `docker save ... | gzip`，Windows PowerShell 里没有 gzip 命令
docker save weliao-backend:local  -o ..\backups\backend-image.tar
docker save weliao-frontend:local -o ..\backups\frontend-image.tar
```

> 家宽上行 5Mbps 传 245MB 大约 6–7 分钟。如果嫌慢，可以改用镜像仓库（见文末「走镜像仓库」）。

---

## 第 3 步：上传并导入

```powershell
# 服务器上建目录
ssh root@<IP> "mkdir -p /opt/weliao"

# 传镜像 + 编排文件 + 配置
scp ..\backups\backend-image.tar   root@<IP>:/opt/weliao/
scp ..\backups\frontend-image.tar  root@<IP>:/opt/weliao/
scp .\docker-compose.small.yml     root@<IP>:/opt/weliao/docker-compose.yml
scp .\.env                         root@<IP>:/opt/weliao/.env
```

> ⚠️ `.env` 里是真实密钥，**只走 scp，不要提交到 git**（仓库 .gitignore 已排除）。
> 上服务器前建议换掉 `MYSQL_ROOT_PASSWORD` 和 `IM_JWT_SECRET`（`openssl rand -base64 48` 生成）。

```bash
# 服务器上导入镜像
cd /opt/weliao
docker load -i backend-image.tar
docker load -i frontend-image.tar
rm -f *.tar            # 导入完就删，省 245MB 磁盘

# 确认端口是 80
sed -i 's/^WEB_PORT=.*/WEB_PORT=80/' .env
grep -E 'WEB_PORT|MYSQL_DB' .env
```

---

## 第 4 步：起服务

```bash
cd /opt/weliao
docker compose up -d
docker compose ps          # 期望：mysql/redis/backend/frontend 全 running，backend healthy
docker compose logs -f backend   # 看到 "Started ImApplication" 就可以 Ctrl+C
```

首次启动要等 Flyway 建表 + 后端启动，**大约 40–60 秒**。

---

## 第 5 步：注册账号并验证

数据库是空的，服务起来后直接注册即可：

1. 浏览器打开 `http://<你的IP>`，注册两个账号（用户名 4-20 位字母数字下划线，密码 6-32 位）
2. 用其中一个账号搜索另一个的用户名、互加好友、发几条消息
3. 注册时会自动创建一个「大肥鱼」AI 会话，发一句话确认 AI 链路通

想让库里有像样的演示数据（多账号、好友关系、群聊、可用的技能缓存），可以写一个脚本
调这些 REST 接口批量造：`/api/auth/register` → `/api/friends/requests` →
`/api/conversations/single` → `/api/conversations/{id}/messages`。
全部走接口而不直接写 SQL，`seq`、好友关系、未读数才会由业务逻辑正确算出。

---

## 第 6 步：验收清单

```bash
curl -s http://<IP>/api/system/info | head -c 400
```

| 检查 | 期望 |
|---|---|
| `cluster.mode` | `multi-node(redis)` |
| `rateLimit.store` | `redis` |
| 浏览器打开 `http://<IP>` | 登录页正常，能登 `fish1 / fish1234` |
| 会话列表 | 有陈一鸣、周雨桐、AI 技能交流群、大肥鱼 |
| 大肥鱼 | 显示**在线**，发一句话能回 |
| 两个浏览器互发 | 实时到达（跨公网，1 秒内） |
| 连点登录 35 次 | 第 31 次 429 |
| `docker stats` | backend 内存 500–700MB，mysql 300–500MB，都在 limit 内 |

---

## 日常运维

```bash
cd /opt/weliao

docker compose ps                    # 状态
docker compose logs --tail=100 backend
docker stats --no-stream             # 看内存有没有顶到限制
docker exec -it weliao-redis-1 redis-cli HGETALL im:presence:2   # 在线登记表
docker system prune -f               # 定期清无用镜像/缓存（会占盘）

# 备份数据库
docker exec weliao-mysql-1 sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" weliao' | gzip > /root/weliao-$(date +%F).sql.gz
```

**升级版本**：本地改完代码 → 重新 build + save → scp → `docker load` → `docker compose up -d --force-recreate backend`（镜像 tag 没变时 compose 不会自己重建）。

---

## 常见坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `backend` 反复重启，日志 `Killed` | 内存超限被 OOM Killer 杀 | 确认用的是 `docker-compose.small.yml`（堆写死 512M）；仍不行就降到 `-Xmx320m` |
| 打开 IP 一直转圈 | 云安全组没放行 80 | 控制台和 `ufw` 两边都放行 |
| 登录后立刻掉线 | 磁盘满，MySQL 写不进去 | `df -h`，`docker system prune -f` |
| WebSocket 连不上 | 前面有代理没转发 `Upgrade` | 这套编排里 nginx 已配好；若自己再加一层代理要记得转发 |
| 消息偶尔延迟很久 | 机器在用 swap | `free -h`；关 swap 或加内存，别硬扛 |
| 换 IP 后前端行为诡异 | 前端按 origin 存 token | 换 IP/域名等于换 origin，重新登录即可 |

---

## 附：走镜像仓库（反复升级时更省事）

传 tar 的缺点是每次改代码都要传 224MB。如果你会反复升级，用镜像仓库只推**变化的层**：

```powershell
# 1. 阿里云 → 容器镜像服务 ACR → 个人版（免费）→ 建命名空间 weliao，仓库 backend / frontend
docker login --username=<阿里云账号> registry.cn-hangzhou.aliyuncs.com

# 2. 打 tag（版本号自己递增，别用 latest，否则服务器分不清拉的是哪版）
docker tag weliao-backend:local  registry.cn-hangzhou.aliyuncs.com/weliao/backend:1.1
docker push                      registry.cn-hangzhou.aliyuncs.com/weliao/backend:1.1
```

```bash
# 3. 服务器拉取，把 compose 里的 image: 换成仓库地址
docker login registry.cn-hangzhou.aliyuncs.com
docker pull registry.cn-hangzhou.aliyuncs.com/weliao/backend:1.1
docker compose up -d --force-recreate backend
```

---

## 一个可选项：给面试官一个 HTTPS 地址

如果你有域名（且已备案），用 Caddy 挡在最前面自动申请证书，5 分钟搞定：

```
# 把 frontend 的 ports 改成 127.0.0.1:8080:80，然后：
docker run -d --name caddy --restart unless-stopped \
  -p 80:80 -p 443:443 \
  -v caddy-data:/data \
  caddyserver/caddy:2 \
  caddy reverse-proxy --from chat.你的域名.com --to 127.0.0.1:8080
```

只绑 IP 的话不需要折腾证书：浏览器对 IP 的 HTTPS 证书本来就不被信任，直接 HTTP 更省事。
