package com.mediacenter.tv.data.api

import com.mediacenter.tv.data.model.Author
import com.mediacenter.tv.data.model.AuthorListResponse
import com.mediacenter.tv.data.model.CollectionItem
import com.mediacenter.tv.data.model.LoginRequest
import com.mediacenter.tv.data.model.LoginResponse
import com.mediacenter.tv.data.model.MediaDetailResponse
import com.mediacenter.tv.data.model.MediaItem
import com.mediacenter.tv.data.model.MediaListResponse
import com.mediacenter.tv.data.model.StreamTokenResponse
import com.mediacenter.tv.data.model.Tag
import com.mediacenter.tv.data.model.TagListResponse
import retrofit2.Response
import retrofit2.http.*

interface MediaCenterApi {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @GET("api/media")
    suspend fun getMediaList(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 40,
        @Query("type") type: String? = null,
        @Query("search") search: String? = null,
        @Query("authorId") authorId: String? = null,
        @Query("tags") tags: String? = null,
        @Query("sortBy") sortBy: String = "createdAt",
        @Query("sortOrder") sortOrder: String = "desc"
    ): Response<MediaListResponse>

    @GET("api/media/{id}")
    suspend fun getMediaDetail(
        @Path("id") id: String
    ): Response<MediaDetailResponse>

    @GET("api/media/{id}/stream-token")
    suspend fun getStreamToken(
        @Path("id") id: String
    ): Response<StreamTokenResponse>

    @GET("api/authors")
    suspend fun getAuthors(): Response<AuthorListResponse>

    @GET("api/tags")
    suspend fun getTags(): Response<TagListResponse>

    @GET("api/collections")
    suspend fun getCollections(): Response<List<CollectionItem>>

    @GET("api/collections/{id}/media")
    suspend fun getCollectionMedia(
        @Path("id") id: String
    ): Response<List<MediaItem>>

    @POST("api/collections/{id}/media")
    suspend fun addMediaToCollection(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @DELETE("api/collections/{id}/media/{mediaId}")
    suspend fun removeMediaFromCollection(
        @Path("id") id: String,
        @Path("mediaId") mediaId: String
    ): Response<Unit>
}
