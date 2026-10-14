package com.linxin.core.notification

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri

/**
 * 检测当前设备支持的实况通知能力。
 */
object DeviceCapability {

    enum class Backend { FLYME, ANDROID16, NORMAL }

    fun resolve(context: Context): Backend {
        val backend = when {
            isFlymeEnabled(context) -> Backend.FLYME
            isAndroid16Promoted(context) -> Backend.ANDROID16
            else -> Backend.NORMAL
        }
        // 每次都现算、并把它算了什么吐出来：这三路在国产 ROM 上翻脸比翻书快，
        // 没有这行日志下次"为什么没上岛"就只能靠猜（beta 上的诊断，转正时不留）
        Log.i(TAG, "backend=$backend sdk=${Build.VERSION.SDK_INT} canPost=${canPostPromoted(context)} perm=${hasPromotedPermission(context)}")
        return backend
    }

    private fun isFlymeEnabled(context: Context): Boolean {
        if (!Build.MANUFACTURER.equals("meizu", ignoreCase = true)) return false
        if (getFlymeVersion() < 11) return false
        if (!hasPromotedPermission("flyme.permission.READ_NOTIFICATION_LIVE_STATE", context)) return false
        return try {
            val result = context.contentResolver.call(
                "content://com.android.systemui.notification.provider".toUri(),
                "isNotificationLiveEnabled",
                null,
                null,
            )
            result?.getBoolean("result", false) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun getFlymeVersion(): Int {
        val display = Build.DISPLAY ?: return -1
        val match = Regex("Flyme\\s*([0-9]+)").find(display)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    /**
     * Android 16 的实况更新（HyperOS 那套「实时动态」/「小米岛」走的是同一套标准 API）。
     *
     * **不能只信 `canPostPromotedNotifications()`**：2026-10-14 在米11（Xiaomi，SDK 37）上
     * `dumpsys package` 现查 `POST_PROMOTED_NOTIFICATIONS: granted=true`，而这个方法照回 false，
     * 于是我们一路降级成普通通知，岛永远不出来。所以两个都问，任一为真就走 promoted ——
     * 真不给上，ProgressStyle 那条也会正常降级成常驻通知，不会更差。
     */
    private fun isAndroid16Promoted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return canPostPromoted(context) || hasPromotedPermission(context)
    }

    private fun canPostPromoted(context: Context): Boolean = try {
        context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
    } catch (_: Exception) {
        false
    }

    private fun hasPromotedPermission(context: Context): Boolean =
        hasPromotedPermission("android.permission.POST_PROMOTED_NOTIFICATIONS", context)

    private fun hasPromotedPermission(permission: String, context: Context): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private const val TAG = "LxLive"
}
