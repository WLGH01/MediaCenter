package com.mediacenter.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局统一的标签 / 作者 Chip 组件。
 *
 * 视觉规范（所有页面保持一致）：
 * - 胶囊形状（RoundedCornerShape 50）
 * - 标签文本统一渲染为 "# 名称"，作者统一渲染为 "👤 名称"
 * - 未选中：深底 #181825 + 浅字 #CDD6F4 + 边框 #313244
 * - 聚焦：  蓝色边框 #89B4FA + 放大 1.06
 * - 选中：  蓝底 #89B4FA + 深字 #11111B
 * - compact 模式：媒体卡片上的静态小标签（无交互、无聚焦、10sp）
 */

private val ChipBg = Color(0xFF181825)
private val ChipBgFocused = Color(0xFF313244)
private val ChipAccent = Color(0xFF89B4FA)
private val ChipTextPrimary = Color(0xFFCDD6F4)
private val ChipTextMuted = Color(0xFF6C7086)
private val ChipTextOnAccent = Color(0xFF11111B)

/** 标签 chip：文本统一渲染为 "# 名称" */
@Composable
fun TagChip(
    name: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    count: Int? = null,
    compact: Boolean = false,
    showRemove: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    MediaChip(
        prefix = "#",
        label = name,
        modifier = modifier,
        selected = selected,
        count = count,
        compact = compact,
        showRemove = showRemove,
        onClick = onClick
    )
}

/** 作者 chip：文本统一渲染为 "👤 名称" */
@Composable
fun AuthorChip(
    name: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    count: Int? = null,
    showRemove: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    MediaChip(
        prefix = "👤",
        label = name,
        modifier = modifier,
        selected = selected,
        count = count,
        compact = false,
        showRemove = showRemove,
        onClick = onClick
    )
}

@Composable
private fun MediaChip(
    prefix: String,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    count: Int? = null,
    compact: Boolean = false,
    showRemove: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (compact) {
        // 静态紧凑样式：媒体卡片角标
        Row(
            modifier = modifier
                .background(Color(0x6689B4FA), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "$prefix$label",
                color = ChipTextPrimary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        label = "chipScale"
    )
    val interactive = onClick != null

    Row(
        modifier = modifier
            .scale(scale)
            .background(
                color = when {
                    selected -> ChipAccent
                    isFocused -> ChipBgFocused
                    else -> ChipBg
                },
                shape = RoundedCornerShape(50)
            )
            .border(
                width = if (isFocused && !selected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> ChipAccent
                    selected -> Color.Transparent
                    else -> Color(0xFF313244)
                },
                shape = RoundedCornerShape(50)
            )
            .then(
                if (interactive) {
                    Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { onClick?.invoke() }
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$prefix $label",
            color = when {
                selected -> ChipTextOnAccent
                isFocused -> Color.White
                else -> ChipTextPrimary
            },
            fontSize = 13.sp,
            fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$count",
                color = if (selected) Color(0xFF3A3F5C) else ChipTextMuted,
                fontSize = 11.sp
            )
        }
        if (showRemove) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = "✕",
                color = if (selected) ChipTextOnAccent else ChipTextMuted,
                fontSize = 11.sp
            )
        }
    }
}
