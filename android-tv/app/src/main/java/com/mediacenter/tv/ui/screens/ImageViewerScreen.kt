package com.mediacenter.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem

@Composable
fun ImageViewerScreen(
    media: MediaItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 优先使用列表/详情接口返回的签名 URL；没有则实时换取
    var imageUrl by remember(media.id) {
        mutableStateOf(ApiClient.resolveUrl(context, media.streamUrl))
    }

    LaunchedEffect(media.id) {
        if (imageUrl == null) {
            try {
                val api = ApiClient.getApi(context)
                val res = api.getStreamToken(media.id)
                if (res.isSuccessful) {
                    imageUrl = ApiClient.resolveUrl(context, res.body()?.streamUrl)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = media.title ?: media.originalName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(text = "图片加载失败", color = Color(0xFFF38BA8), fontSize = 15.sp)
        }

        Text(
            text = media.displayTitle,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .background(Color(0x80000000))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
