package com.mediacenter.tv.data.api

import com.mediacenter.tv.data.model.*
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
        @Query("limit") limit: Int = 30,
        @Query("type") type: String? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String = "createdAt",
        @Query("order") order: String = "desc"
    ): Response<MediaListResponse>

    @GET("api/media/{id}")
    suspend fun getMediaDetail(
        @Path("id") id: String
    ): Response<MediaItem>

    @GET("api/stream/{id}/token")
    suspend fun getStreamToken(
        @Path("id") id: String
    ): Response<StreamTokenResponse>
}
