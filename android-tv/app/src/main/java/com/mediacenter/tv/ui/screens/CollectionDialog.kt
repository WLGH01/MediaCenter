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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediacenter.tv.data.model.MediaItem
import com.mediacenter.tv.ui.viewmodel.MainViewModel

/**
 * 收藏管理对话框：
 * - 展示该媒体当前所在的收藏夹（勾选状态）
 * - 勾选/取消勾选即加入/移出收藏夹
 * - 支持新建收藏夹
 */
@Composable
fun CollectionDialog(
    media: MediaItem,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val collections by viewModel.collections.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    var collectionIds by remember(media.id) { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingState by remember { mutableStateOf(true) }
    var newCollectionName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(media.id) {
        viewModel.loadMediaCollections(media.id) { ids ->
            collectionIds = ids
            isLoadingState = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .background(Color(0xFF1E1E2E), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF45475A), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "收藏「${media.displayTitle}」",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isLoggedIn) "勾选收藏夹即可收藏，取消勾选则移出"
                    else "请先在设置中登录后再使用收藏功能",
                    color = if (isLoggedIn) Color(0xFFA6ADC8) else Color(0xFFF9E2AF),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))

                if (!isLoggedIn) {
                    // 未登录：只显示提示
                } else if (isLoadingState) {
                    Text("加载中…", color = Color(0xFFA6ADC8), fontSize = 13.sp)
                } else if (collections.isEmpty()) {
                    Text(
                        "还没有收藏夹，输入名称创建一个吧",
                        color = Color(0xFFA6ADC8),
                        fontSize = 13.sp
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(180.dp)
                    ) {
                        items(collections, key = { it.id }) { coll ->
                            val checked = collectionIds.contains(coll.id)
                            CollectionToggleChip(
                                title = "${if (checked) "★" else "☆"} ${coll.name}",
                                count = coll.mediaCount ?: 0,
                                checked = checked,
                                onClick = {
                                    if (checked) {
                                        viewModel.removeFromCollection(coll.id, media.id) { ok, err ->
                                            if (ok) {
                                                collectionIds = collectionIds - coll.id
                                                message = "已从「${coll.name}」移出"
                                            } else message = err
                                        }
                                    } else {
                                        viewModel.addToCollection(coll.id, media.id) { ok, err ->
                                            if (ok) {
                                                collectionIds = collectionIds + coll.id
                                                message = "已收藏到「${coll.name}」"
                                            } else message = err
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                if (isLoggedIn) {
                    Spacer(Modifier.height(14.dp))
                    // 新建收藏夹
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCollectionName,
                            onValueChange = { newCollectionName = it },
                            placeholder = { Text("新收藏夹名称", color = Color(0xFF6C7086), fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color(0xFFCDD6F4),
                                focusedBorderColor = Color(0xFF89B4FA),
                                unfocusedBorderColor = Color(0xFF45475A)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .focusRequester(focusRequester)
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                viewModel.createCollection(newCollectionName) { ok, err ->
                                    if (ok) {
                                        message = "已创建「${newCollectionName.trim()}」"
                                        newCollectionName = ""
                                    } else message = err
                                }
                            },
                            enabled = newCollectionName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF89B4FA),
                                disabledContainerColor = Color(0xFF313244)
                            )
                        ) {
                            Text("创建", color = Color(0xFF11111B))
                        }
                    }
                }

                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFA6E3A1), fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                    ) {
                        Text("关闭", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionToggleChip(
    title: String,
    count: Int,
    checked: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .background(
                color = when {
                    checked -> Color(0xFF89B4FA)
                    isFocused -> Color(0xFF313244)
                    else -> Color(0xFF181825)
                },
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = if (isFocused && !checked) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF89B4FA) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (checked) Color(0xFF11111B) else Color(0xFFCDD6F4),
                fontSize = 13.sp,
                fontWeight = if (checked || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                text = "$count 个媒体",
                color = if (checked) Color(0xFF3A3F5C) else Color(0xFF6C7086),
                fontSize = 11.sp
            )
        }
    }
}
