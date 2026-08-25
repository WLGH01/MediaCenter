import { Router } from 'express';
import {
    listCollections,
    createCollection,
    updateCollection,
    deleteCollection,
    listCollectionMedia,
    addMediaToCollection,
    removeMediaFromCollection
} from '../controllers/collectionController';
import { authenticate, requireAuth } from '../middleware/auth';

const router = Router();

// 全部收藏夹接口需要登录
router.use(authenticate, requireAuth);

// 收藏夹 CRUD
router.get('/', listCollections);
router.post('/', createCollection);
router.put('/:id', updateCollection);
router.delete('/:id', deleteCollection);

// 收藏夹内媒体
router.get('/:id/media', listCollectionMedia);
router.post('/:id/media', addMediaToCollection);
router.delete('/:id/media/:mediaId', removeMediaFromCollection);

export default router;
