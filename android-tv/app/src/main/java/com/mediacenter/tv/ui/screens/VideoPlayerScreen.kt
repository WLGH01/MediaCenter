package com.mediacenter.tv.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.runBlocking

/**
 * 签名 URL 续签器：服务端签名 URL 默认 3 分钟过期。
 * ExoPlayer 每次打开新的 HTTP 连接（Range 请求 / seek / 断点续传）都会先经过
 * resolveDataSpec；此处检测 expires 参数，若临近过期则同步换取新的签名 URL，
 * 从而支持长时间不间断播放。
 */
@OptIn(UnstableApi::class)
private class StreamUrlRefresher(
    private val api: MediaCenterApi,
    private val context: android.content.Context,
    private val mediaId: String
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        val uri = dataSpec.uri
        // 非签名 URL（无 expires 参数）直接放行
        val expires = uri.getQueryParameter("expires")?.toLongOrNull() ?: return dataSpec
        val now = System.currentTimeMillis() / 1000
        if (expires - now > 60) return dataSpec

        val refreshedUrl = runBlocking {
            try {
                val res = api.getStreamToken(mediaId)
                if (res.isSuccessful) {
                    ApiClient.resolveUrl(context, res.body()?.streamUrl)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
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

    // 首次进入：向后端换取签名流地址（携带登录态，服务端按用户权限签发）
    LaunchedEffect(media.id, retryKey) {
        isLoading = true
        loadError = null
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

    if (!isLoading && streamUrl != null) {
        val currentUrl = streamUrl!!
        val exoPlayer = remember(media.id, currentUrl, retryKey) {
            val api = ApiClient.getApi(context)
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent("MediaCenterTV/1.0")

            val resolvingFactory = ResolvingDataSource.Factory(
                httpFactory,
                StreamUrlRefresher(api, context, media.id)
            )

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)))
                    prepare()
                    playWhenReady = true
                }
        }

        // 播放错误监听：给出可见的错误反馈而不是黑屏
        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    loadError = when {
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_FORBIDDEN_REQUEST ->
                            "播放被拒绝（鉴权失败或签名过期），请重试"
                        else -> "播放失败: ${error.localizedMessage ?: error.errorCodeName}"
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (loadError == null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            controllerAutoShow = true
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            requestFocus()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = loadError!!, color = Color(0xFFF38BA8), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            loadError = null
                            retryKey++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                    ) {
                        Text("重试", color = Color.White)
                    }
                }
            }

            // 视频标题浮层
            Text(
                text = media.displayTitle,
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(Color(0x80000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    } else {
        // 加载中 / 出错时的全屏占位
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Color(0xFF89B4FA))
                loadError != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = loadError!!, color = Color(0xFFF38BA8), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { retryKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                    ) {
                        Text("重试", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181825))
                    ) {
                        Text("返回", color = Color(0xFFBAC2DE))
                    }
                }
            }
        }
    }
}
