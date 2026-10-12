package com.linxin.feature.luckin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.luckin.LuckinAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 瑞幸登录：手机号 + 验证码，走瑞幸自家网关 `m.lkcoffee.com/capi/`。
 *
 * 这条不是官方 AI 开放平台那套「跳去网页登录再把 token 捞回来」——那套要人在页面里
 * 点一次复制，捞不到还得手填。这里手机号和验证码直接进登录请求，不落文件、不进日志，
 * 会话 Cookie 只存在应用私有的 SharedPreferences 里。
 */
@Composable
fun LuckinLoginScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(0) }
    var logged by remember { mutableStateOf(LuckinAuth.isLoggedIn()) }
    var who by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { LuckinAuth.init(context) }
    LaunchedEffect(left) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    LaunchedEffect(logged) {
        if (logged && who == null) {
            who = runCatching { LuckinAuth.profile().name }.getOrNull()
        }
    }

    Scaffold(topBar = { LxTopBar(title = stringResource(R.string.luckin_login_title), onBack = onBack) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (logged) {
                LxCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.luckin_login_already, who ?: "—"),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(12.dp))
                        LxPillButton(stringResource(R.string.luckin_login_continue)) { onDone() }
                        Spacer(Modifier.height(10.dp))
                        LxPillButton(stringResource(R.string.luckin_login_logout)) {
                            LuckinAuth.logout()
                            logged = false
                            who = null
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.luckin_login_note),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                LxTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 13) phone = it },
                    label = stringResource(R.string.luckin_login_phone),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                LxPillButton(
                    if (left > 0) stringResource(R.string.luckin_login_again, left)
                    else stringResource(R.string.luckin_login_send),
                    enabled = left == 0 && phone.length >= 11,
                ) {
                    scope.launch {
                        busy = true
                        runCatching { LuckinAuth.sendCode(phone.trim()) }
                            .onSuccess {
                                tip = null
                                left = 60
                            }
                            .onFailure { tip = it.message ?: context.getString(R.string.luckin_login_failed) }
                        busy = false
                    }
                }
                LxTextField(
                    value = code,
                    onValueChange = { if (it.length <= 8) code = it },
                    label = stringResource(R.string.luckin_login_code),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                LxPillButton(
                    if (busy) stringResource(R.string.luckin_login_busy)
                    else stringResource(R.string.luckin_login_do),
                    enabled = !busy && phone.length >= 11 && code.length >= 4,
                ) {
                    scope.launch {
                        busy = true
                        runCatching { LuckinAuth.login(phone.trim(), code.trim()) }
                            .onSuccess {
                                logged = true
                                tip = null
                                onDone()
                            }
                            .onFailure { tip = it.message ?: context.getString(R.string.luckin_login_failed) }
                        busy = false
                    }
                }
            }
            tip?.let {
                Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
