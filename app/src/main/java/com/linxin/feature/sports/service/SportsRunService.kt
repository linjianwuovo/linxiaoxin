package com.linxin.feature.sports.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.linxin.R
import com.linxin.feature.sports.domain.RunSession
import com.linxin.feature.sports.domain.SportsRoute
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
 * 引擎是单例，界面直接读它的 StateFlow；服务只负责"别被系统杀掉 + 让人看得见进度"。
 */
@AndroidEntryPoint
class SportsRunService : android.app.Service() {

    @Inject
    lateinit var engine: SportsRunEngine

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        jobScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startOwnForeground()
                observe()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startOwnForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "跑步", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // Android 14 (API 34) 起前台服务必须声明类型
        val flags = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification("0.00 公里"), flags)
    }

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun observe() {
        jobScope.launch {
            engine.state.collect { state ->
                if (!state.active) {
                    stopForeground(true)
                    stopSelf()
                    return@collect
                }
                val seconds = state.elapsedSeconds()
                val text = "%.2f 公里 · %02d:%02d".format(state.distanceKm, seconds / 60, seconds % 60)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val pending = open?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_linxin)
            .setContentTitle(getString(R.string.ra_title_running))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.linxin.sports.STOP"
        private const val CHANNEL_ID = "sports_run"
        private const val NOTIFICATION_ID = 4201

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
