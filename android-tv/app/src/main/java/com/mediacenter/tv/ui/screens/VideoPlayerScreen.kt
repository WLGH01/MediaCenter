package com.mediacenter.tv.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem as MediaModel

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    media: MediaModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isPreparing by remember { mutableStateOf(true) }

    val streamUrl = remember(media.id) {
        ApiClient.getStreamUrl(context, media.id)
    }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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

        // 视频标题浮层
        Text(
            text = media.title ?: media.originalName,
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .background(Color(0x80000000))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
