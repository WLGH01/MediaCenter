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
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    media: MediaModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var streamToken by remember { mutableStateOf<String?>(null) }
    var isLoadingToken by remember { mutableStateOf(true) }

    LaunchedEffect(media.id) {
        try {
            val api = ApiClient.getApi(context)
            val response = api.getStreamToken(media.id)
            if (response.isSuccessful && response.body() != null) {
                streamToken = response.body()!!.token
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingToken = false
        }
    }

    val streamUrl = remember(media.id, streamToken) {
        ApiClient.getStreamUrl(context, media.id, streamToken)
    }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(streamUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoadingToken) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF89B4FA))
            }
        } else {
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
        }

        // 视频标题浮层
        Text(
            text = media.displayTitle,
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
