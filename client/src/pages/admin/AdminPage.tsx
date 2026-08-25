import { useState, useEffect } from 'react';
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

    // 实验性功能开关：默认关闭（隐藏），打开后显示批量删除等实验功能
    const [experimentalEnabled, setExperimentalEnabled] = useState<boolean>(() => {
        try {
            return localStorage.getItem('mc-experimental') === '1';
        } catch {
            return false;
        }
    });

    const toggleExperimental = () => {
        setExperimentalEnabled(prev => {
            const next = !prev;
            try {
                localStorage.setItem('mc-experimental', next ? '1' : '0');
            } catch { /* ignore */ }
            return next;
        });
    };

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

    // ===== API 令牌管理 =====
    const [apiTokens, setApiTokens] = useState<{ id: string; name: string; description: string; role: string; createdAt: string; lastUsedAt: string | null }[]>([]);
    const [apiTokenLoading, setApiTokenLoading] = useState(false);
    const [newTokenName, setNewTokenName] = useState('');
    const [newTokenDesc, setNewTokenDesc] = useState('');
    const [generatingToken, setGeneratingToken] = useState(false);
    const [freshToken, setFreshToken] = useState<string | null>(null);

    const loadApiTokens = async () => {
        setApiTokenLoading(true);
        try {
            const data = await Api.listApiTokens();
            setApiTokens(data.tokens || []);
        } catch (err) {
            notify.error(err);
        } finally {
            setApiTokenLoading(false);
        }
    };

    // 进入页面时加载一次
    useEffect(() => { loadApiTokens(); }, []);

    const handleGenerateToken = async () => {
        const name = newTokenName.trim();
        if (!name) {
            notify.error('请输入令牌名称');
            return;
        }
        setGeneratingToken(true);
        try {
            const data = await Api.createApiToken({ name, description: newTokenDesc.trim() });
            setFreshToken(data.token);
            setNewTokenName('');
            setNewTokenDesc('');
            await loadApiTokens();
        } catch (err) {
            notify.error(err);
        } finally {
            setGeneratingToken(false);
        }
    };

    const handleDeleteToken = (id: string, name: string) => {
        showConfirm({
            message: `确定删除 API 令牌 "${name}" 吗？删除后立即失效，使用该令牌的调用将返回 401。`,
            danger: true,
            confirmText: t('common.confirm'),
            cancelText: t('common.cancel'),
            onConfirm: async () => {
                try {
                    await notify.promise(Api.deleteApiToken(id), {
                        loading: '删除中...',
                        success: 'API 令牌已删除'
                    });
                    await loadApiTokens();
                } catch (err) {
                    notify.error(err);
                }
            }
        });
    };

    const copyToken = async (token: string) => {
        // navigator.clipboard 仅在 HTTPS/localhost 可用；HTTP 下回退 execCommand
        try {
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(token);
                notify.success('已复制到剪贴板');
                return;
            }
            throw new Error('clipboard unavailable');
        } catch {
            try {
                // 回退：临时 textarea + execCommand('copy')，HTTP 环境可用
                const textarea = document.createElement('textarea');
                textarea.value = token;
                textarea.style.position = 'fixed';
                textarea.style.opacity = '0';
                textarea.setAttribute('readonly', '');
                document.body.appendChild(textarea);
                textarea.select();
                textarea.setSelectionRange(0, token.length);
                const ok = document.execCommand('copy');
                document.body.removeChild(textarea);
                if (ok) {
                    notify.success('已复制到剪贴板');
                } else {
                    notify.error('复制失败，请手动选中复制');
                }
            } catch {
                notify.error('复制失败，请手动选中复制');
            }
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

                {/* API 令牌管理 */}
                <div className="card section-card">
                    <div className="card-header">
                        <h2>API 令牌管理</h2>
                    </div>
                    <p className="text-secondary mb-16">
                        生成静态 API 令牌，用于外部脚本/工具调用接口（<code>Authorization: Bearer &lt;token&gt;</code>），无需登录、永不过期（除非手动删除）。
                    </p>

                    {/* 生成新令牌 */}
                    <div className="admin-inline-form mb-16">
                        <input
                            className="form-input flex-1"
                            value={newTokenName}
                            onChange={(e) => setNewTokenName(e.target.value)}
                            placeholder="令牌名称（必填），如：n8n 推送"
                        />
                        <button className="btn btn-primary" onClick={handleGenerateToken} disabled={generatingToken}>
                            {generatingToken ? '生成中...' : '生成令牌'}
                        </button>
                    </div>
                    <input
                        className="form-input mb-16"
                        style={{ width: '100%' }}
                        value={newTokenDesc}
                        onChange={(e) => setNewTokenDesc(e.target.value)}
                        placeholder="备注说明（可选）"
                    />

                    {/* 新生成的令牌（仅显示一次） */}
                    {freshToken && (
                        <div style={{
                            marginBottom: '16px',
                            padding: '12px',
                            border: '1px solid var(--success)',
                            borderRadius: '4px',
                            background: 'var(--bg-secondary)'
                        }}>
                            <div style={{ fontWeight: '600', marginBottom: '6px', color: 'var(--success)' }}>
                                ⚠️ 令牌已生成，请立即复制保存（关闭后不再显示）：
                            </div>
                            <code style={{ wordBreak: 'break-all', userSelect: 'all' }}>{freshToken}</code>
                            <div style={{ marginTop: '8px' }}>
                                <button className="btn btn-primary" onClick={() => copyToken(freshToken)}>复制令牌</button>
                                <button className="btn" style={{ marginLeft: '8px' }} onClick={() => setFreshToken(null)}>关闭</button>
                            </div>
                        </div>
                    )}

                    {/* 令牌列表 */}
                    {apiTokenLoading ? (
                        <p className="muted">加载中...</p>
                    ) : apiTokens.length === 0 ? (
                        <p className="muted">暂无 API 令牌，在上方输入名称生成一个。</p>
                    ) : (
                        <div style={{
                            border: '1px solid var(--border)',
                            borderRadius: '4px',
                            overflow: 'hidden'
                        }}>
                            {apiTokens.map((tk) => (
                                <div key={tk.id} style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    padding: '10px 12px',
                                    borderBottom: '1px solid var(--border-light)',
                                    background: 'var(--bg-secondary)'
                                }}>
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div style={{ fontWeight: '500' }}>{tk.name}</div>
                                        <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                                            {tk.description || '无备注'}
                                            {tk.lastUsedAt ? ` · 最近使用: ${new Date(tk.lastUsedAt).toLocaleString()}` : ' · 未使用'}
                                        </div>
                                    </div>
                                    <button className="btn btn-danger" onClick={() => handleDeleteToken(tk.id, tk.name)}>删除</button>
                                </div>
                            ))}
                        </div>
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
