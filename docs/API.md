# MediaCenter API 文档

基础地址：`http://localhost:3000/api`

## 认证

- JWT：`Authorization: Bearer <token>`，登录接口返回 `token`。
- 静态服务令牌：`Authorization: Bearer <API_TOKEN>`，仅建议服务端使用。
- 未认证请求视为 `guest`。媒体可见性按 `guest`、`user`、`admin`、`owner` 判定。
- 流媒体 `<video>` 无法稳定携带 Header，使用 `GET /media/:id/stream-token` 获取短时签名 URL。

## 统一响应与错误

成功响应通常为 JSON；错误格式：

```json
{ "error": "media.notFound" }
```

常见状态码：`400` 参数错误、`401` 未登录/令牌无效、`403` 无权限、`404` 不存在、`409` 冲突、`429` 触发限流、`500` 服务端错误。

## 认证接口

### `POST /auth/login`

```json
{ "username": "admin", "password": "***" }
```

返回 `{ "token": "jwt...", "user": { "id": "uuid", "username": "admin", "role": "admin" } }`。

### `POST /auth/register`

开放注册时可用：`{ "username": "alice", "password": "..." }`。

### `GET /auth/profile` · `POST /auth/logout` · `POST /auth/refresh` · `POST /auth/change-password`

均需 JWT；修改密码请求体为 `{ "currentPassword": "...", "newPassword": "..." }`。

## 媒体

### `GET /media`

查询参数支持 `page`、`limit`、`search`、`type`、`tags`、`authorId`、`sort`、`order`。返回分页媒体列表。

### `GET /media/:id` · `GET /media/stats`

获取媒体详情或当前用户可见范围内的统计。

### `POST /media/upload`

需登录，`multipart/form-data`，字段 `file`，以及可选 `title`、`description`、`minRole`、`tags`、`authorId`。

### `POST /media` · `PUT /media/:id` · `DELETE /media/:id` · `PUT /media/:id/restore`

创建、编辑、删除（软删除）、恢复媒体；需登录，编辑/删除需本人或管理员。

### `GET /media/:id/stream-token`

需登录或有可见权限。返回短时 URL，例如：

```json
{ "url": "/api/stream/uuid?expires=...&sig=...&purpose=stream" }
```

## 播放、下载与缩略图

### `GET /stream/:id`

支持 `Range: bytes=0-1048575`，响应包含 `206 Partial Content`、`Content-Range`、`Accept-Ranges: bytes`。签名 URL 可直接给浏览器播放。

### `GET /stream/:id/download` · `GET /stream/:id/thumb`

分别下载原文件和获取服务端缩略图；均执行媒体可见性检查。

## 标签、作者与公开资料

- `GET /tags`、`POST /tags`、`PUT /tags/:id`、`DELETE /tags/:id`
- `GET /authors`、`GET /authors/:id`、`POST /authors`、`PUT /authors/:id`、`DELETE /authors/:id`
- `GET /users/:id`：公开用户资料（按可见性返回）

写操作需管理员权限。

## 管理接口

- `GET /admin/users`：用户列表
- `POST /admin/users`：创建用户
- `PUT /admin/users/:id/role`：调整角色
- `DELETE /admin/users/:id`：删除用户
- `POST /admin/users/:id/toggle-ban`：封禁/解封
- `POST /admin/scan`：扫描配置的媒体目录并导入
- `POST /admin/reset-db`：重置数据库（高危操作，仅管理员；执行前请备份）

## 实时事件

`GET /events` 使用 SSE 推送媒体变更；浏览器 EventSource 可通过短时 `token` 查询参数连接。服务端每 15 秒发送心跳。

## curl 示例

```bash
TOKEN=$(curl -s http://localhost:3000/api/auth/login -H 'content-type: application/json' -d '{"username":"admin","password":"..."}' | jq -r .token)
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:3000/api/media?page=1&limit=24'
curl -H "Range: bytes=0-1023" 'http://localhost:3000/api/stream/<uuid>?expires=...&sig=...'
```
