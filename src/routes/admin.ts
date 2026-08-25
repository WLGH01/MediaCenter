import { Router } from 'express';
import { scanMediaFiles } from '../controllers/scanController';
import { resetDatabase, deleteUser, toggleBan, listUsers, updateUserRole, createUser, batchDeleteMedia, listMediaByPath, listApiTokens, createApiToken, deleteApiToken } from '../controllers/adminController';
import { authenticate, requireAuth, requireAdmin } from '../middleware/auth';

const router = Router();

// 所有管理接口都需要管理员权限
router.use(authenticate, requireAuth, requireAdmin);

// 获取用户列表
router.get('/users', listUsers);

// 管理员创建用户
router.post('/users', createUser);

// 更新用户角色
router.put('/users/:id/role', updateUserRole);

// 删除用户
router.delete('/users/:id', deleteUser);

// 切换封禁状态
router.post('/users/:id/toggle-ban', toggleBan);

// 扫描目录导入媒体文件
router.post('/scan', scanMediaFiles);

// 按路径前缀搜索媒体文件（供批量删除勾选）
router.get('/media-by-path', listMediaByPath);

// 批量删除媒体文件接口
router.post('/batch-delete-media', batchDeleteMedia);

// API 令牌管理（管理面板生成静态 API 令牌）
router.get('/api-tokens', listApiTokens);
router.post('/api-tokens', createApiToken);
router.delete('/api-tokens/:id', deleteApiToken);

// 重置数据库（清空 + 重建 + 初始化）
router.post('/reset-db', resetDatabase);

export default router;
