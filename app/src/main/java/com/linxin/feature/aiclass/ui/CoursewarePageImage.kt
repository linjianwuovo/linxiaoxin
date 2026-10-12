package com.linxin.feature.aiclass.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linxin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

/**
 * 课件逐页图的加载器。
 *
 * 为什么不用 Coil：这个工程只依赖了 coil-compose，缓存里没有 coil-network-okhttp /
 * coil-network-ktor 任何一个网络引擎，而 Coil 3 在没有网络引擎时不报错，只是远程图永远出不来。
 * 与其为这一屏加一个新依赖（这台机器的 gradle 还常常连不上代理），不如直接用工程里已有的 OkHttp 拉。
 *
 * 一页就是学校渲染好的一张 PNG。LazyColumn 只组合屏幕上这几条，但每张解码前先按目标宽度
 * 算 inSampleSize，免得 1920x1080 的图按 4 字节一个像素铺满内存。
 */
private const val PAGE_TARGET_WIDTH_PX = 1280

@Composable
fun CoursewarePageImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    val client = remember { coursewareImageClient() }

    LaunchedEffect(url) {
        bitmap = null
        failed = false
        if (url.isBlank()) {
            failed = true
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { fetchCoursewarePage(client, url) }.getOrNull()
        }
        if (loaded == null) failed = true else bitmap = loaded
    }

    val current = bitmap
    when {
        current != null -> Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth(),
        )

        failed -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ai_courseware_page_failed),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.error,
            )
        }

        else -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_courseware_page_loading),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

/** 整个进程共用一个连接池就够了，翻页是一串同域请求 */
private var sharedCoursewareImageClient: OkHttpClient? = null

private fun coursewareImageClient(): OkHttpClient {
    return sharedCoursewareImageClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
        .also { sharedCoursewareImageClient = it }
}

private fun fetchCoursewarePage(client: OkHttpClient, url: String): Bitmap? {
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val bytes = response.body?.bytes() ?: return null
        return decodeSampledToTargetWidth(bytes)
    }
}

/** 先只读尺寸，按目标宽度算出 2 的幂的采样率，再真解码 */
private fun decodeSampledToTargetWidth(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= PAGE_TARGET_WIDTH_PX) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
