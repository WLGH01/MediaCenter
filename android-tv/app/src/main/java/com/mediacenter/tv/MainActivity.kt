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
import com.mediacenter.tv.ui.screens.MediaDetailScreen
import com.mediacenter.tv.ui.screens.SettingsScreen
import com.mediacenter.tv.ui.screens.VideoPlayerScreen
import com.mediacenter.tv.ui.viewmodel.MainViewModel

sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    data class Detail(val media: MediaItem) : Screen()
    data class Player(val media: MediaItem) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
            val backStack = remember { mutableStateListOf<Screen>() }

            fun navigate(screen: Screen) {
                backStack.add(currentScreen)
                currentScreen = screen
            }

            fun goBack() {
                currentScreen =
                    if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else Screen.Home
            }

            fun goHome() {
                backStack.clear()
                currentScreen = Screen.Home
            }

            BackHandler(enabled = currentScreen != Screen.Home) {
                goBack()
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
                                navigate(Screen.Detail(media))
                            },
                            onOpenSettings = {
                                navigate(Screen.Settings)
                            }
                        )
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { goBack() }
                        )
                    }
                    is Screen.Detail -> {
                        MediaDetailScreen(
                            media = screen.media,
                            viewModel = viewModel,
                            onPlay = { media ->
                                navigate(Screen.Player(media))
                            },
                            onBack = { goBack() },
                            onFilterByTag = { tag ->
                                // 跳转首页并按该标签筛选
                                viewModel.setFilterCollection(null)
                                viewModel.setSelectedTags(setOf(tag))
                                goHome()
                            },
                            onFilterByAuthor = { authorId, _ ->
                                viewModel.setFilterCollection(null)
                                viewModel.setFilterAuthor(authorId)
                                goHome()
                            }
                        )
                    }
                    is Screen.Player -> {
                        when (screen.media.mediaType) {
                            "video", "audio" -> {
                                VideoPlayerScreen(
                                    media = screen.media,
                                    onBack = { goBack() }
                                )
                            }
                            "image" -> {
                                ImageViewerScreen(
                                    media = screen.media,
                                    onBack = { goBack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
