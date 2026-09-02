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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem as MediaModel
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import kotlinx.coroutines.delay

/**
 * 使用 GSYVideoPlayer 的播放页：
 * - StandardGSYVideoPlayer 自带 UI 控制器（进度条、播放/暂停按钮）
 * - IJKPlayer 内核（FFmpeg，格式兼容性好，默认内核）
 * - 签名 URL 鉴权：播放前获取签名 URL 直接播放
 * - 返回键退出播放
 *
 * GSYVideoPlayer 内部处理了 D-Pad 按键（左右 seek、OK 播放/暂停）、
 * 生命周期管理、错误提示，不需要我们手动处理。
 */
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

    // ===== GSYVideoPlayer 播放 =====
    val currentUrl = streamUrl!!

    // GSY 播放器实例（remember 确保重组时不重建）
    val gsyPlayer = remember(media.id, currentUrl, retryKey) {
        StandardGSYVideoPlayer(context).apply {
            // setUp(url, cacheWithExternal, title)
            setUp(currentUrl, false, media.displayTitle)
            // 开始播放
            startPlayLogic()
        }
    }

    // 离开播放页时释放资源
    DisposableEffect(gsyPlayer) {
        onDispose {
            try {
                gsyPlayer.release()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { gsyPlayer },
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
