package com.linxin.core.notification

import com.linxin.core.locale.AppText
import android.os.Bundle
import com.linxin.R
import com.linxin.core.domain.SectionSchedule
import com.linxin.feature.schedule.domain.Course
import com.linxin.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseNotificationScheduler @Inject constructor(
    private val notifier: LiveActivityNotifier,
    private val nextClass: NextClassWindowStore,
) {
    companion object {
        private const val KEY = "course_countdown"

        /** 课前进度条按这 15 分钟算满：还有 15 分钟 = 0%，马上上课 = 100% */
        private const val COUNTDOWN_WINDOW_MINUTES = 15
        private const val TRIGGER_MINUTES = 25L
        private const val DISMISS_AFTER_START_MINUTES = 5L
    }

    private var job: Job? = null

    fun start(scope: CoroutineScope, coursesProvider: () -> List<Course>) {
        job?.cancel()
        job = scope.launch {
            while (true) {
                check(coursesProvider())
                delay(60_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        notifier.cancel(KEY)
    }

    fun checkNow(courses: List<Course>) {
        check(courses)
    }

    private fun check(courses: List<Course>) {
        val now = LocalTime.now()
        val target = findTargetCourse(courses, now)

        if (target == null) {
            notifier.cancel(KEY)
            nextClass.clear()
            return
        }

        val startTime = SectionSchedule.startOf(target.startSection) ?: return
        val minutesUntilStart = ChronoUnit.MINUTES.between(now, startTime)

        // 把「下一节几点开始、在哪个教室」交给后台那条链：前台进程一旦被系统收掉，
        // 只有 worker 还能每分钟把这条通知的数字往下刷。2026-10-13 他锁屏看到的
        // 「还有 23 分钟」挂了 22 分钟没动，就是因为刷的那个人已经死了。
        nextClass.publish(target.startSection, target.name, target.room)
        postCountdown(target.name, target.room, minutesUntilStart)
    }

    /**
     * 后台 worker 每分钟调这个：读窗口里存的课名/教室/开始时间，重算还剩几分钟。
     * 回 true = 这条还在显示窗口里，链继续；回 false = 不在窗口（或已经收掉）。
     * 只认窗口里那一个时间戳，不重新拉课表 —— 后台不一定有登录态。
     */
    fun refreshCountdown(): Boolean {
        val minutes = nextClass.signedMinutesUntilStart() ?: return false
        val name = nextClass.courseName()
        if (name.isBlank()) return false
        return when {
            minutes > TRIGGER_MINUTES -> false
            minutes < -DISMISS_AFTER_START_MINUTES -> {
                notifier.cancel(KEY)
                nextClass.clear()
                false
            }
            else -> {
                postCountdown(name, nextClass.room(), minutes)
                true
            }
        }
    }

    private fun postCountdown(name: String, room: String, minutesUntilStart: Long) {
        val statusText = if (minutesUntilStart > 0) {
            AppText.str(R.string.notif_upcoming, minutesUntilStart.toString())
        } else {
            AppText.str(R.string.notif_started)
        }

        // 胶囊那一小块只有七个字的位置，放得下"还剩几分钟"就放不下教室。
        // 2026-10-15 他要的是教室（"小流体云没有教室"）：分钟数展开卡片上本来就有，
        // 教室只在胶囊里露一次，所以这一格给教室；读不到教室的课退回分钟数。
        val capsuleText = shortenRoom(room).ifBlank {
            AppText.str(R.string.notif_capsule_minutes, minutesUntilStart)
        }
        // 实况卡片上「标题 / 副标题 / 内容」是三行：标题放科目，副标题放还剩几分钟，
        // 内容就放教室。之前内容也塞了"还剩几分钟"，结果卡片上连着两行一模一样，
        // 教室一个字都没露出来。
        val roomLine = AppText.str(
            R.string.notif_room_line,
            room.ifBlank { AppText.str(R.string.notif_room_unknown) },
        )
        val bigText = AppText.str(R.string.notif_content, statusText, room)

        val extras = Bundle().apply {
            putString("courseName", name)
            putString("statusText", statusText)
            putString("room", room)
            putString("bigText", bigText)
        }

        val request = LiveActivityRequest(
            key = KEY,
            channelId = KEY,
            title = name,
            content = roomLine,
            capsuleText = capsuleText,
            route = NotificationRoute(destination = Routes.AICLASS_HOME),
            // 课前这段时间用进度条表达（离上课越近条越满）：实况更新那套模板吃的就是进度样式，
            // 流体云要提升一条通知，用进度模板比用纯文本更容易被认出来
            presentation = LiveActivityPresentation.Progress(
                // minutesUntilStart 是分钟数（Long），换算成 0..15 的整数进度
                current = ((COUNTDOWN_WINDOW_MINUTES - minutesUntilStart).toInt())
                    .coerceIn(0, COUNTDOWN_WINDOW_MINUTES),
                total = COUNTDOWN_WINDOW_MINUTES,
                label = statusText,
            ),
            extras = extras,
        )
        notifier.show(request)
    }

    /**
     * 简化地点名称用于胶囊显示。
     * "信息楼（A1）S204" → "A1S204"
     * 提取括号内容 + 括号后内容，去掉中文楼名。
     */
    private fun shortenRoom(room: String): String {
        val match = Regex("[（(]([^）)]+)[）)](.*)").find(room)
        if (match != null) {
            val prefix = match.groupValues[1]
            val suffix = match.groupValues[2].trim()
            return (prefix + suffix).take(7)
        }
        return room.take(7)
    }

    /**
     * 找到当前应该显示通知的课程：
     * - 距开始 ≤25 分钟且尚未开始超过 5 分钟的课程
     */
    private fun findTargetCourse(courses: List<Course>, now: LocalTime): Course? {
        return courses
            .sortedBy { it.startSection }
            .firstOrNull { course ->
                val startTime = SectionSchedule.startOf(course.startSection) ?: return@firstOrNull false
                val minutesUntilStart = ChronoUnit.MINUTES.between(now, startTime)
                // 课前 25 分钟 到 课后 5 分钟
                minutesUntilStart in -DISMISS_AFTER_START_MINUTES..TRIGGER_MINUTES
            }
    }
}
