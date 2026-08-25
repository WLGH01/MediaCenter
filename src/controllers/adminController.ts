import type { Request, Response } from 'express';
import { sql, eq, like, count, asc, desc, inArray, type SQL } from 'drizzle-orm';
import { isUuid, uuidv4 } from '../utils/uuid';
import { getDatabase, schema, ensureDefaultUsers, syncSchemaInternal, API_USERNAME, apiUserId, hashApiToken } from '../db/index';
import { isString } from '../utils/env';
import { hashPassword } from '../utils/hash';
import { randomBytes } from 'node:crypto';
import { serverEvents } from '../utils/serverEvents';
import { deleteFile } from '../utils/storage';
import config from '../config';

/** 系统保留账户（如 API 服务账户）：禁止删除/降级/封禁 */
function isSystemUser(username: string): boolean {
    return username === API_USERNAME;
}

/**
 * 重置数据库 — 清空所有数据并重新初始化
 * POST /api/admin/reset-db
 */
export async function resetDatabase(req: Request, res: Response): Promise<void> {
    // 进入维护模式：阻断所有非重置请求
    req.app.set('maintenance', true);
    console.log('[Admin] 进入维护模式，开始重置数据库...');

    try {
        const db = getDatabase();

        // 1. 删除当前数据库的 public 模式（仅清理当前库，不影响其他库）
        await db.execute(sql`
            DROP SCHEMA IF EXISTS public CASCADE;
            CREATE SCHEMA public;
        `);
        console.log('[Admin] 数据库已清空');

        // 2. 重建 pg_trgm 扩展（被 CASCADE 删除后需要重新启用）
        await db.execute(sql`CREATE EXTENSION IF NOT EXISTS pg_trgm`);
        console.log('[Admin] pg_trgm 扩展已重建');

        // 3. 根据 schema.ts 重建表结构
        await syncSchemaInternal(db);
        console.log('[Admin] 表结构已重建');

        // 4. 重新创建默认管理员
        await ensureDefaultUsers();

        res.json({ message: 'admin.dbReset' });
    } catch (err) {
        console.error('[Admin] 重置数据库失败:', err);
        res.status(500).json({ error: 'admin.resetError' });
    } finally {
        // 退出维护模式
        req.app.set('maintenance', false);
        console.log('[Admin] 退出维护模式');
    }
}

/**
 * 管理员：删除用户
 * DELETE /api/admin/users/:id
 */
