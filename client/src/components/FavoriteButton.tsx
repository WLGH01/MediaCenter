import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Api } from '../api';
import { notify } from '../utils/notify';
import { useAuthStore } from '../stores/auth';
import Modal from './feedback/Modal';

interface Props {
    mediaId: string;
}

interface CollectionBrief {
    id: string;
    name: string;
    mediaCount: number;
}

/**
 * 收藏按钮：点击弹出收藏夹选择器（可新建收藏夹、多选收藏夹）
 * - 未登录：点击提示登录
 * - 已收藏的收藏夹打勾，点击切换收藏状态
 */
export default function FavoriteButton({ mediaId }: Props) {
    const { t } = useTranslation();
    const auth = useAuthStore();
    const [open, setOpen] = useState(false);
    const [collections, setCollections] = useState<CollectionBrief[]>([]);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [loading, setLoading] = useState(false);
    const [newName, setNewName] = useState('');
    const [creating, setCreating] = useState(false);

    const loadState = async () => {
        setLoading(true);
        try {
            const [colData, favData] = await Promise.all([
                Api.listCollections(),
                Api.getMediaCollections(mediaId)
            ]);
            setCollections((colData.collections || []).map((c) => ({ id: c.id, name: c.name, mediaCount: c.mediaCount })));
            setSelected(new Set(favData.collectionIds || []));
        } catch (err) {
            notify.error(err);
        } finally {
            setLoading(false);
        }
    };

    const openModal = () => {
        if (!auth.isLoggedIn) {
            notify.error(t('common.loginRequired') || '请先登录');
            return;
        }
        setOpen(true);
        void loadState();
    };

    const toggleCollection = async (collectionId: string) => {
        const isFav = selected.has(collectionId);
        try {
            if (isFav) {
                await notify.promise(Api.removeMediaFromCollection(collectionId, mediaId), {
                    loading: '移除中...',
                    success: '已取消收藏'
                });
                setSelected((prev) => {
                    const next = new Set(prev);
                    next.delete(collectionId);
                    return next;
                });
            } else {
                await notify.promise(Api.addMediaToCollection(collectionId, [mediaId]), {
                    loading: '收藏中...',
                    success: '已收藏'
                });
                setSelected((prev) => new Set(prev).add(collectionId));
            }
        } catch (err) {
            notify.error(err);
        }
    };

    const handleCreate = async () => {
        const name = newName.trim();
        if (!name) {
            notify.error('请输入收藏夹名称');
            return;
        }
        setCreating(true);
        try {
            const data = await Api.createCollection({ name });
            setCollections((prev) => [...prev, { id: data.collection.id, name: data.collection.name, mediaCount: 0 }]);
            await Api.addMediaToCollection(data.collection.id, [mediaId]);
            setSelected((prev) => new Set(prev).add(data.collection.id));
            setNewName('');
            notify.success('已创建并收藏');
        } catch (err) {
            notify.error(err);
        } finally {
            setCreating(false);
        }
    };

    return (
        <>
            <button className="btn btn-secondary btn-sm" onClick={openModal}>
                {selected.size > 0 ? `★ 已收藏 (${selected.size})` : '☆ 收藏'}
            </button>

            <Modal open={open} title="收藏到收藏夹" onClose={() => setOpen(false)} footer={null}>
                {loading ? (
                    <p className="muted">加载中...</p>
                ) : (
                    <>
                        {/* 新建收藏夹 */}
                        <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
                            <input
                                className="form-input flex-1"
                                placeholder="新建收藏夹名称"
                                value={newName}
                                onChange={(e) => setNewName(e.target.value)}
                                onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
                            />
                            <button className="btn btn-primary" onClick={handleCreate} disabled={creating}>
                                {creating ? '...' : '新建'}
                            </button>
                        </div>

                        {collections.length === 0 ? (
                            <p className="muted">暂无收藏夹，先在上方新建一个。</p>
                        ) : (
                            <div style={{ maxHeight: '300px', overflowY: 'auto' }}>
                                {collections.map((c) => (
                                    <label
                                        key={c.id}
                                        style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            padding: '8px 0',
                                            cursor: 'pointer',
                                            borderBottom: '1px solid var(--border-light)'
                                        }}
                                    >
                                        <input
                                            type="checkbox"
                                            checked={selected.has(c.id)}
                                            onChange={() => toggleCollection(c.id)}
                                            style={{ marginRight: '10px' }}
                                        />
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontWeight: '500' }}>{c.name}</div>
                                            <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{c.mediaCount} 个媒体</div>
                                        </div>
                                    </label>
                                ))}
                            </div>
                        )}
                    </>
                )}
            </Modal>
        </>
    );
}
