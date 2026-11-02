# ============================================================================
# 前端镜像：构建出静态产物，交给 nginx 托管并反代后端
# 生产环境前端与后端同源（都经这个 nginx），所以代码里的相对路径 /api 直接可用，
# 不需要额外配置 API 域名。
# ============================================================================

# ---------- 构建 ----------
FROM node:22-alpine AS build
WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

# ---------- 运行 ----------
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
