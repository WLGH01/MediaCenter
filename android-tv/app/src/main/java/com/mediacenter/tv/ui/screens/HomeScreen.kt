package com.mediacenter.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediacenter.tv.data.model.MediaItem
import com.mediacenter.tv.ui.components.TvMediaCard
import com.mediacenter.tv.ui.viewmodel.MainViewModel
import com.mediacenter.tv.ui.viewmodel.MediaUiState

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onMediaSelect: (MediaItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11111B))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // 顶部 Header 与 导航/分类过滤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MediaCenter TV",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            // 分类标签组 (全部 / 视频 / 音频 / 图片)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterTab(
                    title = "全部",
                    isSelected = selectedType == null,
                    onClick = { viewModel.setFilterType(null) }
                )
                FilterTab(
                    title = "视频",
                    isSelected = selectedType == "video",
                    onClick = { viewModel.setFilterType("video") }
                )
                FilterTab(
                    title = "音频",
                    isSelected = selectedType == "audio",
                    onClick = { viewModel.setFilterType("audio") }
                )
                FilterTab(
                    title = "图片",
                    isSelected = selectedType == "image",
                    onClick = { viewModel.setFilterType("image") }
                )
                FilterTab(
                    title = "⚙ 设置",
                    isSelected = false,
                    onClick = onOpenSettings
                )
            }
        }

        // 内容展示区
        when (val state = uiState) {
            is MediaUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6466F1))
                }
            }
            is MediaUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = state.message, color = Color(0xFFF38BA8), fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("重试")
                        }
                    }
                }
            }
            is MediaUiState.Success -> {
                if (state.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "暂无媒体文件", color = Color(0xFFA6ADC8), fontSize = 16.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            TvMediaCard(
                                media = item,
                                onClick = { onMediaSelect(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isFocused) 1.1f else 1.0f, label = "scale")

    Box(
        modifier = Modifier
            .scale(scale)
            .background(
                color = when {
                    isSelected -> Color(0xFF6466F1)
                    isFocused -> Color(0xFF313244)
                    else -> Color(0xFF1E1E2E)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused && !isSelected) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF89B4FA) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected || isFocused) Color.White else Color(0xFFBAC2DE),
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}
