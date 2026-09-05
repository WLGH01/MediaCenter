package com.mediacenter.tv.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem as MediaModel
import kotlinx.coroutines.delay

/**
 * 深度优化版 VideoPlayerScreen（TV 原生遥控器全适配）：
 * 1. 遥控器 D-Pad 原生拦截：
 *    - 左右键：快进/快退 10 秒并自动唤出进度条 overlay。
 *    - OK / Enter 键：播放/暂停无缝切换。
 *    - 发生错误时自动放行焦点给错误提示框，确保“重试”与“返回”按钮可用遥控器自由切换选中。
 * 2. 4 秒无人操作自动隐去 overlay 控制条。
 * 3. 稳健的生命周期保护与网络断流自动续签重连机制。
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    media: MediaModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    BackHandler { onBack() }

    // 获取服务器签名的流地址
    LaunchedEffect(media.id, retryKey) {
        isLoading = true
        loadError = null
        streamUrl = null
        try {
            val api = ApiClient.getApi(appContext)
            val response = api.getStreamToken(media.id)
            if (response.isSuccessful && response.body()?.streamUrl != null) {
                streamUrl = ApiClient.resolveUrl(appContext, response.body()!!.streamUrl)
            } else {
                loadError = when (response.code()) {
                    401 -> "登录已过期，请在设置中重新登录"
                    403 -> "无权播放该媒体"
                    404 -> "媒体文件不存在或已被删除"
                    else -> "获取播放地址失败 (${response.code()})"
                }
            }
        } catch (e: Exception) {
            loadError = "网络连接异常: ${e.localizedMessage ?: "无法连接到服务器"}"
        } finally {
            isLoading = false
        }
    }

    if (isLoading || streamUrl == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF89B4FA))
            } else if (loadError != null) {
                val errorRetryFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    try { errorRetryFocusRequester.requestFocus() } catch (_: Exception) {}
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError!!, color = Color(0xFFF38BA8), fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { retryKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244)),
                        modifier = Modifier.focusRequester(errorRetryFocusRequester)
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

    val currentUrl = streamUrl!!

    // 创建 ExoPlayer
    val exoPlayer = remember(media.id, currentUrl, retryKey) {
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent("MediaCenterTV/1.0")

        ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)))
                prepare()
                playWhenReady = true
            }
    }

    // 播放器状态
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isEnded by remember { mutableStateOf(false) }

    // 遥控器控制 Overlay 显示控制
    var controlsVisible by remember { mutableStateOf(true) }
    var hideTimerKey by remember { mutableIntStateOf(0) }
    val playerFocusRequester = remember { FocusRequester() }

    // 4 秒无遥控按键自动隐藏控制器
    LaunchedEffect(controlsVisible, hideTimerKey, isPlaying) {
        if (controlsVisible && isPlaying && !isEnded) {
            delay(4000)
            controlsVisible = false
        }
    }

    fun showControls() {
        controlsVisible = true
        hideTimerKey++
    }

    // 轮询更新播放进度
    LaunchedEffect(exoPlayer) {
        while (true) {
            try {
                isPlaying = exoPlayer.isPlaying
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            } catch (_: Exception) {}
            delay(500)
        }
    }

    // Compose 与 Activity 生命周期结合
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                if (event == Lifecycle.Event.ON_STOP) {
                    exoPlayer.pause()
                }
            } catch (_: Exception) {}
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 播放器状态及错误监听
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isEnded = true
                    controlsVisible = true
                } else if (playbackState == Player.STATE_READY) {
                    isEnded = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                ) {
                    loadError = "播放连接断开，正在尝试重连…"
                    retryKey++
                } else {
                    loadError = "播放发生错误: ${error.localizedMessage ?: error.errorCodeName}"
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

    // 页面加载及恢复时获取遥控器键盘焦点
    LaunchedEffect(loadError, isLoading) {
        if (loadError == null && !isLoading) {
            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                // 如果当前展示错误层，放行键盘事件给错误层按钮
                if (loadError != null) return@onKeyEvent false
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                
                showControls()
                when (keyEvent.key) {
                    // OK / Enter 键切换播放/暂停
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (isEnded) {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        } else if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                        true
                    }
                    // 左方向键：后退 10 秒
                    Key.DirectionLeft, Key.MediaRewind -> {
                        val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                        true
                    }
                    // 右方向键：快进 10 秒
                    Key.DirectionRight, Key.MediaFastForward -> {
                        val maxPos = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                        val target = (exoPlayer.currentPosition + 10_000).coerceAtMost(maxPos)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                        true
                    }
                    // 上/下方向键：单纯唤出进度条控制器
                    Key.DirectionUp, Key.DirectionDown -> {
                        true
                    }
                    else -> false
                }
            }
    ) {
        // 视频画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 自定义 TV 遥控控制 Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xCC000000),
                            0.2f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color(0xE6000000)
                        )
                    )
            ) {
                // 顶部标题
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = media.displayTitle,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 底部控制器
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 36.dp)
                ) {
                    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
                    val bufferProgress = if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs else 0f

                    // 进度条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LinearProgressIndicator(
                            progress = { bufferProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Color(0x66FFFFFF),
                            trackColor = Color(0x33FFFFFF)
                        )
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Color(0xFF89B4FA),
                            trackColor = Color.Transparent
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0.01f, 1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(14.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 底部控制行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isEnded -> "✓ 已播放完毕"
                                isPlaying -> "▶ 播放中"
                                else -> "❚❚ 已暂停"
                            },
                            color = Color(0xFF89B4FA),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "${formatTvTime(currentPositionMs)} / ${formatTvTime(durationMs)}",
                            color = Color(0xFFCDD6F4),
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "◄/► 快进10s  ·  OK ${if (isEnded) "重新播放" else "暂停/播放"}  ·  返回 退出",
                            color = Color(0xFFA6ADC8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 错误及重试覆盖层
        if (loadError != null && !isLoading) {
            val popupRetryFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try { popupRetryFocusRequester.requestFocus() } catch (_: Exception) {}
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)),
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244)),
                        modifier = Modifier.focusRequester(popupRetryFocusRequester)
                    ) { Text("重试", color = Color.White) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181825))
                    ) { Text("返回", color = Color.White) }
                }
            }
        }
    }
}

/** 毫秒格式化为 mm:ss / hh:mm:ss */
private fun formatTvTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
