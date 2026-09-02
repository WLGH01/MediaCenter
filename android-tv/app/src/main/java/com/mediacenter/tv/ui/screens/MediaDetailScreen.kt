package com.mediacenter.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import com.mediacenter.tv.ui.components.TagChip
import com.mediacenter.tv.ui.components.AuthorChip
import com.mediacenter.tv.ui.viewmodel.MainViewModel

/**
 * 媒体详情页（二级菜单）：
 * - 封面背景 + 海报式布局
 * - 作品简介 / 标签 / 作者，标签与作者可聚焦并跳转首页筛选
 * - 播放 / 收藏 / 返回
 *
 * 视觉规范：所有标签/作者 chip 统一使用 MediaChips 组件，胶囊形配色一致。
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
                detail = d.copy(
                    streamUrl = d.streamUrl ?: media.streamUrl,
                    thumbUrl = d.thumbUrl ?: media.thumbUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        try { playFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D15))) {
        // ===== 背景：封面大图 + 暗化渐变 =====
        val bgUrl = remember(detail.id, detail.thumbUrl) {
            ApiClient.getThumbnailUrl(context, detail.id, detail.thumbUrl)
        }
        if (bgUrl != null) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.30f,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 双层渐变压暗
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xF00D0D15), Color(0xB30D0D15), Color(0x660D0D15))
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color(0x330D0D15), Color(0xF00D0D15))
                )
            )
        )

        // ===== 前景内容 =====
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            // ===== 左侧海报 =====
            Column(verticalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF181825))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = bgUrl,
                        contentDescription = detail.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 底部渐变 + 时长（委托属性需局部变量）
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                when (detail.mediaType) {
                                    "video" -> Color(0xCCE53935)
                                    "audio" -> Color(0xCC43A047)
                                    else -> Color(0xCC1E88E5)
                                },
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = when (detail.mediaType) {
                                "video" -> "视频"
                                "audio" -> "音频"
                                else -> "图片"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ===== 右侧信息 =====
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 标题
                Text(
                    text = detail.displayTitle,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp
                )

                // 元信息行
                val metaItems = buildList {
                    detail.createdAt?.take(10)?.let { add(it) }
                    detail.fileSize?.takeIf { it > 0 }?.let { add(formatFileSize(it)) }
                    if (detail.width != null && detail.height != null) add("${detail.width}×${detail.height}")
                    detail.uploaderName?.let { add("上传者 $it") }
                }
                if (metaItems.isNotEmpty()) {
                    Text(
                        text = metaItems.joinToString("  ·  "),
                        color = Color(0xFFA6ADC8),
                        fontSize = 13.sp
                    )
                }

                // 作者（可跳转筛选）—— 统一 AuthorChip
                val authorName = detail.displayAuthorName
                val authorId = detail.displayAuthorId
                if (authorName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("作者", color = Color(0xFF6C7086), fontSize = 12.sp)
                        if (authorId != null) {
                            AuthorChip(
                                name = authorName,
                                onClick = { onFilterByAuthor(authorId, authorName) }
                            )
                        } else {
                            Text(authorName, color = Color(0xFFCDD6F4), fontSize = 14.sp)
                        }
                    }
                }

                // 标签（可跳转筛选）—— 统一 TagChip + FlowRow 自动换行
                val mediaTags = detail.tags.orEmpty()
                if (mediaTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "标签",
                            color = Color(0xFF6C7086),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        mediaTags.forEach { tag ->
                            TagChip(
                                name = tag.name,
                                onClick = { onFilterByTag(tag.name) }
                            )
                        }
                    }
                }

                // 简介（容器化）
                val desc = detail.description?.trim()
                if (!desc.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1411111B))
                            .border(1.dp, Color(0xFF1E1E2E), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("简介", color = Color(0xFF6C7086), fontSize = 12.sp)
                        Text(
                            text = desc,
                            color = Color(0xFFBAC2DE),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onPlay(detail) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF89B4FA)
                        ),
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .height(48.dp)
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

        // 返回按钮（可聚焦）
        var backFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .scale(if (backFocused) 1.05f else 1f)
                .background(
                    if (backFocused) Color(0x2689B4FA) else Color(0x1411111B),
                    RoundedCornerShape(8.dp)
                )
                .onFocusChanged { backFocused = it.isFocused }
                .focusable()
                .clickable { onBack() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ 返回", color = Color(0xFFA6ADC8), fontSize = 14.sp)
        }
    }

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
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "btnScale")
    Text(
        text = text,
        color = if (isFocused) Color(0xFF89B4FA) else Color(0xFFBAC2DE),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .scale(scale)
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
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
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
