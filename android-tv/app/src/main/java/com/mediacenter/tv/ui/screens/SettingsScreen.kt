package com.mediacenter.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediacenter.tv.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentUrl by viewModel.serverUrl.collectAsState()
    var inputUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var isFieldFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11111B))
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚙ 服务器地址设置",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "请填写 MediaCenter 后端服务地址（如 http://192.168.1.100:3000）",
            color = Color(0xFFA6ADC8),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .width(480.dp)
                .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp))
                .border(
                    width = if (isFieldFocused) 2.dp else 1.dp,
                    color = if (isFieldFocused) Color(0xFF6466F1) else Color(0xFF313244),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFieldFocused = it.isFocused }
                    .focusable()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    viewModel.updateServerUrl(inputUrl)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6466F1))
            ) {
                Text("保存并返回", color = Color.White)
            }

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
            ) {
                Text("取消", color = Color.White)
            }
        }
    }
}
