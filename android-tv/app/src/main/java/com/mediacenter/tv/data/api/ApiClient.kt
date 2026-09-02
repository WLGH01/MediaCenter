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
            .readTimeout(60, TimeUnit.SECONDS)
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

    /**
     * 将后端返回的相对路径签名 URL（如 /api/stream/{id}?expires=...&sig=...）
     * 拼接为完整可访问地址。
     */
    fun resolveUrl(context: Context, relativePath: String?): String? {
        if (relativePath.isNullOrBlank()) return null
        val base = getServerUrl(context).removeSuffix("/")
        return if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            relativePath
        } else {
            "$base${if (relativePath.startsWith("/")) relativePath else "/$relativePath"}"
        }
    }

    /**
     * 获取媒体的缩略图地址：优先使用后端返回的 24 小时签名 URL；
     * 若无签名 URL（老数据），回退到 {base}/api/stream/{id}/thumb —— 此时仅
     * 对访客可见的媒体有效（服务端流接口不解析 query 里的 JWT）。
     */
    fun getThumbnailUrl(context: Context, mediaId: String, signedThumbUrl: String? = null): String? {
        resolveUrl(context, signedThumbUrl)?.let { return it }
        val baseUrl = getServerUrl(context).removeSuffix("/")
        return "$baseUrl/api/stream/$mediaId/thumb"
    }
}
