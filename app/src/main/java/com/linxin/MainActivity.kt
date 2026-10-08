package com.linxin

import com.linxin.core.locale.ProvideAppLanguage
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.linxin.core.auth.SessionManager
import com.linxin.core.designsystem.theme.LinXinTheme
import com.linxin.core.settings.ThemeMode
import com.linxin.core.settings.ThemePrefs
import com.linxin.core.settings.ThemeSettings
import com.linxin.feature.home.domain.HomeBootstrap
import com.linxin.feature.splash.ui.SplashOverlay
import com.linxin.navigation.LinXinNavHost
import com.linxin.navigation.ShortcutRouter
import com.linxin.navigation.ShortcutTarget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var homeBootstrap: HomeBootstrap
    @Inject lateinit var shortcutRouter: ShortcutRouter
    @Inject lateinit var themePrefs: ThemePrefs

    private var shortcutTarget by mutableStateOf<ShortcutTarget?>(null)
    private var pendingDormTaskId by mutableStateOf<String?>(null)
    private var dormShortcutResolved by mutableStateOf(false)
    private var pendingNotificationRoute by mutableStateOf<String?>(null)
    private var pendingCrashLog: String? = null

    // 应用内开屏放行标志：数据就绪(或超时)且满足最短展示时长后置 true，开屏随之淡出
    private var splashReleased by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectPreviousCrashLog()
        shortcutTarget = ShortcutTarget.fromAction(intent?.action)
        pendingNotificationRoute = intent?.getStringExtra("notification_route")

        // 独立协程：真实执行数据加载（即使超时也会继续跑完，结果写入 HomeBootstrap.snapshot）
        lifecycleScope.launch { homeBootstrap.load() }
        val dormShortcutReady = if (shortcutTarget == ShortcutTarget.DORM_CHECKIN) {
            lifecycleScope.async {
                pendingDormTaskId = shortcutRouter.resolveFirstUnsignedTask()
                dormShortcutResolved = true
            }
        } else {
            dormShortcutResolved = true
            null
        }
        // 独立协程：等待 ready 或超时，并保证开屏最短展示时长，避免一闪而过
        val splashStart = SystemClock.elapsedRealtime()
        lifecycleScope.launch {
            withTimeoutOrNull(SPLASH_MAX_WAIT_MS) {
                homeBootstrap.ready.first { it }
                dormShortcutReady?.await()
            }
            val elapsed = SystemClock.elapsedRealtime() - splashStart
            if (elapsed < SPLASH_MIN_MS) delay(SPLASH_MIN_MS - elapsed)
            splashReleased = true
        }

        // 状态栏与导航栏按系统深色模式切换图标明暗：浅色主题下用 light（深色图标贴暖底），深色主题下用 dark（浅色图标）
        val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val statusBarStyle = if (isNight) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        val navigationBarStyle = if (isNight) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )
        setContent {
            val settings by themePrefs.settings.collectAsState(initial = ThemeSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Monet：Android 12+ 从壁纸动态取色作为主题色；否则用用户手动挑选的 accent。
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val accent = if (settings.monet && android.os.Build.VERSION.SDK_INT >= 31) {
                runCatching {
                    val scheme = if (darkTheme) {
                        androidx.compose.material3.dynamicDarkColorScheme(ctx)
                    } else {
                        androidx.compose.material3.dynamicLightColorScheme(ctx)
                    }
                    scheme.primary
                }.getOrDefault(androidx.compose.ui.graphics.Color(settings.accent))
            } else {
                androidx.compose.ui.graphics.Color(settings.accent)
            }
            LinXinTheme(
                darkTheme = darkTheme,
                accent = accent,
            ) {
                // 语言在组合内换 Resources，不重启进程，所以切换不闪开屏。见 ProvideAppLanguage。
                ProvideAppLanguage(language = settings.language) {
                    Box(Modifier.fillMaxSize()) {
                    LinXinNavHost(
                        sessionManager = sessionManager,
                        shortcutTarget = shortcutTarget,
                        pendingDormTaskId = pendingDormTaskId,
                        isDormShortcutResolved = dormShortcutResolved,
                        pendingNotificationRoute = pendingNotificationRoute,
                        onShortcutConsumed = {
                            shortcutTarget = null
                            pendingDormTaskId = null
                            dormShortcutResolved = false
                            pendingNotificationRoute = null
                            shortcutRouter.consume()
                        },
                    )
                    AnimatedVisibility(
                        visible = !splashReleased,
                        enter = fadeIn(),
                        exit = fadeOut(tween(320)),
                    ) {
                        SplashOverlay()
                    }
                    }
                }
            }
        }
        showCrashDialogIfNeeded()
    }

    /** 读取上次崩溃落盘日志：复制到剪贴板并弹窗，便于用户粘贴给开发端 */
    private fun collectPreviousCrashLog() {
        val file = java.io.File(filesDir, LinXinApp.CRASH_FILE_NAME)
        val content = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()
        if (content.isNullOrBlank()) return
        runCatching { file.delete() }
        pendingCrashLog = content
        runCatching {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("linxin_crash", content))
        }
    }

    private fun showCrashDialogIfNeeded() {
        val log = pendingCrashLog ?: return
        val tv = android.widget.TextView(this).apply {
            text = log
            setTextIsSelectable(true)
            textSize = 11f
            setPadding(48, 24, 48, 24)
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        android.app.AlertDialog.Builder(this)
            .setTitle("上次崩溃日志(已复制,粘贴发我)")
            .setView(sv)
            .setPositiveButton("知道了", null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 通知点击路由
        val notificationRoute = intent.getStringExtra("notification_route")
        if (notificationRoute != null) {
            pendingNotificationRoute = notificationRoute
            return
        }
        shortcutTarget = ShortcutTarget.fromAction(intent.action)
        pendingDormTaskId = null
        dormShortcutResolved = shortcutTarget != ShortcutTarget.DORM_CHECKIN
        shortcutRouter.consume()
        if (shortcutTarget == ShortcutTarget.DORM_CHECKIN) {
            lifecycleScope.launch {
                pendingDormTaskId = shortcutRouter.resolveFirstUnsignedTask()
                dormShortcutResolved = true
            }
        }
    }

    companion object {
        private const val SPLASH_MAX_WAIT_MS = 1500L
        private const val SPLASH_MIN_MS = 700L
    }
}
