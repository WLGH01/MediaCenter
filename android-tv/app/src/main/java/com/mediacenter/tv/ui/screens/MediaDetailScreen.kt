package com.mediacenter.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem
import com.mediacenter.tv.ui.viewmodel.MainViewModel

/**
 * 媒体详情页（二级菜单）：
 * - 封面背景 + 海报式布局
 * - 作品简介 / 标签 / 作者，标签与作者可聚焦并跳转首页筛选
 * - 播放 / 收藏 / 返回
 */
@Composable
fun MediaDetailScreen(
    media: MediaItem,
    viewModel: MainViewModel,
    onPlay: (MediaItem) -> Unit,
    onBack: () -> Unit,
    onFilterByTag: (String) -> Unit,
    onFilterByAuthor: (String, String) -> Unit
) {
    val context = LocalContext.current
    var detail by remember(media.id) { mutableStateOf(media) }
    val playFocusRequester = remember { FocusRequester() }
    var showCollection by remember { mutableStateOf(false) }

    // 拉取完整详情（简介 / 分辨率等列表接口不返回的字段）
    LaunchedEffect(media.id) {
        try {
            val api = ApiClient.getApi(context)
            val res = api.getMediaDetail(media.id)
            if (res.isSuccessful && res.body()?.media != null) {
                val d = res.body()!!.media
                // 合并：列表已带签名 URL（较新），详情补充文案字段
                detail = d.copy(
                    streamUrl = d.streamUrl ?: media.streamUrl,
                    thumbUrl = d.thumbUrl ?: media.thumbUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D15))) {
        // 背景：封面大图 + 暗化渐变（低版本 TV 兼容，不用 blur）
        val bgUrl = remember(detail.id, detail.thumbUrl) {
            ApiClient.getThumbnailUrl(context, detail.id, detail.thumbUrl)
        }
        if (bgUrl != null) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 双层渐变压暗，保证文字可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xE60D0D15), Color(0xB30D0D15), Color(0x660D0D15)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x330D0D15), Color(0xE60D0D15))
                    )
                )
        )

        // 前景内容
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // ===== 左侧海报 =====
            Column(verticalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .width(330.dp)
                        .height(206.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF181825))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                ) {
                    AsyncImage(
                        model = bgUrl,
                        contentDescription = detail.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 底部渐变 + 时长（先拷贝到局部变量，委托属性无法智能转换）
                    val posterDuration = detail.duration
                    if (detail.mediaType != "image" && posterDuration != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color(0xB3000000)
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = formatDuration(posterDuration),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // 类型角标
                    Text(
                        text = when (detail.mediaType) {
                            "video" -> "视频"
                            "audio" -> "音频"
                            else -> "图片"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(
                                when (detail.mediaType) {
                                    "video" -> Color(0xCCE53935)
                                    "audio" -> Color(0xCC43A047)
                                    else -> Color(0xCC1E88E5)
                                },
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // ===== 右侧信息 =====
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 标题
                Text(
                    text = detail.displayTitle,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp
                )

                // 元信息行
                val metaItems = buildList {
                    detail.createdAt?.take(10)?.let { add(it) }
                    detail.fileSize?.let { add(formatFileSize(it)) }
                    if (detail.width != null && detail.height != null) add("${detail.width}×${detail.height}")
                    detail.uploaderName?.let { add("上传者 $it") }
                }
                if (metaItems.isNotEmpty()) {
                    Text(
                        text = metaItems.joinToString("  ·  "),
                        color = Color(0xFFA6ADC8),
                        fontSize = 14.sp
                    )
                }

                // 作者（可跳转筛选）
                detail.displayAuthorName?.let { authorName ->
                    val authorId = detail.displayAuthorId
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("作者", color = Color(0xFF6C7086), fontSize = 13.sp)
                        Spacer(Modifier.width(10.dp))
                        if (authorId != null) {
                            DetailChip(text = "👤 $authorName") {
                                onFilterByAuthor(authorId, authorName)
                            }
                        } else {
                            Text(authorName, color = Color(0xFFCDD6F4), fontSize = 15.sp)
                        }
                    }
                }

                // 标签（可跳转筛选）——委托属性无法智能转换，先拷贝到局部变量
                val mediaTags = detail.tags.orEmpty()
                if (mediaTags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("标签", color = Color(0xFF6C7086), fontSize = 13.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mediaTags.forEach { tag ->
                                DetailChip(text = "# ${tag.name}") {
                                    onFilterByTag(tag.name)
                                }
                            }
                        }
                    }
                }

                // 简介
                val desc = detail.description?.trim()
                if (!desc.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("简介", color = Color(0xFF6C7086), fontSize = 13.sp)
                        Text(
                            text = desc,
                            color = Color(0xFFBAC2DE),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onPlay(detail) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA)),
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (detail.mediaType == "image") "查看图片" else "▶  播放",
                            color = Color(0xFF11111B),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedActionButton(text = "⭐ 收藏") {
                        showCollection = true
                    }
                }
            }
        }

        // 顶部返回提示
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ 返回", color = Color(0xFFA6ADC8), fontSize = 14.sp)
        }
    }

    // 收藏对话框复用（点击收藏按钮时打开）
    if (showCollection) {
        CollectionDialog(
            media = detail,
            viewModel = viewModel,
            onDismiss = { showCollection = false }
        )
    }
}

@Composable
private fun OutlinedActionButton(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (isFocused) Color(0xFF89B4FA) else Color(0xFFBAC2DE),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .scale(if (isFocused) 1.05f else 1f)
            .background(
                if (isFocused) Color(0x2689B4FA) else Color(0x1411111B),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.5.dp,
                if (isFocused) Color(0xFF89B4FA) else Color(0xFF45475A),
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 26.dp, vertical = 13.dp)
    )
}

/** 可聚焦的标签/作者 chip：点击跳转筛选 */
@Composable
private fun DetailChip(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .scale(if (isFocused) 1.06f else 1f)
            .background(
                if (isFocused) Color(0xFF89B4FA) else Color(0xFF181825),
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (isFocused) Color(0xFF89B4FA) else Color(0xFF313244),
                RoundedCornerShape(50)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused) Color(0xFF11111B) else Color(0xFFCDD6F4),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 秒 → mm:ss / h:mm:ss */
internal fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

/** 字节 → 可读大小 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes >= 1 shl 30 -> String.format("%.1f GB", bytes / 1073741824.0)
    bytes >= 1 shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
    bytes >= 1 shl 10 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
