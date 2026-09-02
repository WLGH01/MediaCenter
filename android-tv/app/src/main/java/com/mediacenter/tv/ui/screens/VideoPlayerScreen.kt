package com.mediacenter.tv.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
 * 签名 URL 续签器：服务端签名 URL 默认 3 分钟过期。
 * ExoPlayer 每次打开新的 HTTP 连接（Range 请求 / seek / 断点续传）都会先经过
 * resolveDataSpec；此处检测 expires 参数，若临近过期则同步换取新的签名 URL，
 * 从而支持长时间不间断播放。
 *
 * 注：resolveDataSpec 在 ExoPlayer 的加载线程（非主线程）调用，runBlocking 安全；
 * 加 10 秒超时防止网络异常时无限占用加载线程。
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
                withTimeoutOrNull(10_000L) {
                    val res = api.getStreamToken(mediaId)
                    if (res.isSuccessful) {
                        ApiClient.resolveUrl(context, res.body()?.streamUrl)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        } ?: return dataSpec

        return dataSpec.buildUpon().setUri(Uri.parse(refreshedUrl)).build()
    }
}

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L

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

        // ===== 播放状态轮询（进度/缓冲/播放态） =====
        // try-catch 防御：player 已 release 而轮询协程尚未取消的极小竞态窗口
        var positionMs by remember { mutableLongStateOf(0L) }
        var durationMs by remember { mutableLongStateOf(0L) }
        var bufferedMs by remember { mutableLongStateOf(0L) }
        var isPlaying by remember { mutableStateOf(true) }
        var isBuffering by remember { mutableStateOf(false) }

        LaunchedEffect(exoPlayer) {
            while (isActive) {
                try {
                    positionMs = exoPlayer.currentPosition
                    durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                    bufferedMs = exoPlayer.bufferedPosition
                    isPlaying = exoPlayer.isPlaying
                    isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
                } catch (e: IllegalStateException) {
                    // player 已释放，停止轮询
                    break
                }
                delay(500)
            }
        }

        // 播放错误监听
        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    loadError = when (error.errorCode) {
                        // Media3 将所有非 2xx HTTP 状态（401/403/404 等）归为 BAD_HTTP_STATUS
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                            "播放被拒绝（鉴权失败或签名过期），请重试"
                        else -> "播放失败: ${error.localizedMessage ?: error.errorCodeName}"
                    }
                    try {
                        exoPlayer.pause()
                    } catch (_: Exception) {
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }

        // ===== 控制层显隐（4 秒无操作自动隐藏） =====
        var controlsVisible by remember { mutableStateOf(true) }
        var interactionTick by remember { mutableIntStateOf(0) }

        LaunchedEffect(interactionTick) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }

        fun togglePlayPause() {
            try {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            } catch (_: Exception) {
            }
        }

        fun seekBy(deltaMs: Long) {
            try {
                val dur = exoPlayer.duration.takeIf { it > 0 } ?: return
                val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
                exoPlayer.seekTo(target)
                positionMs = target
            } catch (_: Exception) {
            }
        }

        fun markInteraction() {
            controlsVisible = true
            interactionTick++
        }

        // 播放页主动抢占焦点，确保遥控按键能到达自绘控制层
        val screenFocusRequester = remember { FocusRequester() }
        LaunchedEffect(exoPlayer) {
            try {
                screenFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // 焦点节点尚未就绪，忽略（用户仍可用方向键移入）
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(screenFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.DirectionLeft, Key.MediaRewind -> {
                            seekBy(-SEEK_STEP_MS); markInteraction(); true
                        }
                        Key.DirectionRight, Key.MediaFastForward -> {
                            seekBy(SEEK_STEP_MS); markInteraction(); true
                        }
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                            if (controlsVisible) togglePlayPause() else markInteraction()
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            markInteraction(); true
                        }
                        else -> false
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false // TV 遥控控制层由 Compose 自绘
                    }
                },
                update = { view ->
                    // 重组时确保 PlayerView 始终绑定当前 player 实例
                    if (view.player !== exoPlayer) view.player = exoPlayer
                },
                onRelease = { view ->
                    view.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            if (loadError != null) {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181825))
                    ) {
                        Text("返回", color = Color(0xFFBAC2DE))
                    }
                }
            }

            // ===== TV 自绘控制层（底部） =====
            AnimatedVisibility(
                visible = controlsVisible && loadError == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x00000000),
                                0.5f to Color(0x99000000),
                                1f to Color(0xE6000000)
                            )
                        )
                        .padding(horizontal = 40.dp, vertical = 22.dp)
                ) {
                    // 标题 + 播放状态
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = media.displayTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (isPlaying) "▶ 播放中" else "❚❚ 已暂停",
                            color = Color(0xFF89B4FA),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // 自绘进度条（轨道 + 缓冲 + 进度 + 游标）
                    if (durationMs > 0) {
                        val progress by animateFloatAsState(
                            targetValue = positionMs.toFloat() / durationMs,
                            label = "progress"
                        )
                        val bufferedProgress by animateFloatAsState(
                            targetValue = bufferedMs.toFloat() / durationMs,
                            label = "buffered"
                        )
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                        ) {
                            val barWidth = maxWidth
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .align(Alignment.CenterStart)
                                    .background(Color(0x33FFFFFF), RoundedCornerShape(3.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .align(Alignment.CenterStart)
                                    .background(Color(0x6689B4FA), RoundedCornerShape(3.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .align(Alignment.CenterStart)
                                    .background(Color(0xFF89B4FA), RoundedCornerShape(3.dp))
                            )
                            // 游标圆点
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = barWidth * progress.coerceIn(0f, 1f) - 7.dp)
                                    .size(14.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 时间 + 操作提示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatDuration(positionMs / 1000.0)} / ${formatDuration(durationMs / 1000.0)}",
                            color = Color(0xFFBAC2DE),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "OK 播放/暂停   ◄ ► 快退/快进 ${SEEK_STEP_MS / 1000} 秒   返回键退出",
                            color = Color(0xFF6C7086),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 缓冲指示
            if (loadError == null && isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF89B4FA))
                }
            }
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
