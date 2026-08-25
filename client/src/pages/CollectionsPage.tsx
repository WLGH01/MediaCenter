import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Api } from '../api';
import { notify } from '../utils/notify';
import MediaCard from '../components/list/MediaCard';
import Pagination from '../components/list/Pagination';
import LoadingState from '../components/feedback/LoadingState';
import EmptyState from '../components/feedback/EmptyState';
import Modal from '../components/feedback/Modal';
import { showConfirm } from '../components/feedback/ConfirmDialog';
import type { Media } from '../types';

const PAGE_SIZE = 12;

interface Collection {
    id: string;
    name: string;
    description: string;
    createdAt: string;
    updatedAt: string;
    mediaCount: number;
}

/**
 * 收藏夹页面（/collections）
 * - 收藏夹列表（创建/编辑/删除）
 * - 选中收藏夹后：媒体网格 + 搜索 + 类型筛选 + 作者筛选 + 分页
 */
export default function CollectionsPage() {
    const { t } = useTranslation();

    // 收藏夹列表
    const [collections, setCollections] = useState<Collection[]>([]);
    const [loadingCols, setLoadingCols] = useState(true);
    const [activeId, setActiveId] = useState<string | null>(null);

    // 新建/编辑弹窗
    const [showEdit, setShowEdit] = useState(false);
    const [editing, setEditing] = useState<Collection | null>(null);
    const [editName, setEditName] = useState('');
    const [editDesc, setEditDesc] = useState('');
    const [saving, setSaving] = useState(false);

    // 收藏夹内媒体
    const [items, setItems] = useState<Media[]>([]);
    const [page, setPage] = useState(1);
    const [totalPages, setTotalPages] = useState(1);
    const [search, setSearch] = useState('');
    const [typeFilter, setTypeFilter] = useState('');
    const [authorFilter, setAuthorFilter] = useState('');
    const [loadingMedia, setLoadingMedia] = useState(false);

    const loadCollections = async () => {
        setLoadingCols(true);
        try {
            const data = await Api.listCollections();
            setCollections(data.collections || []);
            // 保持当前选中，若被删则回退第一个
            setActiveId((prev) => {
                const ids = (data.collections || []).map((c) => c.id);
                if (prev && ids.includes(prev)) return prev;
                return ids[0] ?? null;
            });
        } catch (err) {
            notify.error(err);
        } finally {
            setLoadingCols(false);
        }
    };

    useEffect(() => { void loadCollections(); }, []);

    // 切换收藏夹时重置筛选
    const selectCollection = (id: string) => {
        setActiveId(id);
        setPage(1);
        setSearch('');
        setTypeFilter('');
        setAuthorFilter('');
    };

    // 加载收藏夹内媒体
    useEffect(() => {
        if (!activeId) return;
        setLoadingMedia(true);
        const timer = setTimeout(() => {
            Api.listCollectionMedia(activeId, {
                page,
                limit: PAGE_SIZE,
                search: search || undefined,
                type: typeFilter || undefined,
                authorId: authorFilter || undefined
            })
                .then((data) => {
                    setItems(data.items || []);
                    setTotalPages(data.pagination?.totalPages || 1);
                })
                .catch((err) => notify.error(err))
                .finally(() => setLoadingMedia(false));
        }, search ? 400 : 0); // 搜索防抖
        return () => clearTimeout(timer);
    }, [activeId, page, search, typeFilter, authorFilter]);

    const openCreate = () => {
        setEditing(null);
        setEditName('');
        setEditDesc('');
        setShowEdit(true);
    };

    const openEdit = (c: Collection) => {
        setEditing(c);
        setEditName(c.name);
        setEditDesc(c.description);
        setShowEdit(true);
    };

    const handleSave = async () => {
        const name = editName.trim();
        if (!name) {
            notify.error('请输入收藏夹名称');
            return;
        }
        setSaving(true);
        try {
            if (editing) {
                await notify.promise(Api.updateCollection(editing.id, { name, description: editDesc.trim() }), {
                    loading: '保存中...',
                    success: '收藏夹已更新'
                });
            } else {
                const data = await notify.run(() => Api.createCollection({ name, description: editDesc.trim() }));
                if (data) {
                    setActiveId(data.collection.id);
                }
                notify.success('收藏夹已创建');
            }
            setShowEdit(false);
            await loadCollections();
        } catch (err) {
            notify.error(err);
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = (c: Collection) => {
        showConfirm({
            message: `确定删除收藏夹 "${c.name}" 吗？收藏夹内的媒体不会被删除，只是取消收藏关系。`,
            danger: true,
            confirmText: t('common.confirm'),
            cancelText: t('common.cancel'),
            onConfirm: async () => {
                try {
                    await notify.promise(Api.deleteCollection(c.id), {
                        loading: '删除中...',
                        success: '收藏夹已删除'
                    });
                    if (activeId === c.id) setActiveId(null);
                    await loadCollections();
                } catch (err) {
                    notify.error(err);
                }
            }
        });
    };

    const activeCollection = collections.find((c) => c.id === activeId);

    return (
        <div>
            <div className="page-header">
                <div>
                    <h1>收藏夹</h1>
                    <p>管理你的视频收藏，按分类、作者筛选查找</p>
                </div>
                <div className="flex-gap-8">
                    <button className="btn btn-primary" onClick={openCreate}>+ 新建收藏夹</button>
                </div>
            </div>

            {/* 收藏夹列表 */}
            <div className="card section-card">
                {loadingCols ? (
                    <LoadingState />
                ) : collections.length === 0 ? (
                    <EmptyState
                        title="还没有收藏夹"
                        description="点击右上角「新建收藏夹」开始创建，之后可以在媒体详情页收藏到对应收藏夹。"
                    />
                ) : (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                        {collections.map((c) => (
                            <div
                                key={c.id}
                                onClick={() => selectCollection(c.id)}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '8px',
                                    padding: '8px 14px',
                                    border: `1px solid ${activeId === c.id ? 'var(--primary)' : 'var(--border)'}`,
                                    borderRadius: '6px',
                                    cursor: 'pointer',
                                    background: activeId === c.id ? 'var(--bg-secondary)' : 'transparent'
                                }}
                            >
                                <div>
                                    <div style={{ fontWeight: '500' }}>{c.name}</div>
                                    <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{c.mediaCount} 个媒体</div>
                                </div>
                                <button
                                    className="btn btn-ghost"
                                    style={{ padding: '2px 6px', fontSize: '12px' }}
                                    onClick={(e) => { e.stopPropagation(); openEdit(c); }}
                                >
                                    编辑
                                </button>
                                <button
                                    className="btn btn-ghost"
                                    style={{ padding: '2px 6px', fontSize: '12px', color: 'var(--danger)' }}
                                    onClick={(e) => { e.stopPropagation(); handleDelete(c); }}
                                >
                                    删除
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* 收藏夹内媒体 */}
            {activeCollection && (
                <div className="card section-card" style={{ marginTop: '16px' }}>
                    <div className="card-header">
                        <h2>{activeCollection.name}（{activeCollection.mediaCount}）</h2>
                    </div>

                    {/* 筛选栏：搜索 / 类型 / 作者 */}
                    <div className="search-bar" style={{ marginBottom: '16px' }}>
                        <div className="search-bar-group">
                            <input
                                className="form-input"
                                placeholder="搜索标题..."
                                value={search}
                                onChange={(e) => { setSearch(e.target.value); setPage(1); }}
                            />
                            <select
                                className="form-input form-select"
                                value={typeFilter}
                                onChange={(e) => { setTypeFilter(e.target.value); setPage(1); }}
                            >
                                <option value="">全部类型</option>
                                <option value="video">视频</option>
                                <option value="audio">音频</option>
                                <option value="image">图片</option>
                            </select>
                            <input
                                className="form-input"
                                placeholder="按作者名筛选"
                                value={authorFilter}
                                onChange={(e) => { setAuthorFilter(e.target.value); setPage(1); }}
                            />
                        </div>
                    </div>

                    {loadingMedia && items.length === 0 ? (
                        <LoadingState />
                    ) : items.length === 0 ? (
                        <p className="muted">收藏夹为空，去媒体库收藏视频吧。</p>
                    ) : (
                        <>
                            <div className={`grid grid-2${loadingMedia ? ' grid-loading' : ''}`}>
                                {items.map((item) => (
                                    <MediaCard key={item.id} media={item} />
                                ))}
                            </div>
                            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
                        </>
                    )}
                </div>
            )}

            {/* 新建/编辑弹窗 */}
            <Modal
                open={showEdit}
                title={editing ? '编辑收藏夹' : '新建收藏夹'}
                onClose={() => setShowEdit(false)}
                footer={
                    <>
                        <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                            {saving ? '保存中...' : '保存'}
                        </button>
                        <button className="btn btn-secondary" onClick={() => setShowEdit(false)}>
                            {t('common.cancel')}
                        </button>
                    </>
                }
            >
                <div className="form-group">
                    <label>名称</label>
                    <input
                        className="form-input"
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        placeholder="如：喜欢的视频 / 待看"
                        autoFocus
                    />
                </div>
                <div className="form-group">
                    <label>描述（可选）</label>
                    <textarea
                        className="form-input"
                        value={editDesc}
                        onChange={(e) => setEditDesc(e.target.value)}
                        placeholder="这个收藏夹放什么内容..."
                        rows={2}
                    />
                </div>
            </Modal>
        </div>
    );
}
