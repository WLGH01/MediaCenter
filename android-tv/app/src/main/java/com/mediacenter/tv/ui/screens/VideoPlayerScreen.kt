package com.mediacenter.tv.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.api.MediaCenterApi
import com.mediacenter.tv.data.model.MediaItem as MediaModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 签名 URL 续签器：签名 URL 默认 3 分钟过期，
 * ExoPlayer 每次开新连接时检查并自动续签。
 */
@OptIn(UnstableApi::class)
private class StreamUrlRefresher(
    private val api: MediaCenterApi,
    private val context: android.content.Context,
    private val mediaId: String
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        val uri = dataSpec.uri
        val expires = uri.getQueryParameter("expires")?.toLongOrNull() ?: return dataSpec
        val now = System.currentTimeMillis() / 1000
        if (expires - now > 60) return dataSpec

        val refreshedUrl = runBlocking {
            try {
                withTimeoutOrNull(10_000L) {
                    val res = api.getStreamToken(mediaId)
                    if (res.isSuccessful) ApiClient.resolveUrl(context, res.body()?.streamUrl)
                    else null
                }
            } catch (e: Exception) { null }
        } ?: return dataSpec

        return dataSpec.buildUpon().setUri(Uri.parse(refreshedUrl)).build()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    media: MediaModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    BackHandler { onBack() }

    // 获取签名流地址
    LaunchedEffect(media.id, retryKey) {
        isLoading = true
        loadError = null
        streamUrl = null
        try {
            val api = ApiClient.getApi(context)
            val response = api.getStreamToken(media.id)
            if (response.isSuccessful && response.body()?.streamUrl != null) {
                streamUrl = ApiClient.resolveUrl(context, response.body()!!.streamUrl)
            } else {
                loadError = when (response.code()) {
                    401 -> "登录已过期，请到设置中重新登录"
                    403 -> "没有权限播放该媒体"
                    404 -> "媒体不存在或已被删除"
                    else -> "获取播放地址失败 (${response.code()})"
                }
            }
        } catch (e: Exception) {
            loadError = "网络错误: ${e.localizedMessage ?: "无法连接服务器"}"
        } finally {
            isLoading = false
        }
    }

    if (isLoading || streamUrl == null) {
        // 加载中 / 出错占位
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF89B4FA))
            } else if (loadError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError!!, color = Color(0xFFF38BA8), fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { retryKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                    ) { Text("重试", color = Color.White) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181825))
                    ) { Text("返回", color = Color(0xFFBAC2DE)) }
                }
            }
        }
        return
    }

    // ===== 播放器（使用 PlayerView 自带控制器） =====
    val currentUrl = streamUrl!!
    val appContext = context.applicationContext

    val exoPlayer = remember(media.id, currentUrl, retryKey) {
        val api = ApiClient.getApi(context)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent("MediaCenterTV/1.0")

        val resolvingFactory = ResolvingDataSource.Factory(
            httpFactory,
            StreamUrlRefresher(api, appContext, media.id)
        )

        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)))
                prepare()
                playWhenReady = true
            }
    }

    // 错误监听 + 释放
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                loadError = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "播放被拒绝（鉴权失败或签名过期），请重试"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        "网络连接失败，请检查服务器"
                    else -> "播放失败: ${error.localizedMessage ?: error.errorCodeName}"
                }
                try { exoPlayer.pause() } catch (_: Exception) {}
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            try {
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    // PlayerView 自带控制器：D-Pad 左右 seek、OK 播放/暂停、返回退出
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    player = exoPlayer
                    // 默认显示控制器，几秒后自动隐藏
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // 错误覆盖层
        if (loadError != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xE6000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError!!, color = Color(0xFFF38BA8), fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            loadError = null
                            retryKey++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                    ) { Text("重试", color = Color.White) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181825))
                    ) { Text("返回", color = Color(0xFFBAC2DE)) }
                }
            }
        }
    }
}
