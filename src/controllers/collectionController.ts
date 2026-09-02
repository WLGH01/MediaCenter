import type { Request, Response } from 'express';
import { eq, and, asc, desc, count, ilike, inArray, notInArray, isNull, or, sql, type SQL } from 'drizzle-orm';
import { isUuid, uuidv4 } from '../utils/uuid';
import { getDatabase, schema } from '../db/index';
import { isString, isArray } from '../utils/env';
import { generateSignedUrl } from '../utils/signUrl';
import { parseExpr, evaluateExpr } from '../utils/exprParser';
import type { ExprNode } from '../utils/exprParser';

/**
 * 收藏夹控制器
 * 收藏夹（collections）是每个用户私有的媒体分组；收藏夹内媒体多对多（collection_media）。
 */

/** 确保收藏夹属于当前登录用户，否则返回 false */
async function ensureOwner(req: Request, res: Response, collectionId: string): Promise<boolean> {
    const db = getDatabase();
    const rows = await db
        .select({ id: schema.collections.id, userId: schema.collections.userId })
        .from(schema.collections)
        .where(eq(schema.collections.id, collectionId))
        .limit(1)
        .execute();
    const c = rows[0];
    if (!c) {
        res.status(404).json({ error: 'collection.notFound' });
        return false;
    }
    if (c.userId !== req.user!.id) {
        res.status(403).json({ error: 'collection.forbidden' });
        return false;
    }
    return true;
}

/**
 * 我的收藏夹列表（含每个收藏夹的媒体数量）
 * GET /api/collections
 */
