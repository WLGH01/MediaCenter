package com.mediacenter.tv.data.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "mediacenter_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_AUTH_TOKEN = "auth_token"

    private var currentRetrofit: Retrofit? = null
    private var currentApi: MediaCenterApi? = null
    private var currentBaseUrl: String = ""

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getServerUrl(context: Context): String {
        return getPrefs(context).getString(KEY_SERVER_URL, "http://192.168.1.100:3000/") ?: "http://192.168.1.100:3000/"
    }

    fun saveServerUrl(context: Context, url: String) {
        var formattedUrl = url.trim()
        if (!formattedUrl.endsWith("/")) {
            formattedUrl += "/"
        }
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "http://$formattedUrl"
        }
        getPrefs(context).edit().putString(KEY_SERVER_URL, formattedUrl).apply()
        currentRetrofit = null
        currentApi = null
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, null)
    }

    fun saveToken(context: Context, token: String?) {
        getPrefs(context).edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getApi(context: Context): MediaCenterApi {
        val baseUrl = getServerUrl(context)
        if (currentApi != null && currentBaseUrl == baseUrl) {
            return currentApi!!
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val token = getToken(context)
                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        currentBaseUrl = baseUrl
        currentRetrofit = retrofit
        currentApi = retrofit.create(MediaCenterApi::class.java)

        return currentApi!!
    }

    fun getThumbnailUrl(context: Context, mediaId: String): String {
        val baseUrl = getServerUrl(context).removeSuffix("/")
        val token = getToken(context)
        return if (!token.isNullOrEmpty()) {
            "$baseUrl/api/media/$mediaId/thumbnail?token=$token"
        } else {
            "$baseUrl/api/media/$mediaId/thumbnail"
        }
    }

    fun getStreamUrl(context: Context, mediaId: String, streamToken: String? = null): String {
        val baseUrl = getServerUrl(context).removeSuffix("/")
        return if (!streamToken.isNullOrEmpty()) {
            "$baseUrl/api/stream/$mediaId?token=$streamToken"
        } else {
            "$baseUrl/api/stream/$mediaId"
        }
    }
}
