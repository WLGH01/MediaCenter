package com.mediacenter.tv.data.api

import com.mediacenter.tv.data.model.AddToCollectionRequest
import com.mediacenter.tv.data.model.Author
import com.mediacenter.tv.data.model.AuthorListResponse
import com.mediacenter.tv.data.model.CollectionItem
import com.mediacenter.tv.data.model.CollectionListResponse
import com.mediacenter.tv.data.model.CreateCollectionRequest
import com.mediacenter.tv.data.model.CreateCollectionResponse
import com.mediacenter.tv.data.model.LoginRequest
import com.mediacenter.tv.data.model.LoginResponse
import com.mediacenter.tv.data.model.MediaCollectionsResponse
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

    /** 获取流媒体签名 URL（返回 streamUrl/downloadUrl 相对路径） */
    @GET("api/media/{id}/stream-token")
    suspend fun getStreamToken(
        @Path("id") id: String
    ): Response<StreamTokenResponse>

    /** 查询当前用户把该媒体收藏在了哪些收藏夹 */
    @GET("api/media/{id}/collections")
    suspend fun getMediaCollections(
        @Path("id") id: String
    ): Response<MediaCollectionsResponse>

    @GET("api/authors")
    suspend fun getAuthors(
        @Query("limit") limit: Int = 100,
        @Query("sortBy") sortBy: String = "mediaCount",
        @Query("sortOrder") sortOrder: String = "desc"
    ): Response<AuthorListResponse>

    @GET("api/tags")
    suspend fun getTags(
        @Query("limit") limit: Int = 100,
        @Query("sortBy") sortBy: String = "mediaCount",
        @Query("sortOrder") sortOrder: String = "desc"
    ): Response<TagListResponse>

    @GET("api/collections")
    suspend fun getCollections(): Response<CollectionListResponse>

    @POST("api/collections")
    suspend fun createCollection(
        @Body body: CreateCollectionRequest
    ): Response<CreateCollectionResponse>

    @DELETE("api/collections/{id}")
    suspend fun deleteCollection(
        @Path("id") id: String
    ): Response<Unit>

    /** 返回 { items, pagination }（后端为分页结构） */
    @GET("api/collections/{id}/media")
    suspend fun getCollectionMedia(
        @Path("id") id: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("sortBy") sortBy: String = "createdAt",
        @Query("sortOrder") sortOrder: String = "desc"
    ): Response<MediaListResponse>

    /** 后端为批量接口：{ mediaIds: [id] } */
    @POST("api/collections/{id}/media")
    suspend fun addMediaToCollection(
        @Path("id") id: String,
        @Body body: AddToCollectionRequest
    ): Response<Unit>

    @DELETE("api/collections/{id}/media/{mediaId}")
    suspend fun removeMediaFromCollection(
        @Path("id") id: String,
        @Path("mediaId") mediaId: String
    ): Response<Unit>
}
