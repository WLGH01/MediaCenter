import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Api } from '../../api';
import { notify } from '../../utils/notify';
import { useAuthStore } from '../../stores/auth';
import AdminGuard from '../../components/auth/AdminGuard';
import { showConfirm } from '../../components/feedback/ConfirmDialog';

export default function AdminPage() {
    const navigate = useNavigate();
    const { t } = useTranslation();
    const [scanning, setScanning] = useState(false);
    const [scanPath, setScanPath] = useState('./uploads');
    const [resetting, setResetting] = useState(false);
    const [fileHashQuery, setFileHashQuery] = useState('');
    const [fileHashSearching, setFileHashSearching] = useState(false);
    const [fileHashResult, setFileHashResult] = useState<{ found: boolean; media?: { id: string; title: string } } | null>(null);

    // 批量删除状态
    const [batchDelPath, setBatchDelPath] = useState('');
    const [batchDeleting, setBatchDeleting] = useState(false);
    const [batchMediaList, setBatchMediaList] = useState<{ id: string; title: string; filePath: string }[]>([]);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [searchingBatch, setSearchingBatch] = useState(false);

    // 根据目录前缀搜索匹配的媒体
    const searchMediaByPath = async () => {
        const prefix = batchDelPath.trim();
        if (!prefix) return;
        setSearchingBatch(true);
        try {
            const res = await Api.listMediaByPath(prefix);
            const matched = (res.items || []).map((item: any) => ({
                id: item.id,
                title: item.title,
                filePath: item.filePath
            }));
            setBatchMediaList(matched);
            setSelectedIds(new Set(matched.map((m: any) => m.id))); // 默认全选
            notify.success(`已找到 ${matched.length} 个匹配的媒体文件`);
        } catch (err) {
            notify.error(err);
        } finally {
            setSearchingBatch(false);
        }
    };

    const handleBatchDelete = () => {
        const idsToDelete = Array.from(selectedIds);
        if (idsToDelete.length === 0) {
            notify.error('请选择至少一个媒体文件进行删除');
            return;
        }

        showConfirm({
            message: `确定要删除这 ${idsToDelete.length} 个媒体文件吗？这会从磁盘上彻底物理删除对应的源文件！此操作不可撤销。`,
            danger: true,
            confirmText: t('common.confirm'),
            cancelText: t('common.cancel'),
            onConfirm: async () => {
                setBatchDeleting(true);
                await notify.promise(Api.batchDeleteMedia({ ids: idsToDelete }), {
                    loading: '正在执行批量删除...',
                    success: (data: any) => `批量删除成功，共清理了 ${data.count} 个媒体物理文件及数据库记录`,
                    onSuccess: () => {
                        // 移除已删除的项
                        setBatchMediaList(prev => prev.filter(m => !selectedIds.has(m.id)));
                        setSelectedIds(new Set());
                    }
                });
                setBatchDeleting(false);
            }
        });
    };

    const toggleSelectAll = () => {
        if (selectedIds.size === batchMediaList.length) {
            setSelectedIds(new Set());
        } else {
            setSelectedIds(new Set(batchMediaList.map(m => m.id)));
        }
    };

    const toggleSelect = (id: string) => {
        const next = new Set(selectedIds);
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        setSelectedIds(next);
    };

    const runScan = async () => {
        setScanning(true);
        await notify.promise(Api.scanDirectory(scanPath), {
            loading: t('admin.scan.scanning'),
            success: (data) => t('admin.scanComplete') + ` (${data.scan.imported}/${data.scan.total})`
        });
        setScanning(false);
    };

    const handleReset = () => {
        showConfirm({
            message: t('admin.danger.confirm1'),
            danger: true,
            confirmText: t('common.confirm'),
            cancelText: t('common.cancel'),
            onConfirm: () => {
                showConfirm({
                    message: t('admin.danger.confirm2'),
                    danger: true,
                    confirmText: t('common.confirm'),
                    cancelText: t('common.cancel'),
                    onConfirm: async () => {
                        setResetting(true);
                        await notify.promise(Api.resetDatabase(), {
                            loading: t('admin.danger.resetting'),
                            success: t('admin.danger.success'),
                            onSuccess: () => {
                                useAuthStore.getState().logout();
                                navigate('/');
                            }
                        });
                        setResetting(false);
                    }
                });
            }
        });
    };

    const handleFileHashSearch = async () => {
        const hash = fileHashQuery.trim();
        if (!hash) return;
        setFileHashSearching(true);
        setFileHashResult(null);
        try {
            const data = await Api.findMediaByHash(hash);
            if (data.items.length > 0) {
                setFileHashResult({ found: true, media: { id: data.items[0].id, title: data.items[0].title } });
            } else {
                setFileHashResult({ found: false });
            }
        } catch (err) {
            notify.error(err);
            setFileHashResult({ found: false });
        } finally {
            setFileHashSearching(false);
        }
    };

    return (
        <AdminGuard>
            <div>
                <div className="page-header">
                    <h1>{t('admin.title')}</h1>
                </div>

                {/* 管理导航卡片 */}
                <div className="grid grid-3 section-card">
                    <Link
                        to="/admin/tags"
                        className="card link-card"
                        onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--primary)')}
                        onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border)')}
                    >
                        <div className="card-header">
                            <h2>{t('admin.tags.title')}</h2>
                        </div>
                        <p className="card-desc">{t('admin.tags.manageHint')}</p>
                    </Link>

                    <Link
                        to="/admin/authors"
                        className="card link-card"
                        onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--primary)')}
                        onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border)')}
                    >
                        <div className="card-header">
                            <h2>{t('admin.authors.title')}</h2>
                        </div>
                        <p className="card-desc">{t('admin.authors.manageHint')}</p>
                    </Link>

                    <Link
                        to="/admin/users"
                        className="card link-card"
                        onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--primary)')}
                        onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border)')}
                    >
                        <div className="card-header">
                            <h2>{t('admin.users.title')}</h2>
                        </div>
                        <p className="card-desc">{t('admin.users.manageHint')}</p>
                    </Link>
                </div>

                {/* 目录扫描 */}
                <div className="card section-card">
                    <div className="card-header">
                        <h2>{t('admin.scan.title')}</h2>
                    </div>
                    <p className="text-secondary mb-16">{t('admin.scan.hint')}</p>
                    <div className="admin-inline-form mb-16">
                        <input className="form-input flex-1" value={scanPath} onChange={(e) => setScanPath(e.target.value)} placeholder={t('admin.scan.placeholder')} />
                        <button className="btn btn-primary" onClick={runScan} disabled={scanning}>
                            {scanning ? t('admin.scan.scanning') : t('admin.scan.btn')}
                        </button>
                    </div>
                </div>

                {/* 文件 Hash 搜索 */}
                <div className="card section-card">
                    <div className="card-header">
                        <h2>{t('admin.fileHash.title')}</h2>
                    </div>
                    <p className="text-secondary mb-16">{t('admin.fileHash.hint')}</p>
                    <div className="admin-inline-form mb-16">
                        <input
                            className="form-input flex-1"
                            value={fileHashQuery}
                            onChange={(e) => setFileHashQuery(e.target.value)}
                            placeholder={t('admin.fileHash.placeholder')}
                            onKeyDown={(e) => e.key === 'Enter' && handleFileHashSearch()}
                        />
                        <button className="btn btn-primary" onClick={handleFileHashSearch} disabled={fileHashSearching}>
                            {fileHashSearching ? '...' : t('common.search')}
                        </button>
                    </div>
                    {fileHashResult !== null && (
                        <p style={{ margin: 0, color: fileHashResult.found ? 'var(--success)' : 'var(--danger)' }}>
                            {fileHashResult.found ? (
                                <span>
                                    {t('admin.fileHash.found')}{' '}
                                    <Link to={`/view/${fileHashResult.media!.id}`} className="link-card-inline">
                                        {fileHashResult.media!.title}
                                    </Link>
                                </span>
                            ) : (
                                t('admin.fileHash.notFound')
                            )}
                        </p>
                    )}
                </div>

                {/* 按路径前缀批量删除 */}
                <div className="card section-card">
                    <div className="card-header">
                        <h2>按目录前缀批量删除/清理</h2>
                    </div>
                    <p className="text-secondary mb-16">
                        输入目录路径前缀（如 <code>/media/test</code> 或 <code>/app/uploads</code>），检索已导入的文件并进行勾选，物理删除媒体及其磁盘文件。
                    </p>
                    <div className="admin-inline-form mb-16">
                        <input
                            className="form-input flex-1"
                            value={batchDelPath}
                            onChange={(e) => setBatchDelPath(e.target.value)}
                            placeholder="输入要清理的容器内目录前缀，例如 /media"
                            onKeyDown={(e) => e.key === 'Enter' && searchMediaByPath()}
                        />
                        <button className="btn btn-primary" onClick={searchMediaByPath} disabled={searchingBatch}>
                            {searchingBatch ? '搜索中...' : '搜索匹配文件'}
                        </button>
                    </div>

                    {batchMediaList.length > 0 && (
                        <div style={{ marginTop: '16px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                                <div>
                                    已选择 {selectedIds.size} / {batchMediaList.length} 个媒体文件
                                </div>
                                <div className="flex-gap-8">
                                    <button className="btn" onClick={toggleSelectAll}>
                                        {selectedIds.size === batchMediaList.length ? '取消全选' : '全选'}
                                    </button>
                                    <button className="btn btn-danger" onClick={handleBatchDelete} disabled={batchDeleting || selectedIds.size === 0}>
                                        {batchDeleting ? '删除中...' : '删除选中媒体'}
                                    </button>
                                </div>
                            </div>
                            
                            <div style={{
                                maxHeight: '300px',
                                overflowY: 'auto',
                                border: '1px solid var(--border)',
                                borderRadius: '4px',
                                padding: '12px',
                                background: 'var(--bg-secondary)'
                            }}>
                                {batchMediaList.map(item => (
                                    <label key={item.id} style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        padding: '6px 0',
                                        cursor: 'pointer',
                                        borderBottom: '1px solid var(--border-light)'
                                    }}>
                                        <input
                                            type="checkbox"
                                            checked={selectedIds.has(item.id)}
                                            onChange={() => toggleSelect(item.id)}
                                            style={{ marginRight: '10px' }}
                                        />
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontWeight: '500' }}>{item.title}</div>
                                            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', wordBreak: 'break-all' }}>{item.filePath}</div>
                                        </div>
                                    </label>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                {/* 危险操作 */}
                <div className="card card-danger">
                    <div className="card-header">
                        <h2 className="text-danger">{t('admin.danger.title')}</h2>
                    </div>
                    <p className="text-secondary mb-16">{t('admin.danger.hint')}</p>
                    <button className="btn btn-danger" onClick={handleReset} disabled={resetting}>
                        {resetting ? t('admin.danger.resetting') : t('admin.danger.btn')}
                    </button>
                </div>
            </div>
        </AdminGuard>
    );
}
