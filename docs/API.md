# MediaCenter API 文档（详细版）

> 本文档基于当前代码（`src/routes/*` + `src/controllers/*`）逐接口整理，覆盖全部已注册路由。

## 目录

- [基础信息](#基础信息)
- [认证与权限](#认证与权限)
- [通用约定](#通用约定)
- [认证接口](#认证接口-auth)
- [媒体接口](#媒体接口-media)
- [流媒体接口](#流媒体接口-stream)
- [标签接口](#标签接口-tags)
- [作者接口](#作者接口-authors)
- [用户接口](#用户接口-users)
- [管理接口](#管理接口-admin)
- [实时事件](#实时事件-sse)
- [错误码汇总](#错误码汇总)
- [curl 速查](#curl-速查)

---

## 基础信息

| 项目 | 值 |
|---|---|
| 基础地址 | `http://<host>:<port>/api` |
| 默认端口 | `3000`（Unraid 示例：`http://192.168.3.20:3023/api`） |
| 数据格式 | JSON（上传接口用 `multipart/form-data`） |
| 流媒体 | HTTP Range 分段传输，支持拖拽播放 |

---

## 认证与权限

### 认证方式（三种）

| 方式 | Header / 参数 | 说明 |
|---|---|---|
| **JWT 登录令牌** | `Authorization: Bearer <token>` | `POST /auth/login` 获取；有效期 **7 天**；过期后 30 天内可 `POST /auth/refresh` 续期 |
| **静态 API 令牌** | `Authorization: Bearer <token>` | 环境变量 `API_TOKEN`，或**管理面板生成**（存数据库）；永不过期，角色为 `admin` |
| **签名 URL** | URL query：`?expires=&uid=&purpose=&role=&sig=` | 流媒体/下载专用；`<video>` 标签无法带 Header 时使用 |

> **无令牌请求视为 `guest`**（匿名访客），仅能访问 `minRole='guest'` 的媒体。

### 角色与可见性（`minRole`）

| 角色 | 能看到的媒体 |
|---|---|
| `guest`（匿名） | 仅 `guest` 级（公开） |
| `user`（登录用户） | `guest` + `user` 级 + 自己上传的 `owner` 级 |
| `admin` | 全部（含软删除记录） |
| `owner` | 仅上传者本人 + 管理员 |

### Token 有效期

| Token | 有效期 | 获取/续期 |
|---|---|---|
| Access Token | 7 天 | `POST /auth/login`、`POST /auth/refresh` |
| Refresh Token | 30 天 | `POST /auth/login`；登出/改密后全部失效 |

---

## 通用约定

### 成功响应
- `2xx`：JSON 对象或数组（见各接口）

### 错误响应格式
```json
{ "error": "media.notFound" }
```

### 状态码

| 码 | 含义 |
|---|---|
| `400` | 参数缺失/格式错误 |
| `401` | 未登录 / 令牌无效或过期 |
| `403` | 无权限（角色不足 / 非本人） |
| `404` | 资源不存在 |
| `409` | 冲突（用户名重复 / 文件哈希重复） |
| `415` | 不支持的媒体类型 |
| `429` | 触发限流 |
| `500` | 服务端错误 |

---

## 认证接口（/auth）

### `POST /auth/login` — 登录
**公开**

请求：
```json
{ "username": "admin", "password": "***" }
```

成功响应 `200`：
```json
{
  "message": "auth.loginSuccess",
  "user": { "id": "uuid", "username": "admin", "role": "admin" },
  "token": "eyJhbGciOi...",
  "refreshToken": "uuid-uuid"
}
```

错误：`400` 空凭据 · `401` 用户名或密码错误 · `403` 账号被封禁

### `POST /auth/register` — 注册
**公开**（仅当 `ALLOW_REGISTRATION=true` 时可用，默认关闭返回 403）

请求：
```json
{ "username": "alice", "password": "abc12345" }
```
约束：用户名 3~32 字符；密码 ≥8 位且必须同时含字母和数字。

响应 `201`：同登录结构（`role: "user"`）。

### `POST /auth/refresh` — 刷新令牌
**公开**

请求：
```json
{ "refreshToken": "uuid-uuid" }
```

响应 `200`：
```json
{ "token": "新JWT", "refreshToken": "新refreshToken" }
```

错误：`401` 令牌无效/过期 · `403` 用户被封禁

### `POST /auth/logout` — 登出
**需登录**。撤销该用户全部 refresh token（服务端会话失效）。

响应：`{ "message": "auth.logoutSuccess" }`

### `GET /auth/profile` — 当前用户信息
**需登录**

响应：
```json
{ "user": { "id": "uuid", "username": "admin", "role": "admin", "createdAt": "..." } }
```

### `POST /auth/change-password` — 修改密码
**需登录**

请求：
```json
{ "oldPassword": "旧密码", "newPassword": "新密码" }
```
改密后自动撤销该用户全部 refresh token（其他设备下线）。

---

## 媒体接口（/media）

### `GET /media` — 媒体列表（分页/搜索/筛选）
**任意身份**（可见性自动过滤）

Query 参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` | int | 页码，默认 1 |
| `limit` | int | 每页数量，默认 20，最大 100；**`limit=0` = 不分页返回全部** |
| `search` | string | 标题/描述模糊搜索 |
| `type` | string | 按类型过滤：`video` / `audio` / `image`（匹配 mimeType 前缀） |
| `tags` | string | 标签表达式，如 `tags=A&(B|C)`、`!D` 排除 |
| `authorExpr` | string | 作者表达式，同上 |
| `authorId` | uuid | 指定作者 |
| `uploaderId` | uuid | 指定上传者 |
| `fileHash` | string | 按文件哈希精确查（**仅管理员**，否则 403） |
| `filePath` | string | 路径模糊查（**仅管理员**，否则 403） |
| `fileName` | string | 文件名模糊查（**仅管理员**，否则 403） |
| `sortBy` | string | `createdAt`(默认) / `title` / `fileSize` / `mimeType` / `relevance`(需 search) |
| `sortOrder` | string | `asc` / `desc`（默认 desc） |

响应 `200`：
```json
{
  "items": [
    {
      "id": "uuid",
      "title": "标题",
      "fileSize": 123456,
      "mimeType": "video/mp4",
      "duration": 33.4,
      "fileHash": "sha256...",
      "deletedAt": null,
      "createdAt": "...",
      "uploaderId": "uuid",
      "uploaderName": "admin",
      "authorId": "uuid",
      "authorName": "作者",
      "streamUrl": "/api/stream/uuid?expires=...&sig=...",
      "thumbUrl": "/api/stream/uuid/thumb?..." ,
      "tags": [{ "id": "uuid", "name": "标签" }]
    }
  ],
  "pagination": { "page": 1, "limit": 20, "total": 100, "totalPages": 5, "sortBy": "createdAt", "sortOrder": "desc" }
}
```

### `GET /media/stats` — 概览统计
**任意身份**（统计当前用户可见范围）

响应：
```json
{
  "media": { "total": 100, "video": 60, "audio": 20, "image": 20, "totalSize": 123456789 },
  "tags": 30, "authors": 10, "users": 5,
  "recent": [ /* 最近上传 8 条，结构同列表项 */ ]
}
```
`totalSize`、`tags`、`authors`、`users` 仅管理员返回（其余为 `null`）。

### `GET /media/:id` — 媒体详情
**按可见性过滤**

响应：
```json
{
  "media": {
    "id": "uuid", "title": "...", "description": "...",
    "fileName": "xxx.mp4", "fileSize": 123, "fileHash": "...",
    "mimeType": "video/mp4", "minRole": "owner", "duration": 33.4,
    "mediaInfo": "{...ffprobe JSON...}", "sourceMeta": null,
    "uploaderId": "uuid", "deletedAt": null, "createdAt": "...", "updatedAt": "...",
    "streamUrl": "/api/stream/uuid?...", "downloadUrl": "/api/stream/uuid/download?...", "thumbUrl": "...",
    "tags": [{"id":"uuid","name":"标签"}],
    "author": { "id": "uuid", "name": "作者", "altNames": [], "urls": [] } 
  }
}
```
> 非管理员不返回 `filePath` / `fileName` / `mediaInfo` / `fileHash`。

### `POST /media/upload` — 上传文件
**需登录**（上传者或管理员）

`multipart/form-data`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | file | **必填**，媒体文件 |
| `title` | string | 可选，覆盖标题 |
| `description` | string | 可选 |
| `minRole` | string | 可选，`guest`/`user`/`owner`（管理员可设 `admin`） |
| `tags` | string[] | 可选 |
| `authorId` | uuid | 可选 |

响应 `201`：`{ "message": "media.uploadSuccess", "id": "uuid" }`

### `POST /media` — 导入媒体（API 推送 / 本地路径）
**仅管理员**

> 用于把服务器上**已存在**的文件登记进媒体库，不传输文件内容。

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `filePath` | string | ✅ | 容器内本地路径，如 `/media/xxx.mp4` |
| `mimeType` | string | ❌ | 媒体类型；不传则按扩展名识别 |
| `fileHash` | string | ❌ | 哈希，用于查重（重复返回 409） |
| `fileSize` | number | ❌ | 字节数，默认 0 |
| `sourceMeta` | string | ❌ | 来源元数据（JSON 字符串） |

请求示例：
```json
{
  "filePath": "/media/407876-1080p.mp4",
  "mimeType": "video/mp4",
  "fileSize": 87870000,
  "sourceMeta": "{\"source\":\"hanime1\",\"hanimeId\":\"407876\"}"
}
```

响应 `201`：`{ "message": "media.importSuccess", "id": "uuid" }`

> ⚠️ **重要**：`filePath` 必须是**服务器本地存在的文件路径**。如果传 `https://...` 远程 URL，服务端不会自动下载，播放/下载会因文件不存在返回 404。

### `PUT /media/:id` — 更新媒体元数据
**需登录**（上传者本人可改通用字段；管理员可改全部字段）

请求体（按需传字段）：
```json
{
  "title": "新标题",
  "description": "新描述",
  "minRole": "user",
  "tags": ["标签1", "标签2"],
  "author": "作者名"
}
```

管理员专属字段：`fileName`、`mimeType`、`uploaderId`、`filePath`（须文件存在）、`fileHash`、`thumbPath`、`duration`、`mediaInfo`、`sourceMeta`、`source`、`createdAt`、`updatedAt`、`altNames`（作者别名合并）。

响应：`{ "message": "media.updateSuccess", "media": { ...完整详情... } }`

### `DELETE /media/:id` — 删除媒体
**需登录**（上传者或管理员）

- **管理员**：硬删除 —— 删除磁盘文件 + 数据库记录
- **普通用户**：软删除 —— 仅标记 `deletedAt`，文件保留

响应：`{ "message": "media.deleteSuccess" }`

### `PUT /media/:id/restore` — 恢复软删除媒体
**仅管理员**

响应：`{ "message": "media.restoreSuccess" }`

### `GET /media/:id/stream-token` — 刷新签名令牌
**按可见性过滤**。为长视频/长时间播放刷新签名 URL（避免过期）。

响应 `200`：
```json
{ "streamUrl": "/api/stream/uuid?...", "downloadUrl": "/api/stream/uuid/download?..." }
```

---

## 流媒体接口（/stream）

### `GET /stream/:id` — 流式播放
**签名 URL 或 Bearer 令牌**均可。

- 支持 `Range: bytes=0-1048575` → `206 Partial Content` + `Content-Range`
- 响应头：`Accept-Ranges: bytes`、`Content-Type`（归一化）、`Cache-Control: public, max-age=31536000, immutable`
- 未找到本地文件 → `404 media.fileNotFound`

### `GET /stream/:id/download` — 下载原文件
**签名 URL 或 Bearer 令牌**。

- 响应头：`Content-Disposition: attachment; filename="..."`（URL 编码）

### `GET /stream/:id/thumb` — 服务端缩略图
**签名 URL 或 Bearer 令牌**。

- 返回已生成的缩略图（`thumbPath`）；未生成 → `404`，前端生成兜底
- 仅当 `SERVER_THUMBNAILS=true` 时服务端才会主动生成

---

## 标签接口（/tags）

### `GET /tags` — 标签列表
**任意登录用户**（`authenticate`，访客也可）

Query：`page`、`limit`(默认20,最大100)、`search`（名称/别名模糊）、`sortBy`（`name`/`mediaCount`/`createdAt`）、`sortOrder`

响应：
```json
{
  "tags": [{ "id": "uuid", "name": "标签", "altNames": [], "createdAt": "...", "mediaCount": 12 }],
  "pagination": { "page": 1, "limit": 20, "total": 50, "totalPages": 3 }
}
```

### `POST /tags` — 创建标签
**仅管理员**

```json
{ "name": "新标签" }
```
已存在时直接返回现有标签（200），新建返回 201。

### `PUT /tags/:id` — 更新标签（别名）
**仅管理员**

```json
{ "altNames": ["别名1", "别名2"] }
```

### `DELETE /tags/:id` — 删除标签
**仅管理员**。自动清除所有媒体关联。

---

## 作者接口（/authors）

### `GET /authors` — 作者列表
**任意登录用户**

Query：`page`、`limit`、`search`（名称/别名/链接）、`sortBy`（`name`/`mediaCount`）、`sortOrder`

响应：
```json
{
  "authors": [{ "id": "uuid", "name": "作者", "altNames": [], "urls": [], "mediaCount": 5 }],
  "pagination": { "page": 1, "limit": 20, "total": 10, "totalPages": 1 }
}
```

### `GET /authors/:id` — 作者主页
**任意身份**。返回作者信息 + 按当前用户可见范围统计的媒体数。

响应：
```json
{
  "author": { "id": "uuid", "name": "作者", "altNames": [], "urls": [], "mediaCount": 5 },
  "stats": { "total": 5, "video": 4, "audio": 1, "image": 0 }
}
```

### `POST /authors` — 创建作者
**仅管理员**

```json
{ "name": "作者名", "altNames": ["别名"], "urls": ["https://..."] }
```
名称已存在 → `409`。

### `PUT /authors/:id` — 更新作者
**仅管理员**：`name`、`altNames`、`urls` 均可选。

### `DELETE /authors/:id` — 删除作者
**仅管理员**。删除后关联媒体的 `authorId` 置空（`ON DELETE SET NULL`）。

---

## 用户接口（/users）

### `GET /users/:id` — 公开用户主页
**任意身份**。用户信息/统计公开；媒体列表按访问者可见性过滤。

---

## 管理接口（/admin）

> 全部接口**仅管理员**（`authenticate` + `requireAuth` + `requireAdmin`）。

### 用户管理

#### `GET /admin/users` — 用户列表
Query：`page`、`limit`(默认20,最大100)、`search`（用户名模糊）、`sortBy`（`username`/`role`/`createdAt`）、`sortOrder`

响应：
```json
{
  "users": [
    { "id": "uuid", "username": "admin", "role": "admin", "banned": 0, "createdAt": "...", "updatedAt": "...", "isSystemUser": false }
  ],
  "pagination": { "page": 1, "limit": 20, "total": 3, "totalPages": 1 }
}
```

#### `POST /admin/users` — 创建用户
```json
{ "username": "newuser", "password": "abc12345", "role": "user" }
```
角色：`guest` / `user` / `admin`（默认 `user`）。

#### `PUT /admin/users/:id/role` — 修改角色
```json
{ "role": "admin" }
```

#### `DELETE /admin/users/:id` — 删除用户
该用户上传的媒体转移到 API 服务账户并设为仅管理员可见。不可删除自己/系统账户。

#### `POST /admin/users/:id/toggle-ban` — 封禁/解封
响应：`{ "message": "admin.userBanned|admin.userUnbanned", "banned": true|false }`

### 媒体维护

#### `POST /admin/scan` — 扫描目录导入
```json
{ "path": "/media" }
```
递归扫描目录内所有支持的媒体文件（`.mp4 .mkv .mp3 .jpg` 等），去重导入。

响应：
```json
{
  "message": "admin.scanComplete",
  "scan": { "total": 4436, "imported": 100, "skipped": 4336, "errors": 0, "files": ["..."], "errorDetails": [] }
}
```

#### `POST /admin/batch-delete-media` — 批量删除（硬删除）
```json
{ "ids": ["uuid1", "uuid2"] }
```
或按路径前缀：
```json
{ "pathPrefix": "/media/bilibili" }
```
管理员硬删除：物理删除文件 + 数据库记录。响应：`{ "message": "admin.batchDelete.success", "count": N }`

#### `GET /admin/media-by-path?prefix=/media` — 按路径前缀搜索
返回供批量删除勾选：
```json
{ "items": [{ "id": "uuid", "title": "标题", "filePath": "/media/xxx.mp4" }] }
```

### API 令牌管理（管理面板生成静态令牌）

#### `GET /admin/api-tokens` — 令牌列表
```json
{ "tokens": [{ "id": "uuid", "name": "n8n推送", "description": "", "role": "admin", "createdAt": "...", "lastUsedAt": null }] }
```

#### `POST /admin/api-tokens` — 生成令牌
```json
{ "name": "n8n推送", "description": "可选备注" }
```
响应 `201`（**token 仅此一次返回明文**）：
```json
{ "message": "admin.apiToken.created", "token": "mc_xxxxx", "hint": "admin.apiToken.copyNow" }
```

#### `DELETE /admin/api-tokens/:id` — 删除令牌（立即失效）
响应：`{ "message": "admin.apiToken.deleted" }`

> 生成的令牌用法：`Authorization: Bearer mc_xxxxx`，角色 `admin`，永不过期。

### 危险操作

#### `POST /admin/reset-db` — 重置数据库
清空所有数据（媒体、标签、用户等）→ 重建表结构 → 重新创建默认管理员。**不可逆，执行前请备份。**

---

## 实时事件（SSE）

### `GET /events` — SSE 推送
- 浏览器 `EventSource('/api/events')`；也可带短时 token：`/api/events?token=<jwt>`（EventSource 无法带 Header）
- 每 15 秒发送心跳注释帧（`: ping`）
- 事件类型：`media.updated`（created/updated/deleted/restored/deleted_all）
- 推送按连接用户过滤：仅推给**能看到该媒体**且**非触发者本人**的用户
- 并发上限 200 连接，超出返回 429

示例（浏览器）：
```js
const es = new EventSource('/api/events?token=' + jwtToken);
es.addEventListener('media.updated', (e) => {
  const data = JSON.parse(e.data);
  console.log(data); // { type: 'created', mediaId: 'uuid', visibility: {...} }
});
```

---

## 错误码汇总

| 错误码 | 含义 |
|---|---|
| `auth.loginRequired` | 需要登录 |
| `auth.tokenInvalid` | 令牌无效/过期 |
| `auth.invalidCredentials` | 用户名或密码错误 |
| `auth.banned` | 账号被封禁 |
| `auth.refreshTokenInvalid` | 刷新令牌无效 |
| `media.notFound` | 媒体不存在 |
| `media.fileNotFound` | 本地文件不存在（远程 URL 未下载时常见） |
| `media.permissionDenied` | 无权限查看 |
| `media.deleteDenied` | 无权删除（非本人） |
| `media.duplicateFile` | 文件哈希重复 |
| `media.noFilePath` | 缺少 filePath |
| `media.invalidTagExpr` | 标签表达式错误 |
| `admin.required` | 需要管理员权限 |
| `admin.batchDelete.noTargets` | 批量删除无目标 |
| `tag.notFound` / `author.notFound` / `auth.userNotFound` | 资源不存在 |
| `error.internal` | 服务端内部错误 |

---

## curl 速查

```bash
HOST=http://192.168.3.20:3023/api
ADMIN_PASS='wanglei0715'

# 1. 登录拿 token
TOKEN=$(curl -s -X POST $HOST/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 2. 媒体列表
curl -s -H "Authorization: Bearer $TOKEN" "$HOST/media?page=1&limit=24"

# 3. 搜索
curl -s -H "Authorization: Bearer $TOKEN" "$HOST/media?search=Ambush&type=video"

# 4. 导入本地文件（API 推送）
curl -s -X POST $HOST/media \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"filePath":"/media/407876-1080p.mp4","mimeType":"video/mp4"}'

# 5. 流式播放（Range 分段）
curl -s -H "Range: bytes=0-1023" -H "Authorization: Bearer $TOKEN" "$HOST/stream/<uuid>"

# 6. 下载
curl -s -OJ -H "Authorization: Bearer $TOKEN" "$HOST/stream/<uuid>/download"

# 7. 扫描目录
curl -s -X POST $HOST/admin/scan \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"path":"/media"}'

# 8. 生成 API 令牌（管理面板）
curl -s -X POST $HOST/admin/api-tokens \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"脚本推送","description":"n8n"}'
# → 复制返回的 token，之后用它：
curl -s -H "Authorization: Bearer mc_xxxxx" "$HOST/media?limit=5"
```
