package com.mediacenter.tv.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem as MediaModel
import xyz.doikki.videoplayer.player.VideoView
import xyz.doikki.videoplayer.exo.ExoMediaPlayerFactory
import xyz.doikki.videoplayer.ui.StandardVideoController

/**
 * 采用 DKVideoPlayer 重构的 VideoPlayerScreen：
 * 1. 集成 DKVideoPlayer 框架 (ExoMediaPlayer 内核)，自带精美原生控制器与播放体验。
 * 2. 完美适配遥控器方向键调节进度条、OK 键播放/暂停及后退逻辑。
 * 3. 完美结合 Compose 与 Activity 生命周期管理。
 */
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

    val currentUrl = streamUrl!!
    var playerViewRef by remember { mutableStateOf<VideoView<*>?>(null) }

    // 生命周期监听，确保在应用后台或返回时及时暂停释放
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> playerViewRef?.pause()
                    Lifecycle.Event.ON_RESUME -> playerViewRef?.resume()
                    Lifecycle.Event.ON_DESTROY -> playerViewRef?.release()
                    else -> {}
                }
            } catch (_: Exception) {}
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                playerViewRef?.release()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                VideoView<xyz.doikki.videoplayer.player.AbstractPlayer>(ctx).apply {
                    setPlayerFactory(ExoMediaPlayerFactory.create())
                    setUrl(currentUrl)
                    val controller = StandardVideoController(ctx).apply {
                        setTitle(media.title)
                    }
                    setVideoController(controller)
                    start()
                    playerViewRef = this
                }
            },
            update = { view ->
                playerViewRef = view
            },
            modifier = Modifier.fillMaxSize()
        )

        // 错误重试层
        if (loadError != null && !isLoading) {
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