export async function listCollections(req: Request, res: Response): Promise<void> {
    try {
        if (!req.user!.id) {
            res.status(401).json({ error: 'auth.loginRequired' });
            return;
        }
        const db = getDatabase();
        const rows = await db
            .select({
                id: schema.collections.id,
                name: schema.collections.name,
                description: schema.collections.description,
                createdAt: schema.collections.createdAt,
                updatedAt: schema.collections.updatedAt,
                mediaCount: count(schema.collectionMedia.mediaId)
            })
            .from(schema.collections)
            .leftJoin(schema.collectionMedia, eq(schema.collections.id, schema.collectionMedia.collectionId))
            .where(eq(schema.collections.userId, req.user!.id))
            .groupBy(schema.collections.id)
            .orderBy(desc(schema.collections.updatedAt))
            .execute();

        res.json({ collections: rows });
    } catch (err) {
        console.error('[Collection] 获取收藏夹列表失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 创建收藏夹
 * POST /api/collections
 * Body: { name: string; description?: string }
 */
export async function createCollection(req: Request, res: Response): Promise<void> {
    try {
        if (!req.user!.id) {
            res.status(401).json({ error: 'auth.loginRequired' });
            return;
        }
        const { name, description } = req.body;
        if (!isString(name) || !name.trim()) {
            res.status(400).json({ error: 'collection.nameRequired' });
            return;
        }
        const trimmedName = name.trim().slice(0, 64);
        const trimmedDesc = isString(description) ? description.trim().slice(0, 256) : '';

        const db = getDatabase();
        const [collection] = await db
            .insert(schema.collections)
            .values({
                id: uuidv4(),
                name: trimmedName,
                description: trimmedDesc,
                userId: req.user!.id
            })
            .returning()
            .execute();

        res.status(201).json({ collection: { ...collection, mediaCount: 0 } });
    } catch (err) {
        console.error('[Collection] 创建收藏夹失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 更新收藏夹（改名/改描述）
 * PUT /api/collections/:id
 * Body: { name?: string; description?: string }
 */
export async function updateCollection(req: Request, res: Response): Promise<void> {
    try {
        const { id } = req.params;
        if (!isString(id) || !isUuid(id)) {
            res.status(404).json({ error: 'collection.notFound' });
            return;
        }
        if (!(await ensureOwner(req, res, id))) return;

        const { name, description } = req.body;
        const updates: Record<string, unknown> = {};
        if (isString(name) && name.trim()) updates.name = name.trim().slice(0, 64);
        if (isString(description)) updates.description = description.trim().slice(0, 256);
        if (Object.keys(updates).length === 0) {
            res.status(400).json({ error: 'collection.noUpdate' });
            return;
        }
        updates.updatedAt = new Date().toISOString();

        const db = getDatabase();
        const [collection] = await db
            .update(schema.collections)
            .set(updates)
            .where(eq(schema.collections.id, id))
            .returning()
            .execute();

        res.json({ collection });
    } catch (err) {
        console.error('[Collection] 更新收藏夹失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 删除收藏夹（连带收藏关系，不删除媒体本身）
 * DELETE /api/collections/:id
 */
export async function deleteCollection(req: Request, res: Response): Promise<void> {
    try {
        const { id } = req.params;
        if (!isString(id) || !isUuid(id)) {
            res.status(404).json({ error: 'collection.notFound' });
            return;
        }
        if (!(await ensureOwner(req, res, id))) return;

        const db = getDatabase();
        await db.delete(schema.collections).where(eq(schema.collections.id, id)).execute();
        res.json({ message: 'collection.deleted' });
    } catch (err) {
        console.error('[Collection] 删除收藏夹失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/** 获取所有未删除的媒体 ID（NOT 运算全集，供标签表达式求值） */
async function getAllMediaIds(): Promise<Set<string>> {
    const db = getDatabase();
    const rows = await db
        .select({ id: schema.media.id })
        .from(schema.media)
        .where(isNull(schema.media.deletedAt))
        .execute();
    return new Set(rows.map((r) => r.id));
}

/** 标签维度求值：叶子 → 匹配 tags.name / altNames → 返回媒体 ID 集合 */
async function evaluateTagAst(node: ExprNode): Promise<Set<string>> {
    return evaluateExpr(node, async (name) => {
        const db = getDatabase();
        const rows = await db
            .select({ mediaId: schema.mediaTags.mediaId })
            .from(schema.mediaTags)
            .innerJoin(schema.tags, eq(schema.mediaTags.tagId, schema.tags.id))
            .where(
                or(
                    eq(schema.tags.name, name),
                    sql`${name} = ANY(${schema.tags.altNames})`
                )
            )
            .execute();
        return new Set(rows.map((r) => r.mediaId));
    }, getAllMediaIds);
}

/**
 * 收藏夹内的媒体列表（支持搜索 / 类型 / 作者 / 标签筛选，分页）
 * GET /api/collections/:id/media?page=1&limit=20&search=xxx&type=video&authorId=uuid&tags=A&(B|C)
 */
export async function listCollectionMedia(req: Request, res: Response): Promise<void> {
    try {
        const { id } = req.params;
        if (!isString(id) || !isUuid(id)) {
            res.status(404).json({ error: 'collection.notFound' });
            return;
        }
        if (!(await ensureOwner(req, res, id))) return;

        const db = getDatabase();
        const qPage = isString(req.query.page) ? parseInt(req.query.page, 10) : NaN;
        const page = Math.max(1, qPage || 1);
        const qLimit = isString(req.query.limit) ? parseInt(req.query.limit, 10) : NaN;
        const noLimit = qLimit === 0;
        const limit = noLimit ? 0 : Math.min(100, Math.max(1, qLimit || 20));
        const offset = noLimit ? 0 : (page - 1) * limit;

        const search = isString(req.query.search) ? req.query.search.trim() : undefined;
        const type = isString(req.query.type) ? req.query.type : undefined;
        const authorId = isString(req.query.authorId) ? req.query.authorId : undefined;
        const tagsExpr = isString(req.query.tags) ? req.query.tags.trim() : undefined;
        const sortBy = isString(req.query.sortBy) ? req.query.sortBy : 'createdAt';
        const sortOrder = isString(req.query.sortOrder) && req.query.sortOrder.toLowerCase() === 'asc' ? 'asc' : 'desc';

        const conditions: SQL[] = [eq(schema.collectionMedia.collectionId, id)];
        // 排除已软删除的媒体
        conditions.push(isNull(schema.media.deletedAt));
        if (search) {
            conditions.push(ilike(schema.media.title, `%${search}%`));
        }
        if (type) {
            conditions.push(ilike(schema.media.mimeType, `${type}/%`));
        }
        if (authorId) {
            conditions.push(eq(schema.media.authorId, authorId));
        }
        // 标签表达式筛选：?tags=A&(B|C)|D  支持 ! 排除
        if (tagsExpr) {
            try {
                const ast = parseExpr(tagsExpr);
                if (ast) {
                    if (ast.type === 'not') {
                        // 顶层 NOT → 只求 child，用 NOT IN 避免全量补集
                        const idSet = await evaluateTagAst(ast.child);
                        const ids = [...idSet];
                        if (ids.length > 0) {
                            conditions.push(notInArray(schema.collectionMedia.mediaId, ids));
                        }
                    } else {
                        const idSet = await evaluateTagAst(ast);
                        const ids = [...idSet];
                        if (ids.length === 0) {
                            res.json({ items: [], pagination: { page, limit, total: 0, totalPages: 0, sortBy, sortOrder } });
                            return;
                        }
                        // 限定在收藏夹内：先求交集，再查收藏夹
                        conditions.push(inArray(schema.media.id, ids));
                    }
                }
            } catch {
                res.status(400).json({ error: 'media.invalidTagExpr' });
                return;
            }
        }

        const where = and(...conditions);

        // 总数
        const [countRes] = await db
            .select({ total: count() })
            .from(schema.collectionMedia)
            .innerJoin(schema.media, eq(schema.collectionMedia.mediaId, schema.media.id))
            .where(where)
            .execute();
        const total = Number(countRes?.total || 0);

        // 排序：createdAt（收藏时间）/ title / media 的 createdAt
        let orderBy: SQL;
        if (sortBy === 'title') {
            orderBy = sortOrder === 'asc' ? asc(schema.media.title) : desc(schema.media.title);
        } else if (sortBy === 'mediaCreatedAt') {
            orderBy = sortOrder === 'asc' ? asc(schema.media.createdAt) : desc(schema.media.createdAt);
        } else {
            // 默认按收藏时间
            orderBy = sortOrder === 'asc' ? asc(schema.collectionMedia.createdAt) : desc(schema.collectionMedia.createdAt);
        }

        let query = db
            .select({
                id: schema.media.id,
                title: schema.media.title,
                description: schema.media.description,
                mimeType: schema.media.mimeType,
                fileSize: schema.media.fileSize,
                duration: schema.media.duration,
                thumbPath: schema.media.thumbPath,
                createdAt: schema.media.createdAt,
                uploaderId: schema.media.uploaderId,
                uploaderName: schema.users.username,
                authorId: schema.media.authorId,
                authorName: schema.authors.name,
                favoritedAt: schema.collectionMedia.createdAt
            })
            .from(schema.collectionMedia)
            .innerJoin(schema.media, eq(schema.collectionMedia.mediaId, schema.media.id))
            .leftJoin(schema.users, eq(schema.media.uploaderId, schema.users.id))
            .leftJoin(schema.authors, eq(schema.media.authorId, schema.authors.id))
            .where(where)
            .orderBy(orderBy);

        if (!noLimit) query = query.limit(limit).offset(offset) as typeof query;
        const rows = await query.execute();

        // 生成签名链接 + 作者对象
        const items = rows.map((r) => {
            const { thumbPath, ...rest } = r;
            return {
                ...rest,
                author: r.authorId ? { id: r.authorId, name: r.authorName } : null,
                streamUrl: generateSignedUrl(r.id, 'stream', req.user?.id || null, { role: req.user?.role }),
                thumbUrl: thumbPath
                    ? generateSignedUrl(r.id, 'thumb', req.user?.id || null, { expiresIn: 24 * 3600, role: req.user?.role })
                    : null
            };
        });

        res.json({
            items,
            pagination: { page, limit, total, totalPages: noLimit ? 1 : Math.ceil(total / limit), sortBy, sortOrder }
        });
    } catch (err) {
        console.error('[Collection] 获取收藏夹媒体失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 添加媒体到收藏夹（批量）
 * POST /api/collections/:id/media
 * Body: { mediaIds: string[] }
 */
export async function addMediaToCollection(req: Request, res: Response): Promise<void> {
    try {
        const { id } = req.params;
        if (!isString(id) || !isUuid(id)) {
            res.status(404).json({ error: 'collection.notFound' });
            return;
        }
        if (!(await ensureOwner(req, res, id))) return;

        const { mediaIds } = req.body;
        const ids = isArray(mediaIds) ? mediaIds.filter(isString).filter(isUuid) : [];
        if (ids.length === 0) {
            res.status(400).json({ error: 'collection.mediaRequired' });
            return;
        }

        const db = getDatabase();
        // 查询收藏夹里已存在的媒体，避免唯一冲突
        const existing = await db
            .select({ mediaId: schema.collectionMedia.mediaId })
            .from(schema.collectionMedia)
            .where(and(eq(schema.collectionMedia.collectionId, id), inArray(schema.collectionMedia.mediaId, ids)))
            .execute();
        const existingSet = new Set(existing.map((e) => e.mediaId));
        const toAdd = ids.filter((m) => !existingSet.has(m));
        if (toAdd.length > 0) {
            await db
                .insert(schema.collectionMedia)
                .values(toAdd.map((mediaId) => ({ collectionId: id, mediaId })))
                .execute();
        }

        await db.update(schema.collections).set({ updatedAt: new Date().toISOString() }).where(eq(schema.collections.id, id)).execute();

        res.json({ message: 'collection.added', added: toAdd.length, skipped: ids.length - toAdd.length });
    } catch (err) {
        console.error('[Collection] 添加媒体失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 从收藏夹移除媒体
 * DELETE /api/collections/:id/media/:mediaId
 */
export async function removeMediaFromCollection(req: Request, res: Response): Promise<void> {
    try {
        const { id, mediaId } = req.params;
        if (!isString(id) || !isUuid(id) || !isString(mediaId) || !isUuid(mediaId)) {
            res.status(404).json({ error: 'collection.notFound' });
            return;
        }
        if (!(await ensureOwner(req, res, id))) return;

        const db = getDatabase();
        await db
            .delete(schema.collectionMedia)
            .where(and(eq(schema.collectionMedia.collectionId, id), eq(schema.collectionMedia.mediaId, mediaId)))
            .execute();

        await db.update(schema.collections).set({ updatedAt: new Date().toISOString() }).where(eq(schema.collections.id, id)).execute();

        res.json({ message: 'collection.removed' });
    } catch (err) {
        console.error('[Collection] 移除媒体失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}

/**
 * 查询某媒体被当前用户收藏在哪些收藏夹
 * GET /api/media/:id/collections
 */
export async function getMediaCollections(req: Request, res: Response): Promise<void> {
    try {
        const { id } = req.params;
        if (!isString(id) || !isUuid(id)) {
            res.status(404).json({ error: 'media.notFound' });
            return;
        }
        if (!req.user!.id) {
            res.json({ collectionIds: [] });
            return;
        }

        const db = getDatabase();
        const rows = await db
            .select({ collectionId: schema.collectionMedia.collectionId })
            .from(schema.collectionMedia)
            .innerJoin(schema.collections, eq(schema.collectionMedia.collectionId, schema.collections.id))
            .where(and(eq(schema.collectionMedia.mediaId, id), eq(schema.collections.userId, req.user!.id)))
            .execute();

        res.json({ collectionIds: rows.map((r) => r.collectionId) });
    } catch (err) {
        console.error('[Collection] 查询媒体收藏状态失败:', err);
        res.status(500).json({ error: 'error.internal' });
    }
}