export async function deleteUser(req: Request, res: Response): Promise<void> {
    try {
        const id = req.params.id;
        if (!isString(id)) {
            res.status(400).json({ error: 'admin.invalidId' });
            return;
        }

        // 不能删除自己
        if (id === req.user!.id) {
            res.status(400).json({ error: 'admin.cannotDeleteSelf' });
            return;
        }

        const db = getDatabase();
        const existing = await db.select({ id: schema.users.id, username: schema.users.username }).from(schema.users).where(eq(schema.users.id, id)).limit(1).execute();

        if (!existing[0]) {
            res.status(404).json({ error: 'auth.userNotFound' });
            return;
        }

        // 系统保留账户（如 API 服务账户）不可删除
        if (isSystemUser(existing[0].username)) {
            res.status(400).json({ error: 'admin.systemUserProtected' });
            return;
        }

        // 将该用户上传的媒体转移到 API 服务账户（未启用时转移给当前管理员），并设为仅管理员可见
        const transferTargetId = apiUserId ?? req.user!.id;
        if (!transferTargetId) {
            // 极端情况：既无 API 服务账户也无操作者用户 id（理论上 requireAdmin 已保证其一存在）
            res.status(500).json({ error: 'error.internal' });
            return;
        }
        await db
            .update(schema.media)
            .set({ uploaderId: transferTargetId, minRole: 'admin', updatedAt: new Date().toISOString() })
            .where(eq(schema.media.uploaderId, id))
            .execute();

        await db.delete(schema.users).where(eq(schema.users.id, id)).execute();

        // 批量可见性变更无法精确按用户过滤 → 广播一次，让所有客户端刷新列表
        serverEvents.emit('media.updated', {
            type: 'updated',
            actorId: req.user?.id,
            visibility: { uploaderId: transferTargetId, minRole: 'admin' }
        });

        console.log(`[Admin] 用户 ${id} 已被删除，其媒体已转移至 ${transferTargetId}`);
        res.json({ message: 'admin.userDeleted' });
    } catch (err) {
        console.error('[Admin] 删除用户失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 管理员：切换用户封禁状态
 * POST /api/admin/users/:id/toggle-ban
 */
export async function toggleBan(req: Request, res: Response): Promise<void> {
    try {
        const id = req.params.id;
        if (!isString(id)) {
            res.status(400).json({ error: 'admin.invalidId' });
            return;
        }

        // 不能封禁自己
        if (id === req.user!.id) {
            res.status(400).json({ error: 'admin.cannotBanSelf' });
            return;
        }

        const db = getDatabase();
        const existing = await db.select({ id: schema.users.id, username: schema.users.username, banned: schema.users.banned }).from(schema.users).where(eq(schema.users.id, id)).limit(1).execute();

        const user = existing[0];
        if (!user) {
            res.status(404).json({ error: 'auth.userNotFound' });
            return;
        }

        // 系统保留账户（如 API 服务账户）不可封禁
        if (isSystemUser(user.username)) {
            res.status(400).json({ error: 'admin.systemUserProtected' });
            return;
        }

        const newBanned = user.banned ? 0 : 1;

        await db.update(schema.users).set({ banned: newBanned }).where(eq(schema.users.id, id)).execute();

        console.log(`[Admin] 用户 ${id} 封禁状态已切换为 ${newBanned}`);
        res.json({
            message: newBanned ? 'admin.userBanned' : 'admin.userUnbanned',
            banned: !!newBanned
        });
    } catch (err) {
        console.error('[Admin] 切换封禁状态失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 管理员：获取所有用户列表
 * GET /api/admin/users
 */
export async function listUsers(req: Request, res: Response): Promise<void> {
    try {
        const db = getDatabase();
        const page = Math.max(1, parseInt(req.query.page as string) || 1);
        const limit = Math.min(100, Math.max(1, parseInt(req.query.limit as string) || 20));
        const offset = (page - 1) * limit;
        const search = (req.query.search as string)?.trim();
        // 排序：username | role | createdAt
        const sortBy = isString(req.query.sortBy) ? req.query.sortBy : 'createdAt';
        const sortOrder = isString(req.query.sortOrder) && req.query.sortOrder.toLowerCase() === 'asc' ? 'asc' : 'desc';
        let orderBy: SQL;
        if (sortBy === 'username') {
            orderBy = sortOrder === 'asc' ? asc(schema.users.username) : desc(schema.users.username);
        } else if (sortBy === 'role') {
            orderBy = sortOrder === 'asc' ? asc(schema.users.role) : desc(schema.users.role);
        } else {
            orderBy = sortOrder === 'asc' ? asc(schema.users.createdAt) : desc(schema.users.createdAt);
        }

        const where = search ? like(schema.users.username, `%${search}%`) : undefined;

        const [countResult] = await db
            .select({ total: count() })
            .from(schema.users)
            .where(where)
            .execute();
        const total = countResult?.total ?? 0;

        const rows = await db
            .select({
                id: schema.users.id,
                username: schema.users.username,
                role: schema.users.role,
                banned: schema.users.banned,
                createdAt: schema.users.createdAt,
                updatedAt: schema.users.updatedAt
            })
            .from(schema.users)
            .where(where)
            .orderBy(orderBy)
            .limit(limit)
            .offset(offset)
            .execute();

        // 标记系统账户（API 服务账户），前端据此禁用删除/降级/封禁
        const users = rows.map((u) => ({
            ...u,
            isSystemUser: u.username === API_USERNAME
        }));

        res.json({
            users,
            pagination: {
                page,
                limit,
                total,
                totalPages: Math.ceil(total / limit)
            }
        });
    } catch (err) {
        console.error('[Admin] 获取用户列表失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 管理员创建用户（注册关闭时也可由管理员手动添加）
 * POST /api/admin/users
 * Body: { username: string; password: string; role?: 'guest' | 'user' | 'admin' }
 */
export async function createUser(req: Request, res: Response): Promise<void> {
    try {
        const { username, password, role } = req.body;
        if (!isString(username) || !isString(password)) {
            res.status(400).json({ error: 'auth.emptyCredentials' });
            return;
        }
        if (username.length < 3 || username.length > 32) {
            res.status(400).json({ error: 'auth.usernameLength' });
            return;
        }
        if (password.length < config.minPasswordLength) {
            res.status(400).json({ error: 'auth.passwordLength' });
            return;
        }
        if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
            res.status(400).json({ error: 'auth.passwordStrength' });
            return;
        }
        // 角色校验（默认 user）
        const targetRole = isString(role) ? role : 'user';
        if (!['guest', 'user', 'admin'].includes(targetRole)) {
            res.status(400).json({ error: 'admin.invalidRole' });
            return;
        }
        // 系统保留账户名不可创建
        if (username === API_USERNAME) {
            res.status(400).json({ error: 'admin.systemUserProtected' });
            return;
        }

        const db = getDatabase();
        const existing = await db
            .select({ id: schema.users.id })
            .from(schema.users)
            .where(eq(schema.users.username, username))
            .limit(1)
            .execute();
        if (existing.length > 0) {
            res.status(409).json({ error: 'auth.usernameExists' });
            return;
        }

        const id = uuidv4();
        const hash = hashPassword(password);
        await db.insert(schema.users).values({ id, username, passwordHash: hash, role: targetRole }).execute();

        res.status(201).json({
            message: 'admin.users.userCreated',
            user: { id, username, role: targetRole, createdAt: new Date().toISOString() }
        });
    } catch (err) {
        console.error('[Admin] 创建用户失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 管理员：更新用户角色
 * PUT /api/admin/users/:id/role
 */
export async function updateUserRole(req: Request, res: Response): Promise<void> {
    try {
        const id = req.params.id;
        if (!isString(id) || !isUuid(id)) {
            res.status(404).json({ error: 'auth.userNotFound' });
            return;
        }

        const { role } = req.body;
        if (!isString(role)) {
            res.status(400).json({ error: 'error.invalidRole' });
            return;
        }
        const validRoles = ['guest', 'user', 'admin'];

        if (!validRoles.includes(role)) {
            res.status(400).json({ error: 'error.invalidRole' });
            return;
        }

        const db = getDatabase();

        const existing = await db.select({ id: schema.users.id, username: schema.users.username }).from(schema.users).where(eq(schema.users.id, id)).limit(1).execute();

        const user = existing[0];
        if (!user) {
            res.status(404).json({ error: 'auth.userNotFound' });
            return;
        }

        // 不能修改自己的角色
        if (user.id === req.user!.id) {
            res.status(400).json({ error: 'error.cannotSelfChange' });
            return;
        }

        // 系统保留账户（如 API 服务账户）不可降级
        if (isSystemUser(user.username)) {
            res.status(400).json({ error: 'admin.systemUserProtected' });
            return;
        }

        await db.update(schema.users).set({ role, updatedAt: new Date().toISOString() }).where(eq(schema.users.id, id)).execute();

        res.json({
            message: 'admin.roleUpdated',
            user: { id: user.id, username: user.username, role }
        });
    } catch (err) {
        console.error('[Admin] 更新用户角色失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 按路径前缀搜索媒体文件 (仅限管理员)
 * GET /api/admin/media-by-path?prefix=/media
 * 返回 id / title / filePath，供批量删除勾选界面使用
 */
export async function listMediaByPath(req: Request, res: Response): Promise<void> {
    try {
        const prefix = isString(req.query.prefix) ? req.query.prefix.trim() : '';
        if (!prefix) {
            res.status(400).json({ error: 'admin.batchDelete.noTargets' });
            return;
        }

        const db = getDatabase();
        const records = await db
            .select({
                id: schema.media.id,
                title: schema.media.title,
                filePath: schema.media.filePath
            })
            .from(schema.media)
            .where(like(schema.media.filePath, `${prefix}%`))
            .orderBy(schema.media.filePath)
            .limit(10000)
            .execute();

        res.json({ items: records });
    } catch (err) {
        console.error('[Admin] 按路径搜索媒体失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 批量删除媒体文件 (仅限管理员，硬删除)
 * POST /api/admin/batch-delete-media
 * Body: { ids?: string[], pathPrefix?: string }
 */
export async function batchDeleteMedia(req: Request, res: Response): Promise<void> {
    try {
        const { ids, pathPrefix } = req.body;
        const db = getDatabase();

        let targetIds: string[] = [];
        let usedPathPrefix = false;

        if (Array.isArray(ids) && ids.length > 0) {
            targetIds = ids.filter(isString);
        } else if (isString(pathPrefix) && pathPrefix.trim() !== '') {
            usedPathPrefix = true;
            const prefix = pathPrefix.trim();
            // 查询指定路径前缀的所有媒体 ID
            const records = await db
                .select({ id: schema.media.id })
                .from(schema.media)
                .where(like(schema.media.filePath, `${prefix}%`))
                .execute();
            targetIds = records.map(r => r.id);
        }

        if (targetIds.length === 0) {
            // 按路径前缀查询但没有匹配 → 返回 0 而非报错（前端据此提示"无匹配"）
            if (usedPathPrefix) {
                res.json({ message: 'admin.batchDelete.noMatch', count: 0 });
                return;
            }
            res.status(400).json({ error: 'admin.batchDelete.noTargets' });
            return;
        }

        // 分批查询物理文件路径进行物理删除 (避免 SQL 参数上限)
        const BATCH_SIZE = 500;
        let deletedCount = 0;

        for (let i = 0; i < targetIds.length; i += BATCH_SIZE) {
            const batchIds = targetIds.slice(i, i + BATCH_SIZE);
            const records = await db
                .select({
                    id: schema.media.id,
                    filePath: schema.media.filePath,
                    thumbPath: schema.media.thumbPath
                })
                .from(schema.media)
                .where(inArray(schema.media.id, batchIds))
                .execute();

            for (const record of records) {
                // 删除物理媒体文件
                deleteFile(record.filePath);
                // 删除缩略图
                if (record.thumbPath) {
                    deleteFile(record.thumbPath);
                }
            }

            // 从关联表和主表中删除
            await db.delete(schema.mediaTags).where(inArray(schema.mediaTags.mediaId, batchIds)).execute();
            await db.delete(schema.media).where(inArray(schema.media.id, batchIds)).execute();
            
            deletedCount += records.length;
        }

        // 广播批量删除事件，通知客户端刷新
        serverEvents.emit('media.updated', {
            type: 'deleted_all',
            actorId: req.user?.id
        });

        console.log(`[Admin] 批量硬删除成功：共删除了 ${deletedCount} 条媒体记录及物理文件`);
        res.json({
            message: 'admin.batchDelete.success',
            count: deletedCount
        });
    } catch (err) {
        console.error('[Admin] 批量删除失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 列出所有 API 令牌（不含明文 token，仅元信息）
 * GET /api/admin/api-tokens
 */
export async function listApiTokens(req: Request, res: Response): Promise<void> {
    try {
        const db = getDatabase();
        const rows = await db
            .select({
                id: schema.apiTokens.id,
                name: schema.apiTokens.name,
                description: schema.apiTokens.description,
                role: schema.apiTokens.role,
                createdAt: schema.apiTokens.createdAt,
                lastUsedAt: schema.apiTokens.lastUsedAt
            })
            .from(schema.apiTokens)
            .orderBy(desc(schema.apiTokens.createdAt))
            .execute();

        res.json({ tokens: rows });
    } catch (err) {
        console.error('[Admin] 获取 API 令牌列表失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 生成新的 API 令牌（仅返回一次明文）
 * POST /api/admin/api-tokens
 * Body: { name: string; description?: string }
 */
export async function createApiToken(req: Request, res: Response): Promise<void> {
    try {
        const { name, description } = req.body;
        if (!isString(name) || !name.trim()) {
            res.status(400).json({ error: 'admin.apiToken.nameRequired' });
            return;
        }
        const trimmedName = name.trim().slice(0, 64);
        const trimmedDesc = isString(description) ? description.trim().slice(0, 256) : '';

        // 生成随机令牌：mc_ 前缀 + 32 字节随机数（base64url）
        const rawToken = `mc_${randomBytes(32).toString('base64url')}`;

        const db = getDatabase();
        await db
            .insert(schema.apiTokens)
            .values({
                name: trimmedName,
                description: trimmedDesc,
                token: hashApiToken(rawToken),
                role: 'admin'
            })
            .execute();

        console.log(`[Admin] 已生成 API 令牌: ${trimmedName}`);
        res.status(201).json({
            message: 'admin.apiToken.created',
            token: rawToken, // 仅此一次返回明文
            hint: 'admin.apiToken.copyNow'
        });
    } catch (err) {
        console.error('[Admin] 生成 API 令牌失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 删除 API 令牌（立即失效）
 * DELETE /api/admin/api-tokens/:id
 */
export async function deleteApiToken(req: Request, res: Response): Promise<void> {
    try {
        const id = req.params.id;
        if (!isString(id) || !isUuid(id)) {
            res.status(404).json({ error: 'admin.apiToken.notFound' });
            return;
        }

        const db = getDatabase();
        const existing = await db.select({ id: schema.apiTokens.id }).from(schema.apiTokens).where(eq(schema.apiTokens.id, id)).limit(1).execute();
        if (!existing[0]) {
            res.status(404).json({ error: 'admin.apiToken.notFound' });
            return;
        }

        await db.delete(schema.apiTokens).where(eq(schema.apiTokens.id, id)).execute();
        res.json({ message: 'admin.apiToken.deleted' });
    } catch (err) {
        console.error('[Admin] 删除 API 令牌失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}
