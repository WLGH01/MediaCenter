package com.mediacenter.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import coil.request.ImageRequest
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvMediaCard(
    media: MediaItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "scale")
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF89B4FA) else Color.Transparent,
        label = "border"
    )

    val thumbnailUrl = remember(media.id, media.thumbUrl) {
        ApiClient.getThumbnailUrl(context, media.id, media.thumbUrl)
    }

    Column(
        modifier = modifier
            .scale(scale)
            .width(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color(0xFF313244) else Color(0xFF1E1E2E))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) borderColor else Color(0x14FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // ===== 封面区 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = media.title ?: media.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 底部渐变（为时长/标签提供可读性）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0x99000000)
                        )
                    )
            )

            // 类型角标 (左上)
            Text(
                text = when (media.mediaType) {
                    "video" -> "视频"
                    "audio" -> "音频"
                    else -> "图片"
                },
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        color = when (media.mediaType) {
                            "video" -> Color(0xD9E53935)
                            "audio" -> Color(0xD943A047)
                            else -> Color(0xD91E88E5)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // 时长角标 (右下)
            if (media.mediaType != "image" && media.duration != null) {
                val durationSec = media.duration.toInt()
                val h = durationSec / 3600
                val m = (durationSec % 3600) / 60
                val s = durationSec % 60
                val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)

                Text(
                    text = timeStr,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xB3000000), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // 标签预览 (左下，最多 2 个) —— 统一 compact TagChip
            val previewTags = media.tags?.take(2)
            if (!previewTags.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    previewTags.forEach { tag ->
                        TagChip(
                            name = tag.name,
                            compact = true
                        )
                    }
                }
            }
        }

        // ===== 信息区 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = media.displayTitle,
                color = if (isFocused) Color.White else Color(0xFFCDD6F4),
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            (media.displayAuthorName ?: media.uploaderName)?.let { name ->
                Text(
                    text = name,
                    color = Color(0xFFA6ADC8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
