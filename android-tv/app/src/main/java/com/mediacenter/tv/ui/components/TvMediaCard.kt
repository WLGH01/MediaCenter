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
    val borderColor by animateColorAsState(targetValue = if (isFocused) Color(0xFF89B4FA) else Color.Transparent, label = "border")

    val thumbnailUrl = remember(media.id, media.thumbUrl) {
        ApiClient.getThumbnailUrl(context, media.id, media.thumbUrl)
    }

    Column(
        modifier = modifier
            .scale(scale)
            .width(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color(0xFF313244) else Color(0xFF1E1E2E))
            .border(2.5.dp, borderColor, RoundedCornerShape(14.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .background(Color(0xFF181825))
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

            // 类型标签 (Video/Audio/Image)
            Text(
                text = media.mediaType.uppercase(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        color = when (media.mediaType) {
                            "video" -> Color(0xE6E53935)
                            "audio" -> Color(0xE643A047)
                            else -> Color(0xE61E88E5)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // 视频时长显示
            if (media.mediaType == "video" && media.duration != null) {
                val durationSec = media.duration.toInt()
                val min = durationSec / 60
                val sec = durationSec % 60
                val timeStr = String.format("%d:%02d", min, sec)

                Text(
                    text = timeStr,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // 标题信息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = media.displayTitle,
                color = if (isFocused) Color.White else Color(0xFFCDD6F4),
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            media.author?.let { author ->
                Text(
                    text = author.name,
                    color = Color(0xFFA6ADC8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } ?: run {
                val fallbackAuthor = media.authorName ?: media.uploaderName
                fallbackAuthor?.let { name ->
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
}
