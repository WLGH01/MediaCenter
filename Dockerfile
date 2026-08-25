# ============================================================
# Stage 1: Server Builder — 编译服务器 TypeScript 代码
# ============================================================
FROM node:24-alpine AS server-builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run server:build

# ============================================================
# Stage 2: Client Builder — 构建 React 前端（Vite）
# ============================================================
FROM node:24-alpine AS client-builder

WORKDIR /app/client

COPY client/package.json client/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY client/ ./
RUN npm run build

# ============================================================
# Stage 3: Production Runtime (All-in-One: Node + PostgreSQL)
# ============================================================
FROM node:24-alpine AS runner

WORKDIR /app

# 安装运行时依赖：
# - ffmpeg (提取媒体元数据)
# - postgresql (本地数据库引擎)
# - postgresql-contrib (提供 pg_trgm 扩展)
# - postgresql-pgvector (提供 vector 扩展)
# - su-exec (轻量级 gosu 替代品，用于在 entrypoint 中安全降权运行 postgres 和 mediacenter)
RUN apk add --no-cache ffmpeg postgresql postgresql-contrib postgresql-pgvector su-exec \
    && addgroup -S -g 10001 mediacenter \
    && adduser -S -D -H -u 10001 -G mediacenter mediacenter

# 安装生产依赖
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --omit=dev && npm cache clean --force

# 复制编译产物
COPY --from=server-builder /app/dist/index.js ./index.js
COPY --from=client-builder /app/dist/public ./public

# 复制启动脚本并赋予执行权限
COPY entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

# 暴露端口 (3000: 媒体中心 Web, 5432: 本地内置 PG 可选暴露)
EXPOSE 3000 5432

# PostgreSQL 数据默认卷路径
ENV PGDATA=/var/lib/postgresql/data
VOLUME /var/lib/postgresql/data
VOLUME /app/uploads

ENTRYPOINT ["/app/entrypoint.sh"]
