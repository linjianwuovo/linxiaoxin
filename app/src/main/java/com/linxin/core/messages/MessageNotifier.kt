package com.linxin.core.messages

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.linxin.MainActivity
import com.linxin.R
import com.linxin.core.locale.AppText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息提醒的落地通道。和 `core/notification` 那套"进行中活动"的实况通知分开：
 * 那边是 IMPORTANCE_LOW 的常驻条，这里是会响的普通消息，用户能在系统设置里单独关。
 */
@Singleton
class MessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                AppText.str(R.string.msg_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = AppText.str(R.string.msg_channel_desc)
                setShowBadge(true)
            },
        )
    }

    val canPost: Boolean
        get() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 发一条提醒；返回是否真的发出去了（没权限就发不出去，调用方据此决定要不要记 seen） */
    fun show(alert: MessageAlert): Boolean {
        ensureChannel()
        if (!canPost) return false
        val id = idFor(alert.key)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ROUTE, alert.route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_linxin)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        manager.notify(id, notification)
        return true
    }

    /** key → 固定 id，保证同一条消息重复轮询时更新而不是叠一堆 */
    private fun idFor(key: String): Int = 5000 + (key.hashCode() and 0x3FFF)

    companion object {
        const val CHANNEL_ID = "linxin_messages"
        const val EXTRA_ROUTE = "notification_route"
    }
}
