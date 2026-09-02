package com.mediacenter.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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

    val authors by viewModel.authors.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val collections by viewModel.collections.collectAsState()

    val selectedAuthorId by viewModel.selectedAuthorId.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // 对话框状态
    var showTagPicker by remember { mutableStateOf(false) }
    var showAuthorPicker by remember { mutableStateOf(false) }
    var collectionTarget by remember { mutableStateOf<MediaItem?>(null) }

    // 当前已选作者名（用于入口按钮显示）
    val selectedAuthorName = remember(selectedAuthorId, authors) {
        authors.find { it.id == selectedAuthorId }?.name
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11111B))
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        // 顶部 Header 与 导航/分类过滤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MediaCenter TV",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            // 基础分类标签组 (全部 / 视频 / 音频 / 图片 / 设置)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterTab(
                    title = "全部",
                    isSelected = selectedType == null && selectedCollectionId == null,
                    onClick = {
                        viewModel.setFilterCollection(null)
                        viewModel.setFilterType(null)
                    }
                )
                FilterTab(
                    title = "视频",
                    isSelected = selectedType == "video" && selectedCollectionId == null,
                    onClick = {
                        viewModel.setFilterCollection(null)
                        viewModel.setFilterType("video")
                    }
                )
                FilterTab(
                    title = "音频",
                    isSelected = selectedType == "audio" && selectedCollectionId == null,
                    onClick = {
                        viewModel.setFilterCollection(null)
                        viewModel.setFilterType("audio")
                    }
                )
                FilterTab(
                    title = "图片",
                    isSelected = selectedType == "image" && selectedCollectionId == null,
                    onClick = {
                        viewModel.setFilterCollection(null)
                        viewModel.setFilterType("image")
                    }
                )
                FilterTab(
                    title = "⚙ 设置",
                    isSelected = false,
                    onClick = onOpenSettings
                )
            }
        }

        // 次级长条横向选择器 (排序 / 标签 / 收藏库 / 作者)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // 排序按钮
            item {
                val sortText = when (sortBy) {
                    "createdAt" -> if (sortOrder == "desc") "排序: 最新↓" else "排序: 最早↑"
                    "title" -> if (sortOrder == "desc") "排序: 标题↓" else "排序: 标题↑"
                    "fileSize" -> if (sortOrder == "desc") "排序: 大小↓" else "排序: 大小↑"
                    else -> "排序"
                }
                FilterChip(
                    title = sortText,
                    isSelected = true,
                    activeColor = Color(0xFF45475A),
                    onClick = { viewModel.setSort(sortBy) }
                )
            }

            // 标签筛选入口（打开选择对话框）
            item {
                val tagText = when {
                    selectedTags.isEmpty() -> "🏷 标签筛选"
                    selectedTags.size == 1 -> "🏷 ${selectedTags.first()}"
                    else -> "🏷 ${selectedTags.size} 个标签"
                }
                FilterChip(
                    title = tagText,
                    isSelected = selectedTags.isNotEmpty(),
                    onClick = { showTagPicker = true }
                )
            }

            // 已选标签快捷移除
            items(selectedTags.sorted(), key = { "sel_tag_$it" }) { tagName ->
                FilterChip(
                    title = "✕ #$tagName",
                    isSelected = false,
                    activeColor = Color(0xFFF38BA8),
                    onClick = { viewModel.toggleTag(tagName) }
                )
            }

            // 作者筛选入口（打开选择对话框，避免作者过多时横向滚动过长）
            item {
                FilterChip(
                    title = if (selectedAuthorName != null) "👤 $selectedAuthorName"
                    else "👤 作者筛选",
                    isSelected = selectedAuthorId != null,
                    onClick = { showAuthorPicker = true }
                )
            }

            // 已选作者快捷移除
            if (selectedAuthorName != null) {
                item {
                    FilterChip(
                        title = "✕",
                        isSelected = false,
                        activeColor = Color(0xFFF38BA8),
                        onClick = { viewModel.setFilterAuthor(null) }
                    )
                }
            }

            // 收藏夹
            if (isLoggedIn && collections.isNotEmpty()) {
                items(collections, key = { "coll_${it.id}" }) { coll ->
                    FilterChip(
                        title = "⭐ ${coll.name} (${coll.mediaCount ?: 0})",
                        isSelected = selectedCollectionId == coll.id,
                        onClick = {
                            viewModel.setFilterCollection(
                                if (selectedCollectionId == coll.id) null else coll.id
                            )
                        }
                    )
                }
            }

            // 操作提示（仅提示，不可聚焦）
            item {
                Text(
                    text = "OK 查看详情 · 长按 OK 收藏",
                    color = Color(0x996C7086),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
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
                    CircularProgressIndicator(color = Color(0xFF89B4FA))
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
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                        ) {
                            Text("重试", color = Color.White)
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
                        Text(text = "暂无相关媒体文件", color = Color(0xFFA6ADC8), fontSize = 16.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 190.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            TvMediaCard(
                                media = item,
                                onClick = { onMediaSelect(item) },
                                onLongClick = {
                                    if (isLoggedIn) collectionTarget = item
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 标签选择对话框
    if (showTagPicker) {
        TagPickerDialog(
            viewModel = viewModel,
            onDismiss = { showTagPicker = false }
        )
    }

    // 作者选择对话框
    if (showAuthorPicker) {
        AuthorPickerDialog(
            viewModel = viewModel,
            onDismiss = { showAuthorPicker = false }
        )
    }

    // 收藏管理对话框（长按媒体卡片打开）
    collectionTarget?.let { target ->
        CollectionDialog(
            media = target,
            viewModel = viewModel,
            onDismiss = { collectionTarget = null }
        )
    }
}

@Composable
fun FilterChip(
    title: String,
    isSelected: Boolean,
    activeColor: Color = Color(0xFF89B4FA),
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "scale")

    Box(
        modifier = Modifier
            .scale(scale)
            .background(
                color = when {
                    isSelected -> activeColor
                    isFocused -> Color(0xFF313244)
                    else -> Color(0xFF181825)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = if (isFocused && !isSelected) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF89B4FA) else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color(0xFF11111B) else if (isFocused) Color.White else Color(0xFFBAC2DE),
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
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
