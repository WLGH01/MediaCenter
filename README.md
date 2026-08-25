# MediaCenter（自用修改版）

> 基于 [dawn-lc/MediaCenter](https://github.com/dawn-lc/MediaCenter) 的**自用修改版**。
>
> 本仓库用于个人学习、家庭服务器和私有部署场景，保留原项目的核心媒体中心能力，并在此基础上进行界面美化、安全配置、Docker 部署和文档整理。
>
> **Node.js 24 · Express 5 · React · PostgreSQL 16 · Drizzle ORM · ffmpeg · PWA**

[![Node.js](https://img.shields.io/badge/Node.js-24%2B-339933?logo=node.js&logoColor=white)](https://nodejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](docker-compose.yml)
[![License](https://img.shields.io/badge/license-PolyForm%20Noncommercial-orange)](LICENSE)

## 📌 项目说明

- **上游项目**：[dawn-lc/MediaCenter](https://github.com/dawn-lc/MediaCenter)
- **当前项目**：[WLGH01/MediaCenter](https://github.com/WLGH01/MediaCenter)
- **项目性质**：基于上游项目的个人自用修改版，非官方发行版
- **修改重点**：前端视觉重构、安全配置收紧、Docker Compose 部署、API 文档和 README 整理
- **使用范围**：个人、学习、家庭服务器及私有部署

本仓库不代表上游项目的官方立场。使用、分发和商业用途请同时遵守上游项目及本仓库所附许可证要求。

## ✨ 特性

### 媒体体验

- 视频、音频、图片在线播放与下载
- HTTP Range 分段传输，支持拖拽播放和断点请求
- 短时签名 URL，适配 `<video>` / `<audio>` 原生播放器
- ffprobe 自动提取时长、分辨率、编码等元数据
- 服务端缩略图 + 浏览器端生成兜底
- 响应式布局，适配桌面、平板和移动端

### 组织与检索

- 扫描服务器指定目录，自动导入媒体文件
- 标签、作者和媒体可见性管理
- PostgreSQL `pg_trgm` 全文模糊检索
- OpenAI 兼容 Embeddings 语义检索
- 标签表达式、作者筛选和 RRF 混合排序
- SSE 实时推送媒体库变化

### 安全与管理

- 访客 / 用户 / 管理员 / 仅自己四级可见性
- JWT 登录认证与静态 API Token
- Helmet 安全响应头、CSP、限流和请求体大小限制
- 默认同源 CORS，跨域需显式白名单
- 管理面板支持用户、标签、作者、扫描和数据库维护
- Docker 非 root、只读根文件系统、丢弃 capabilities

### PWA 与主题

- Service Worker 离线缓存
- 缩略图 Cache First 策略
- 深色 / 浅色 / 跟随系统
- 主题偏好保存在浏览器本地

## 🖼️ 页面预览

首页提供媒体统计、最近上传和快捷入口；媒体库支持搜索、筛选、排序和分页；播放器页面提供媒体详情、播放控制和下载操作。

## 🚀 快速开始

### 环境要求

- Node.js >= 24
- PostgreSQL >= 16，并启用 `pg_trgm`
- ffmpeg（Docker 镜像已内置；本地运行需自行安装）

### 本地运行

```bash
npm install
cd client && npm install && cd ..
cp .env.example .env
# 编辑 .env，填写数据库和管理员配置
npm run build
npm start
```

打开 <http://localhost:3000>。

### 开发模式

```bash
npm run dev
```

后端使用 `tsx watch`，前端使用 Vite HMR。

## 🐳 Docker 部署（推荐）

### 方式一：All-in-One 镜像（内置 PostgreSQL，无需额外数据库）

本项目已打包为 **All-in-One 镜像**（内置 PostgreSQL + pgvector + pg_trgm + ffmpeg），一条命令即可启动：

```bash
docker run -d --name mediacenter \
  -e ADMIN_USERNAME=admin \
  -e ADMIN_PASSWORD='替换为强密码' \
  -e POSTGRES_PASSWORD='替换为数据库密码' \
  -e JWT_SECRET='openssl rand -hex 32' \
  -p 3000:3000 \
  -v /your/media/library:/media \
  -v /your/uploads:/app/uploads \
  -v /your/postgres-data:/var/lib/postgresql/data \
  ghcr.io/wlgh01/mediacenter:latest
```

- `/media`：本地媒体库目录（扫描导入用，可选）
- `/app/uploads`：网页上传的文件目录
- `/var/lib/postgresql/data`：内置 PostgreSQL 数据持久化

### 方式二：Compose（应用 + 独立 PostgreSQL）

```bash
cp .env.example .env
# 生产环境必须修改所有 replace/change-me 占位值
docker compose up -d --build
```

默认服务：

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| MediaCenter | `http://localhost:3000` | Web 与 API |
| PostgreSQL | Compose 内部 | 不建议直接暴露到公网 |

Compose 会自动完成 PostgreSQL 健康检查，并持久化数据库数据。媒体目录由 `MEDIA_DIR` 映射到容器 `/app/uploads`。

### ⚡ 自动构建（GitHub Actions）

仓库已配置 GitHub Actions 工作流（[`.github/workflows/docker-build.yml`](.github/workflows/docker-build.yml)）：

- **每次 push 到 `main` 分支**，自动构建 推送到 GitHub Container Registry
- 也可在仓库 **Actions** 页面手动触发（`workflow_dispatch`）
- **无需再手动构建镜像**；部署端拉取新镜像重启即可

```bash
git add .
git commit -m "update"
git push
```

### 🖥️ Unraid 部署

1. 将 [`mediacenter.xml`](mediacenter.xml) 放到 Unraid 的 `/boot/config/plugins/dockerMan/templates-user/` 目录
2. Unraid 的 Docker 页面 → **Add Container** → 选择 **MediaCenter** 模板
3. 按需配置端口、路径映射和环境变量，应用即可

### 生产部署建议

1. 使用反向代理提供 HTTPS。
2. 修改 `JWT_SECRET`、`ADMIN_PASSWORD`、`POSTGRES_PASSWORD`。
3. `API_TOKEN` 只提供给可信服务端，不要写入前端代码。
4. 默认保持同源部署；确需跨域时配置 `CORS_ORIGIN` 白名单。
5. `UPLOAD_DIR` / `MEDIA_DIR` 只挂载专用媒体目录。
6. 定期备份 PostgreSQL 的 `pgdata` 卷。

## ⚙️ 环境变量

### 必填

| 变量 | 说明 |
| --- | --- |
| `JWT_SECRET` | JWT 签名密钥，建议使用 `openssl rand -hex 32` 生成 |
| `DATABASE_URL` | PostgreSQL 连接字符串 |
| `ADMIN_USERNAME` | 初始管理员用户名 |
| `ADMIN_PASSWORD` | 初始管理员密码 |
| `UPLOAD_DIR` | 应用内媒体目录 |

### 常用可选项

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `3000` | HTTP 监听端口 |
| `API_TOKEN` | 未启用 | 静态服务端 API Token |
| `CORS_ORIGIN` | 同源 | 逗号分隔的跨域白名单 |
| `TRUST_PROXY` | `false` | 是否信任前置反向代理 |
| `DB_POOL_SIZE` | `16` | 数据库连接池上限 |
| `MAX_FILE_SIZE` | `34359738368` | 单文件大小上限，默认 32 GB |
| `SERVER_THUMBNAILS` | `false` | 是否启用服务端缩略图 |
| `ALLOW_REGISTRATION` | `false` | 是否开放自助注册 |
| `EMBEDDING_BASE_URL` | 未启用 | OpenAI 兼容 Embeddings 地址 |
| `EMBEDDING_MODEL` | `qwen3-embedding:0.6b` | 嵌入模型名称 |
| `EMBEDDING_DIM` | `1024` | 向量维度 |
| `RRF_K` | `60` | 混合检索 RRF 常数 |

## 📚 API 文档

完整接口文档见 [`docs/API.md`](docs/API.md)，包含：

- JWT / 静态 Token 认证
- 登录、注册和用户资料
- 媒体列表、上传、编辑和删除
- 流媒体、Range、下载与缩略图
- 标签、作者和管理员接口
- 目录扫描与数据库维护
- SSE 实时事件
- curl 请求示例和错误码说明

API 基础路径：`/api`。

## 🧰 命令

| 命令 | 说明 |
| --- | --- |
| `npm start` | 构建并启动生产服务 |
| `npm run dev` | 同时启动后端和前端开发服务 |
| `npm run build` | 构建后端、前端和 PWA |
| `npm run server:build` | 仅构建后端 |
| `npm run client:build` | 仅构建前端 |
| `npm run server:dev` | 仅启动后端开发服务 |
| `npm run client:dev` | 仅启动前端 Vite |

## 🏗️ 项目结构

```text
src/                 Express API、认证、数据库、扫描、流媒体
client/src/          React 页面、组件、状态管理与查询
client/public/       PWA 图标与离线页面
docs/API.md           API 文档
Dockerfile            多阶段生产镜像（内置 PostgreSQL + ffmpeg）
docker-compose.yml    应用 + PostgreSQL 编排
entrypoint.sh         容器启动脚本（初始化并启动内置 PostgreSQL）
mediacenter.xml       Unraid Docker 模板
.github/workflows/    GitHub Actions 自动构建
```

## 🔐 许可证

本项目使用 [PolyForm Noncommercial 1.0.0](LICENSE)。个人、教育和非商业使用免费；商业使用请先获得授权。
