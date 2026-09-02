package com.mediacenter.tv.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val username: String,
    val role: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class MediaItem(
    val id: String,
    val title: String? = null,
    val originalName: String? = null,
    val description: String? = null,
    val fileSize: Long? = 0L,
    val duration: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val visibility: String? = null,
    val createdAt: String? = null,
    val authorId: String? = null,
    val authorName: String? = null,
    val uploaderName: String? = null,
    val author: Author? = null,
    val tags: List<Tag>? = emptyList(),
    // 后端在列表/详情/收藏夹接口中返回的短时签名 URL（相对路径，需拼接服务器地址）
    val streamUrl: String? = null,
    val thumbUrl: String? = null,
    // 收藏夹接口返回的收藏时间
    val favoritedAt: String? = null
) {
    val displayTitle: String
        get() = title?.ifBlank { null } ?: originalName?.ifBlank { null } ?: "未命名媒体"

    val mediaType: String
        get() = when {
            mimeType?.startsWith("video/") == true -> "video"
            mimeType?.startsWith("audio/") == true -> "audio"
            mimeType?.startsWith("image/") == true -> "image"
            else -> "video"
        }

    /** 展示用作者信息（详情接口返回 author 对象，列表接口返回平铺字段） */
    val displayAuthorName: String?
        get() = author?.name ?: authorName

    val displayAuthorId: String?
        get() = author?.id ?: authorId
}

data class Author(
    val id: String,
    val name: String,
    val avatarUrl: String? = null
)

data class AuthorListResponse(
    val authors: List<Author> = emptyList()
)

data class Tag(
    val id: String,
    val name: String,
    val altNames: List<String>? = emptyList(),
    val color: String? = null,
    val mediaCount: Int? = 0
)

data class TagListResponse(
    val tags: List<Tag> = emptyList()
)

data class Pagination(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Long = 0L,
    val totalPages: Int = 1
)

data class MediaListResponse(
    val items: List<MediaItem> = emptyList(),
    val pagination: Pagination? = null
)

/**
 * 后端 GET /api/media/{id}/stream-token 返回的是完整的签名 URL（相对路径）：
 * { "streamUrl": "/api/stream/{id}?expires=...&uid=...&purpose=stream&role=...&sig=...", "downloadUrl": "..." }
 */
data class StreamTokenResponse(
    val streamUrl: String? = null,
    val downloadUrl: String? = null
)

data class CollectionItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val mediaCount: Int? = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** 后端 GET /api/collections 返回 { collections: [...] } */
data class CollectionListResponse(
    val collections: List<CollectionItem> = emptyList()
)

data class MediaDetailResponse(
    val media: MediaItem
)

/** 查询媒体被收藏在哪些收藏夹：GET /api/media/{id}/collections → { collectionIds: [...] } */
data class MediaCollectionsResponse(
    val collectionIds: List<String> = emptyList()
)

/** 添加媒体到收藏夹的请求体：POST /api/collections/{id}/media → { mediaIds: [...] } */
data class AddToCollectionRequest(
    val mediaIds: List<String>
)

/** 创建收藏夹请求体：POST /api/collections → { name, description } */
data class CreateCollectionRequest(
    val name: String,
    val description: String? = null
)

data class CreateCollectionResponse(
    val collection: CollectionItem? = null
)
