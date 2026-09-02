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
    val title: String?,
    val originalName: String,
    val type: String, // "video", "audio", "image"
    val size: Long,
    val duration: Double?,
    val width: Int?,
    val height: Int?,
    val mimeType: String,
    val visibility: String,
    val createdAt: String,
    val author: Author?,
    val tags: List<Tag>? = emptyList()
)

data class Author(
    val id: String,
    val name: String,
    val avatarUrl: String?
)

data class Tag(
    val id: String,
    val name: String,
    val color: String?
)

data class MediaListResponse(
    val items: List<MediaItem>,
    val total: Long,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

data class StreamTokenResponse(
    val token: String,
    val expiresAt: String
)
