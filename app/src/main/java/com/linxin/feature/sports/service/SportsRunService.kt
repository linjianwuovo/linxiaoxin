package com.linxin.feature.sports.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import com.linxin.R
import com.linxin.core.notification.AndroidPromotedBackend
import com.linxin.core.notification.LiveActivityNotifier
import com.linxin.core.notification.LiveActivityPresentation
import com.linxin.core.notification.LiveActivityRequest
import com.linxin.core.notification.NotificationRoute
import com.linxin.feature.sports.domain.RunSession
import com.linxin.feature.sports.domain.SportsRoute
import com.linxin.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 前台服务：把这一场跑完的过程保住（锁屏、切后台都不掉点），
 * 通知栏那条就是官方那三样：公里、时长、速率。
 *
 * 通知走 `LiveActivityNotifier` 那套门面 —— 能被系统提升成实况通知（流体云 / 实时岛）的
 * 就提升，提升不了的自动退回常驻通知，卡片上三行内容一个字不少。
 *
 * 引擎是单例，界面直接读它的 StateFlow；服务只负责"别被系统杀掉 + 让人看得见进度"。
 */
@AndroidEntryPoint
class SportsRunService : android.app.Service() {

    @Inject
    lateinit var engine: SportsRunEngine

    @Inject
    lateinit var notifier: LiveActivityNotifier

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        jobScope.cancel()
        notifier.cancel(KEY)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // 这是"进程被杀之后系统自己把服务复活"那一路：复活发生在后台，而引擎是进程内的
            // @Singleton、会话早跟着进程一起没了，这里没有任何可续的东西。
            // Android 12+ 从后台起 location 类型的前台服务是直接抛，所以这一路只停、不去贴通知。
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                stopForeground(true)
                notifier.cancel(KEY)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startOwnForeground()
                observe()
            }
        }
        // 原来这一行是 super.onStartCommand(...)，而 Service 的默认值是 START_STICKY ——
        // 那正是上面那条后台复活的来源。这一场的数据不落盘，进程一死就没了，粘着重启续不出任何东西，
        // 只会多一次崩，所以明写不粘。
        return START_NOT_STICKY
    }

    private fun startOwnForeground() {
        retireLegacyChannel()
        // Android 14 (API 34) 起前台服务必须声明类型
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        val request = requestFor(engine.state.value)
        ServiceCompat.startForeground(this, notifier.idFor(request), notifier.buildInitial(request), type)
    }

    /** 老版本这条挂在自建渠道「跑步」上，换门面之后那个渠道成孤儿，删掉别留在设置里 */
    private fun retireLegacyChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)
            .deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun observe() {
        jobScope.launch {
            engine.state.collect { state ->
                if (!state.active) {
                    stopForeground(true)
                    notifier.cancel(KEY)
                    stopSelf()
                    return@collect
                }
                notifier.show(requestFor(state))
            }
        }
    }

    /**
     * 卡片三行照官方那一屏排：大字那行给公里数，副标题给状态，内容给时长和速率。
     * 进度条按"这一场要跑够几公里"走，那个数是服务器下发的 mixOnceMile，不是我们拍的。
     */
    private fun requestFor(state: SportsRunEngine.State): LiveActivityRequest {
        val seconds = state.elapsedSeconds()
        val distance = "%.2f".format(state.distanceKm)
        val timeText = "%02d:%02d".format(seconds / 60, seconds % 60)
        val label = when {
            state.simulating -> getString(R.string.ra_sim_on)
            state.paused -> getString(R.string.ra_title_paused)
            state.waitingFix -> getString(R.string.ra_wait_gps)
            else -> getString(R.string.ra_title_running)
        }
        val total = (engine.targetKm * 100).toInt().coerceAtLeast(1)
        val speed = "%.1f".format(state.instantKmh)
        return LiveActivityRequest(
            key = KEY,
            channelId = AndroidPromotedBackend.CHANNEL_ID,
            title = distance + getString(R.string.ra_km),
            content = getString(R.string.notif_run_line, speed, timeText),
            // 胶囊那串在米11 上被裁过，所以只放实时速度：「6.4km/h」七个字
            capsuleText = getString(R.string.notif_run_capsule, speed),
            route = NotificationRoute(destination = Routes.RUNNING_ACTIVE),
            presentation = LiveActivityPresentation.Progress(
                current = (state.distanceKm * 100).toInt().coerceIn(0, total),
                total = total,
                label = label,
            ),
        )
    }

    companion object {
        const val ACTION_STOP = "com.linxin.sports.STOP"

        /** 门面内部按 key 映射通知 id，这里不再自己写死一个 id */
        private const val KEY = "sports_run"
        private const val LEGACY_CHANNEL_ID = "sports_run"

        fun start(context: Context) {
            val intent = Intent(context, SportsRunService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SportsRunService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** 开场参数走引擎单例，不用塞进 Intent（路线点可能上千个） */
        fun begin(
            context: Context,
            engine: SportsRunEngine,
            session: RunSession,
            route: SportsRoute?,
            paceKmh: Double,
        ) {
            engine.begin(session, route, paceKmh)
            start(context)
        }
    }
}
