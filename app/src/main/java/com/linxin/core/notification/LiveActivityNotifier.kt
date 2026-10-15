package com.linxin.core.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实况通知统一门面。业务层只调用此类，不接触平台私有实现。
 *
 * 使用稳定 key → notificationId 映射，保证同一 key 更新同一条通知。
 */
@Singleton
class LiveActivityNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    private val keyToId = mutableMapOf<String, Int>()
    private var nextId = 3001

    private val flymeBackend by lazy { FlymeLiveBackend() }
    private val androidPromotedBackend by lazy { AndroidPromotedBackend() }
    private val normalBackend by lazy { NormalBackend() }

    private fun backendFor(context: Context): LiveActivityBackend =
        when (DeviceCapability.resolve(context)) {
            DeviceCapability.Backend.FLYME -> flymeBackend
            DeviceCapability.Backend.ANDROID16 -> androidPromotedBackend
            DeviceCapability.Backend.NORMAL -> normalBackend
        }

    private fun idFor(key: String): Int =
        keyToId.getOrPut(key) { nextId++ }

    /**
     * 每次都重新判能力，别按 key 缓存 backend：ColorOS 那个流体云开关是用户随时能在设置里
     * 拨的，缓存下来他在不重启进程的情况下打开，我们也还是发普通通知，看着就像"适配没生效"。
     */
    private fun backendForKey(key: String): LiveActivityBackend = backendFor(context)

    /**
     * 构建初始 Notification 对象（用于 startForeground）。
     */
    fun buildInitial(request: LiveActivityRequest): Notification {
        val id = idFor(request.key)
        return backendForKey(request.key).build(context, request, id)
    }

    /**
     * 发布或更新通知。
     */
    fun show(request: LiveActivityRequest) {
        val id = idFor(request.key)
        val notification = backendForKey(request.key).build(context, request, id)
        notificationManager.notify(id, notification)
    }

    /**
     * 取消指定 key 的通知。
     */
    fun cancel(key: String) {
        keyToId.remove(key)?.let { id ->
            notificationManager.cancel(id)
        }
    }

    /**
     * 获取指定 key 对应的 notification ID（用于 startForeground）。
     */
    fun idFor(request: LiveActivityRequest): Int = idFor(request.key)
}
