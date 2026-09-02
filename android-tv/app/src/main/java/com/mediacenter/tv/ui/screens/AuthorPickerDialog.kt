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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediacenter.tv.data.model.Author
import com.mediacenter.tv.ui.viewmodel.MainViewModel

/**
 * 作者选择对话框（TV 遥控器友好）：
 * - 网格展示全部作者（按媒体数排序）并显示计数
 * - 支持按名称搜索（含别名匹配，客户端过滤）
 * - 单选：点击作者立即应用筛选并关闭
 */
@Composable
fun AuthorPickerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val authors by viewModel.authors.collectAsState()
    val selectedAuthorId by viewModel.selectedAuthorId.collectAsState()

    var search by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val filtered = remember(authors, search) {
        val q = search.trim()
        if (q.isEmpty()) authors
        else authors.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.altNames?.any { alt -> alt.contains(q, ignoreCase = true) } == true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .background(Color(0xFF1E1E2E), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF45475A), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "作者筛选",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filtered.size} 位作者",
                        color = Color(0xFF6C7086),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("搜索作者…", color = Color(0xFF6C7086), fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFCDD6F4),
                        focusedBorderColor = Color(0xFF89B4FA),
                        unfocusedBorderColor = Color(0xFF45475A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(Modifier.height(12.dp))

                if (filtered.isEmpty()) {
                    Text(
                        text = if (authors.isEmpty()) "服务器还没有作者信息" else "没有匹配的作者",
                        color = Color(0xFFA6ADC8),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(280.dp)
                    ) {
                        items(filtered, key = { it.id }) { author ->
                            val checked = selectedAuthorId == author.id
                            AuthorToggleChip(
                                author = author,
                                checked = checked,
                                onClick = {
                                    // 单选：再次点击当前选中项则取消筛选
                                    viewModel.setFilterAuthor(
                                        if (checked) null else author.id
                                    )
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    Button(
                        onClick = {
                            viewModel.setFilterAuthor(null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                    ) {
                        Text("清除", color = Color(0xFFBAC2DE))
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA))
                    ) {
                        Text("关闭", color = Color(0xFF11111B))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorToggleChip(
    author: Author,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (checked) {
                Text(
                    text = "✓ ",
                    color = Color(0xFF11111B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "👤 ${author.name}",
                color = if (checked) Color(0xFF11111B) else Color(0xFFCDD6F4),
                fontSize = 13.sp,
                fontWeight = if (checked || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${author.mediaCount ?: 0}",
                color = if (checked) Color(0xFF3A3F5C) else Color(0xFF6C7086),
                fontSize = 11.sp
            )
        }
    }
}
