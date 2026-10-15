package com.linxin.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.linxin.MainActivity
import com.linxin.R

/**
 * Android 16+ 的 Live Updates（实况更新）backend。
 *
 * ColorOS 16 的「流体云」就是照这套标准 API 做的 —— OPPO 的说法是应用遵循谷歌实时活动 API
 * 规范即可直接打通，不用接它的私有 SDK。所以适配流体云 = 把这套标准姿势做对，
 * 而不是去找 OPPO 的库。三条硬条件少一条系统就不提升（来源见类注释末尾）：
 * ① manifest 声明 `POST_PROMOTED_NOTIFICATIONS`（已有）；
 * ② `setOngoing(true)` + `setRequestPromotedOngoing(true)`；
 * ③ 用 `ProgressStyle` 这个模板，并给一句状态栏胶囊上用的短摘要。
 * 另外它不接受分组摘要、自定义内容视图和自定义背景色。
 *
 * 这里以前是手塞 extras（`android.requestPromotedOngoing` / `android.shortCriticalText`）冒充的：
 * 自己拼字符串一旦系统换键名就静默失效，样式校验也拿不到。androidx.core 升到 1.18 之后
 * 换回真 API —— `setRequestPromotedOngoing` / `setShortCriticalText` / `ProgressStyle`。
 *
 * 来源：IT之家 / DoNews 关于 ColorOS 16 流体云接入 Android 16 Live Updates API 的报道，
 * 以及 Android Authority 对 Live Update 条件的整理（要 POST_PROMOTED_NOTIFICATIONS +
 * requestPromotedOngoing + 进行中 + 进度样式 + 短摘要）。
 */
class AndroidPromotedBackend : LiveActivityBackend {

    companion object {
        const val CHANNEL_ID = "linxin_live_promoted"

        /** 状态栏胶囊上系统只留得下几个字，多给也是被裁，这里先自己裁掉 */
        private const val CAPSULE_MAX_CHARS = 12
    }

    override fun build(context: Context, request: LiveActivityRequest, notificationId: Int): Notification {
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_linxin)
            .setContentTitle(request.title)
            .setContentText(request.content)
            .setOngoing(request.ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(buildPendingIntent(context, request, notificationId))
            .setShortCriticalText(request.capsuleText.take(CAPSULE_MAX_CHARS))
            .setRequestPromotedOngoing(true)

        when (val presentation = request.presentation) {
            is LiveActivityPresentation.Progress -> {
                // ProgressStyle 吃的是 0..100 的百分比，不是 current/total 两个数
                val total = presentation.total.coerceAtLeast(1)
                builder.setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgress((presentation.current * 100 / total).coerceIn(0, 100)),
                )
                presentation.label?.let { builder.setSubText(it) }
            }

            is LiveActivityPresentation.Text -> {
                presentation.subtitle?.let { builder.setSubText(it) }
                // 只有走文本形态才允许换成大字展开样式；进度那条一换样式就不再是实况更新
                request.extras.getString("bigText")?.let { style ->
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(style))
                }
            }
        }

        return builder.build()
    }

    private fun buildPendingIntent(
        context: Context,
        request: LiveActivityRequest,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("notification_route", request.route.destination)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // 不写"存在即跳过"：渠道名可随版本刷新，否则改过的文案在老用户手机上永远刷不掉
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "安大信 实况通知",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "跑步、签到等正在进行的活动"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
        )
    }
}
