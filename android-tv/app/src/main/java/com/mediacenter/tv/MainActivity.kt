package com.mediacenter.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mediacenter.tv.data.model.MediaItem
import com.mediacenter.tv.ui.screens.HomeScreen
import com.mediacenter.tv.ui.screens.ImageViewerScreen
import com.mediacenter.tv.ui.screens.SettingsScreen
import com.mediacenter.tv.ui.screens.VideoPlayerScreen
import com.mediacenter.tv.ui.viewmodel.MainViewModel

sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    data class Player(val media: MediaItem) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

            BackHandler(enabled = currentScreen != Screen.Home) {
                currentScreen = Screen.Home
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF11111B))
            ) {
                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        HomeScreen(
                            viewModel = viewModel,
                            onMediaSelect = { media ->
                                currentScreen = Screen.Player(media)
                            },
                            onOpenSettings = {
                                currentScreen = Screen.Settings
                            }
                        )
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    is Screen.Player -> {
                        when (screen.media.type) {
                            "video", "audio" -> {
                                VideoPlayerScreen(
                                    media = screen.media,
                                    onBack = { currentScreen = Screen.Home }
                                )
                            }
                            "image" -> {
                                ImageViewerScreen(
                                    media = screen.media,
                                    onBack = { currentScreen = Screen.Home }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
