package com.mediacenter.tv.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediacenter.tv.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUrl by viewModel.serverUrl.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    var inputUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isUrlFocused by remember { mutableStateOf(false) }
    var isUserFocused by remember { mutableStateOf(false) }
    var isPassFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11111B))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚙ 设置与账户登录",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 服务端地址输入
        Text(
            text = "后端服务地址（例: http://192.168.1.100:3000）",
            color = Color(0xFFA6ADC8),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .width(480.dp)
                .padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .width(480.dp)
                .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp))
                .border(
                    width = if (isUrlFocused) 2.dp else 1.dp,
                    color = if (isUrlFocused) Color(0xFF6466F1) else Color(0xFF313244),
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
                    .onFocusChanged { isUrlFocused = it.isFocused }
                    .focusable()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoggedIn) {
            Text(
                text = "✅ 当前已登录账户",
                color = Color(0xFFA6E3A1),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = {
                    viewModel.logout()
                    Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF38BA8))
            ) {
                Text("退出当前账户", color = Color.White)
            }
        } else {
            // 用户名
            Text(
                text = "用户名",
                color = Color(0xFFA6ADC8),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .width(480.dp)
                    .padding(bottom = 6.dp)
            )
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp))
                    .border(
                        width = if (isUserFocused) 2.dp else 1.dp,
                        color = if (isUserFocused) Color(0xFF6466F1) else Color(0xFF313244),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = username,
                    onValueChange = { username = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isUserFocused = it.isFocused }
                        .focusable()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 密码
            Text(
                text = "密码",
                color = Color(0xFFA6ADC8),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .width(480.dp)
                    .padding(bottom = 6.dp)
            )
            Box(
                modifier = Modifier
                    .width(480.dp)
                    .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp))
                    .border(
                        width = if (isPassFocused) 2.dp else 1.dp,
                        color = if (isPassFocused) Color(0xFF6466F1) else Color(0xFF313244),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isPassFocused = it.isFocused }
                        .focusable()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.login(username, password) { success, error ->
                        if (success) {
                            Toast.makeText(context, "登录成功！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, error ?: "登录失败", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6466F1))
            ) {
                Text("登录账户", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    viewModel.updateServerUrl(inputUrl)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF313244))
            ) {
                Text("保存设置并返回", color = Color.White)
            }
        }
    }
}
