package com.linxin.feature.running.service

import com.linxin.core.locale.AppText
import com.linxin.R
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.linxin.core.location.LocationProvider
import com.linxin.core.notification.LiveActivityPresentation
import com.linxin.core.notification.LiveActivityNotifier
import com.linxin.core.notification.LiveActivityRequest
import com.linxin.core.notification.NotificationRoute
import com.linxin.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class RunTrackingService : Service() {

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var tracker: RunningTracker
    @Inject lateinit var notifier: LiveActivityNotifier

    @Inject lateinit var internalFeed: com.linxin.core.location.InternalGpsFeed

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking(removeSession = false)
            ACTION_CANCEL -> stopTracking(removeSession = true)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTracking() {
        // 走 LocationProvider 那句：应用内部喂点开着的时候不需要真定位权限
        if (!locationProvider.hasLocationPermission()) {
            tracker.onLocationError(AppText.str(R.string.svc_loc_perm_missing))
            stopSelf()
            return
        }

        val startInfo = tracker.state.value.startInfo
        val targetKm = startInfo?.mixOnceMileKm ?: 0.0
        val initialRequest = LiveActivityRequest(
            key = NOTIFICATION_KEY,
            channelId = NOTIFICATION_KEY,
            title = AppText.str(R.string.svc_run_active),
            content = AppText.str(R.string.svc_waiting_gps),
            capsuleText = "0.00km",
            route = NotificationRoute(destination = Routes.RUNNING_ACTIVE),
            // 实况更新（小米「实时动态」/OPPO 流体云）吃的是进度模板：没有 ProgressStyle
            // 系统就当一条普通常驻通知，卡片上没有那根进度条。单次要求公里数就是分母。
            presentation = run {
                val label = if (targetKm > 0.0) AppText.str(R.string.svc_run_target, formatKm(targetKm)) else null
                if (targetKm > 0.0) {
                    LiveActivityPresentation.Progress(0, (targetKm * 1000).toInt(), label)
                } else {
                    LiveActivityPresentation.Text(subtitle = label)
                }
            },
            extras = Bundle().apply {
                putString("bigText", AppText.str(R.string.svc_waiting_gps))
                putString("distance", "0.00 km")
                putString("line2", AppText.str(R.string.svc_waiting_loc))
                putString("line3", "")
            },
        )
        startForeground(notifier.idFor(initialRequest), notifier.buildInitial(initialRequest))

        trackingJob?.cancel()
        tracker.onServiceStarted()
        trackingJob = serviceScope.launch {
            try {
                locationProvider.locationUpdates(intervalMs = 3000L, minDistanceM = 2f).collect { location ->
                    tracker.onLocationUpdate(location)
                    notifyState()
                }
            } catch (_: SecurityException) {
                tracker.onLocationError(AppText.str(R.string.svc_loc_perm_missing))
                stopTracking(removeSession = false)
            }
        }
    }

    private fun stopTracking(removeSession: Boolean) {
        trackingJob?.cancel()
        trackingJob = null
        if (removeSession) {
            tracker.cancelSession()
        } else {
            tracker.stopCollecting()
        }
        notifier.cancel(NOTIFICATION_KEY)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyState() {
        val state = tracker.state.value
        val distanceKm = state.totalDistanceMeters / 1000.0
        val durationSeconds = ((System.currentTimeMillis() - state.startTimeMillis) / 1000L).coerceAtLeast(0L)
        val speedKmh = if (durationSeconds > 0L) distanceKm / durationSeconds * 3600.0 else 0.0

        val title = if (state.isCollecting) AppText.str(R.string.svc_run_active) else AppText.str(R.string.svc_run_paused)
        val distanceStr = String.format("%.2f km", distanceKm)
        val durationStr = formatDuration(durationSeconds)
        val speedStr = if (speedKmh > 0.0) String.format("%.1f km/h", speedKmh) else "-- km/h"
        val capsuleText = String.format("%.2fkm", distanceKm)
        val content = AppText.str(R.string.svc_content, distanceStr, durationStr)
        // 喂点开着就必须标出来：不然屏幕上看着像真在跑，回头提交才发现是假坐标
        val label = if (internalFeed.isOn.value) {
            state.locationLabel + "（模拟定位）"
        } else {
            state.locationLabel
        }
        val bigText = AppText.str(R.string.svc_bigtext, distanceStr, durationStr, speedStr, label)

        val extras = Bundle().apply {
            putString("bigText", bigText)
            putString("distance", distanceStr)
            putString("line2", AppText.str(R.string.svc_line2, durationStr, speedStr, label))
            putString("line3", "")
        }

        val request = LiveActivityRequest(
            key = NOTIFICATION_KEY,
            channelId = NOTIFICATION_KEY,
            title = title,
            content = content,
            capsuleText = capsuleText,
            route = NotificationRoute(destination = Routes.RUNNING_ACTIVE),
            presentation = run {
                val targetMeters = (state.startInfo?.mixOnceMileKm ?: 0.0) * 1000.0
                if (targetMeters > 1.0) {
                    LiveActivityPresentation.Progress(
                        current = state.totalDistanceMeters.toInt(),
                        total = targetMeters.toInt(),
                        label = AppText.str(R.string.svc_run_target, formatKm(state.startInfo?.mixOnceMileKm ?: 0.0)),
                    )
                } else {
                    LiveActivityPresentation.Text()
                }
            },
            extras = extras,
        )
        notifier.show(request)
    }

    /** 公里数一律一位小数，通知里 "1.0" 比 "1.0" 的 Double 尾巴好看 */
    private fun formatKm(km: Double): String = String.format("%.1f", km)

    private fun formatDuration(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    companion object {
        private const val NOTIFICATION_KEY = "running"
        private const val ACTION_START = "com.linxin.running.START"
        private const val ACTION_STOP = "com.linxin.running.STOP"
        private const val ACTION_CANCEL = "com.linxin.running.CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(ACTION_CANCEL)
            context.startService(intent)
        }
    }
}
