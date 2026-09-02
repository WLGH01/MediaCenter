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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediacenter.tv.data.model.Tag
import com.mediacenter.tv.ui.viewmodel.MainViewModel

/**
 * 标签选择对话框（TV 遥控器友好）：
 * - 网格展示全部标签（按媒体数排序）并显示计数
 * - 支持按名称搜索
 * - 多选标签后以 AND 表达式（A&B）联合筛选
 */
@Composable
fun TagPickerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val tags by viewModel.tags.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()

    var search by remember { mutableStateOf("") }
    var pendingSelection by remember(selectedTags) { mutableStateOf(selectedTags) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val filtered = remember(tags, search) {
        val q = search.trim()
        if (q.isEmpty()) tags
        else tags.filter { it.name.contains(q, ignoreCase = true) }
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
                        text = "标签筛选（可多选）",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (pendingSelection.isEmpty()) "未选择"
                        else pendingSelection.sorted().joinToString(" & "),
                        color = Color(0xFF89B4FA),
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("搜索标签…", color = Color(0xFF6C7086), fontSize = 13.sp) },
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
                        text = if (tags.isEmpty()) "服务器还没有任何标签" else "没有匹配的标签",
                        color = Color(0xFFA6ADC8),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(260.dp)
                    ) {
                        items(filtered, key = { it.id }) { tag ->
                            val checked = pendingSelection.contains(tag.name)
                            TagToggleChip(
                                tag = tag,
                                checked = checked,
                                onClick = {
                                    val next = pendingSelection.toMutableSet()
                                    if (!next.add(tag.name)) next.remove(tag.name)
                                    pendingSelection = next
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
                            viewModel.clearTags()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
                    ) {
                        Text("清除", color = Color(0xFFBAC2DE))
                    }
                    Button(
                        onClick = {
                            viewModel.setSelectedTags(pendingSelection)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA))
                    ) {
                        Text("完成", color = Color(0xFF11111B))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagToggleChip(
    tag: Tag,
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
            Text(
                text = if (checked) "✓ " else "",
                color = Color(0xFF11111B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "#${tag.name}",
                color = if (checked) Color(0xFF11111B) else Color(0xFFCDD6F4),
                fontSize = 13.sp,
                fontWeight = if (checked || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${tag.mediaCount ?: 0}",
                color = if (checked) Color(0xFF3A3F5C) else Color(0xFF6C7086),
                fontSize = 11.sp
            )
        }
    }
}
