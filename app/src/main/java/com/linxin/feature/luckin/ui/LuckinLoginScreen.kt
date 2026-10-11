package com.linxin.feature.luckin.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.settings.LuckinPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 瑞幸登录：只在瑞幸官方开放平台域名里发生，我们不经手手机号和验证码。
 *
 * 登录成功后，开放平台会在页面上给出个人 token。这里做的事是轮询那段页面，
 * 把 token 从 localStorage / sessionStorage / 可见文本里捞出来存进本机 DataStore，
 * 然后关掉这页，之后所有下单链路都是我们自己直连 MCP 接口。
 *
 * 捞不到就让他自己按页面提示复制——兜底，不是主路。
 */
private const val LOGIN_URL = "https://open.lkcoffee.com/"

/** token 长这样：一串十六进制 + "mcp" + 后缀。三个来源都按这一个正则找 */
private const val HARVEST_JS = """
(function(){
  var re = /[0-9a-fA-F]{20,}mcp[A-Za-z0-9_]{2,}/;
  function fromStore(st){
    try {
      for (var i = 0; i < st.length; i++) {
        var k = st.key(i) || '';
        var v; try { v = st.getItem(k) || ''; } catch (e) { continue; }
        var m = v.match(re) || k.match(re);
        if (m) return m[0];
      }
    } catch (e) {}
    return '';
  }
  var out = fromStore(window.localStorage) || fromStore(window.sessionStorage);
  if (!out) {
    try {
      var t = document.body ? document.body.innerText : '';
      var m3 = t.match(re);
      if (m3) out = m3[0];
    } catch (e) {}
  }
  return out || '';
})()
"""
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LuckinLoginScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var wv by remember { mutableStateOf<WebView?>(null) }
    val waitText = stringResource(R.string.luckin_login_wait)
    var status by remember { mutableStateOf(waitText) }
    var reloadTick by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadTick, done) {
        if (done) return@LaunchedEffect
        // 登录是异步的，轮询页面直到拿到 token；每 1.5 秒一次，最多 5 分钟
        val web = wv
        if (web == null) return@LaunchedEffect
        var tries = 0
        while (!done && tries < 200) {
            tries++
            web.evaluateJavascript(HARVEST_JS) { raw ->
                val v = raw.trim().removeSurrounding("\"")
                if (v.length > 24) {
                    done = true
                    status = context.getString(R.string.luckin_login_ok)
                    scope.launch { LuckinPrefs(context).setToken(v); onDone() }
                }
            }
            delay(1500)
        }
        if (!done) status = context.getString(R.string.luckin_login_timeout)
    }

    Scaffold(
        topBar = {
            LxTopBar(
                title = stringResource(R.string.luckin_login_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { reloadTick++ }) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                    }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.luckin_login_note),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Box(Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    reloadTick++
                                }
                            }
                            loadUrl(LOGIN_URL)
                            wv = this
                        }
                    },
                )
            }
            LxCard {
                Column(
                    Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(status, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer3()
                    Text(
                        stringResource(R.string.luckin_login_manual),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Spacer3() {
    Box(Modifier.fillMaxWidth().padding(top = 2.dp))
}
