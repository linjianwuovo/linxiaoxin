package com.linxin.core.notification

import android.content.Context
import androidx.core.content.edit
import com.linxin.core.domain.SectionSchedule
import com.linxin.core.messages.MessageScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「下一节快开课了」这个窗口。
 *
 * 写的人是在前台每分钟跳一次的 [CourseNotificationScheduler]；读的人是后台的
 * MessagePollWorker。为什么要这么绕一层：WorkManager 的周期任务最短就是 15 分钟，
 * 想在课前做到一分钟一次，只能靠这条窗口把一次性任务一节一节自己续下去。
 *
 * 存的是绝对时间戳，不是「第几节」：这样过期的值只会让快轮询不触发（那个时间戳已经在过去），
 * 不会把昨天那节课当成今天要开的课来提醒。进程被杀、隔夜没打开过 App 这些情况，
 * 最坏结果就是退回 15 分钟一次，不会报错也不会误提。
 */
@Singleton
class NextClassWindowStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy { context.getSharedPreferences(PREF, Context.MODE_PRIVATE) }

    /** 把「今天第几节几点开始」记下来；只有还没开课的才有意义 */
    fun publish(startSection: Int, courseName: String, room: String) {
        val startTime = SectionSchedule.startOf(startSection) ?: return
        publishAt(LocalDate.now().atTime(startTime), courseName, room)
    }

    fun publishAt(startDateTime: LocalDateTime, courseName: String, room: String) {
        val epochSecond = startDateTime.atZone(ZoneId.systemDefault()).toEpochSecond()
        prefs.edit {
            putLong(KEY_START, epochSecond)
            putString(KEY_NAME, courseName)
            putString(KEY_ROOM, room)
        }
        // 课程倒计时通知在课前 25 分钟就出来了，但消息检查提速只按他说的 15 分钟来：
        // 这里再判一次，别拿倒计时的那个数当窗口。
        if (minutesUntilStart(MessageScheduler.FAST_WINDOW_MINUTES) != null) {
            MessageScheduler.syncFast(context, on = true)
        }
    }

    fun clear() {
        prefs.edit { putLong(KEY_START, 0L).putString(KEY_NAME, null).putString(KEY_ROOM, null) }
        MessageScheduler.syncFast(context, on = false)
    }

    /** 还有几分钟开课；不在窗口里（没记过、已经点过、或者超出窗口）一律回 null */
    fun minutesUntilStart(withinMinutes: Long): Long? {
        val delta = signedMinutesUntilStart() ?: return null
        return delta.takeIf { it in 0L..withinMinutes }
    }

    /** 带符号：开课之后就是负数。倒计时那条通知要一直显示到课后 5 分钟，所以得知道负多少 */
    fun signedMinutesUntilStart(): Long? {
        val start = prefs.getLong(KEY_START, 0L)
        if (start <= 0L) return null
        return (start - System.currentTimeMillis() / 1000L) / 60L
    }

    fun courseName(): String = prefs.getString(KEY_NAME, "").orEmpty()

    fun room(): String = prefs.getString(KEY_ROOM, "").orEmpty()

    private companion object {
        const val PREF = "linxin_next_class_window"
        const val KEY_START = "starts_at_epoch_second"
        const val KEY_NAME = "course_name"
        const val KEY_ROOM = "room"
    }
}
