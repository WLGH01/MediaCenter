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
    val fileSize: Long? = 0L,
    val duration: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val visibility: String? = null,
    val createdAt: String? = null,
    val authorName: String? = null,
    val uploaderName: String? = null,
    val author: Author? = null,
    val tags: List<Tag>? = emptyList()
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
}

data class Author(
    val id: String,
    val name: String,
    val avatarUrl: String? = null
)

data class Tag(
    val id: String,
    val name: String,
    val color: String? = null
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

data class StreamTokenResponse(
    val token: String,
    val expiresAt: String
)
