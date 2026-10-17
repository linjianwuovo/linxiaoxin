package com.linxin.feature.about.ui

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSand
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.designsystem.theme.LxTerra

// 小红书主页的分享短链：装了小红书 App 会直接跳到 App 里，没装就走浏览器
private const val XHS_LINK = "https://xhslink.cn/o/2WLa4prWUT3"
private const val XHS_PACKAGE = "com.xingin.xhs"

/** 指名让小红书来接这条链接；它不认领原样就把 .cn 换成 .com 再问一次。接了返回 true。 */
private fun openInXiaohongshu(context: android.content.Context, url: String): Boolean {
    for (u in listOf(url, url.replace("://xhslink.cn/", "://xhslink.com/"))) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage(XHS_PACKAGE)
        if (intent.resolveActivity(context.packageManager) != null &&
            runCatching { context.startActivity(intent); true }.getOrDefault(false)
        ) return true
    }
    return false
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_about), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp),
        ) {
            BrandCard(versionName = uiState.versionName)

            Spacer(modifier = Modifier.height(22.dp))

            LxCard {
                Column {
                    GitHubRow()
                    CardDivider()
                    XiaohongshuRow()
                }
            }
        }
    }
}

@Composable
private fun BrandCard(versionName: String) {
    LxCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(80.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(1.45f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontWeight = FontWeight.Medium,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = LxTerra,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                fontSize = 13.sp,
                color = LxInkMuted,
            )
            if (versionName.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "v$versionName",
                    fontSize = 12.sp,
                    color = LxInkMuted,
                )
            }
            // MiSans 许可协议第 1 条要求"在软件中特别注明使用了 MiSans 字体"。
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.about_misans),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = LxInkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GitHubRow() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/linjianwuovo/linxiaoxin"))
                context.startActivity(intent)
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.about_github),
            fontSize = 15.sp,
            color = LxInk,
        )
        Text(
            text = "›",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            color = LxInkMuted,
        )
    }
}

@Composable
private fun XiaohongshuRow() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // 小红书在系统里认领的是 xhslink.com 和 www.xiaohongshu.com，
                // 不认领分享出来这条 .cn —— 直接 ACTION_VIEW 只会被浏览器接走。
                // 所以三层：① 原链接指名问小红书要不要；② 它不应就把域名换成 .com 再指名问一次；
                // ③ 两回都不应（没装/版本不认）才交给系统默认浏览器。
                if (!openInXiaohongshu(context, XHS_LINK)) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(XHS_LINK)))
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.about_xhs),
            fontSize = 15.sp,
            color = LxInk,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.about_xhs_copy),
                fontSize = 13.sp,
                color = LxSandDeep,
                modifier = Modifier
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("xhs", XHS_LINK))
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.about_xhs_copied),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "›",
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                color = LxInkMuted,
            )
        }
    }
}

@Composable
private fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(LxSand),
    )
}

