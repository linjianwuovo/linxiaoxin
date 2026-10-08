package com.linxin.core.messages

import com.linxin.R
import com.linxin.core.locale.AppText
import com.linxin.feature.checkin.data.CheckinRepository
import com.linxin.feature.holiday.data.HolidayRepository
import com.linxin.feature.news.data.NewsRepository
import com.linxin.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息轮询：一次拉三路数据，挑出没提醒过的发通知。
 *
 * 只读，不提交任何东西。三路都各自吞失败——某一路挂了不影响其它两路，
 * 也不要把失败记成"已提醒"，否则下次就永远不会再提醒了。
 */
@Singleton
class MessagePoller @Inject constructor(
    private val newsRepository: NewsRepository,
    private val checkinRepository: CheckinRepository,
    private val holidayRepository: HolidayRepository,
    private val prefs: MessagePrefs,
    private val notifier: MessageNotifier,
) {
    /** 返回本次真正发出的通知条数 */
    suspend fun runOnce(): Int {
        val settings = prefs.current()
        if (!settings.enabled) return 0
        val firstRun = settings.lastRunAt == 0L
        prefs.markRun()

        var sent = 0
        if (settings.news) sent += emit(MessageTag.NEWS, awaitNew(), quiet = firstRun)
        if (settings.checkin) sent += emit(MessageTag.CHECKIN, awaitCheckin())
        if (settings.returnToSchool) sent += emit(MessageTag.RETURN, awaitReturn())
        return sent
    }

    /** quiet = true 时只记 seen 不发通知：第一次开开关不把存量公告一次性推给你 */
    private suspend fun emit(tag: String, alerts: List<MessageAlert>, quiet: Boolean = false): Int {
        if (alerts.isEmpty()) return 0
        val seen = prefs.seenKeys(tag)
        val fresh = alerts.filter { it.key !in seen }
        if (fresh.isEmpty()) return 0
        if (quiet) {
            prefs.markSeen(tag, fresh.map { it.key }.toSet())
            return 0
        }
        val posted = fresh.filter { notifier.show(it) }
        // 只把真发出去的记成已提醒；发不出去（系统通知被关）下次继续试
        prefs.markSeen(tag, posted.map { it.key }.toSet())
        return posted.size
    }

    private suspend fun awaitNew(): List<MessageAlert> {
        val page = newsRepository.getNoticeList(page = 1).getOrNull() ?: return emptyList()
        return page.items.take(NEW_NEWS_MAX).map { item ->
            MessageAlert(
                tag = MessageTag.NEWS,
                key = item.id,
                title = AppText.str(R.string.msg_news_title),
                body = item.title.ifBlank { AppText.str(R.string.ui_035) },
                route = Routes.newsDetail(item.id),
            )
        }
    }

    private suspend fun awaitCheckin(): List<MessageAlert> {
        val tasks = checkinRepository.getTasks(page = 1).getOrNull() ?: return emptyList()
        return tasks.filter { !it.isSigned && it.isInOpenWindow }.map { task ->
            MessageAlert(
                tag = MessageTag.CHECKIN,
                // 同一天同一任务只提醒一次；跨天 key 变化，第二天还能再提醒
                key = "${task.taskDateId}_${LocalDate.now()}",
                title = AppText.str(R.string.msg_checkin_title),
                body = AppText.str(
                    R.string.msg_checkin_body,
                    task.taskName.ifBlank { AppText.str(R.string.title_dorm_checkin) },
                    task.startTime,
                    task.endTime,
                ),
                route = Routes.CHECKIN_LIST,
            )
        }
    }

    private suspend fun awaitReturn(): List<MessageAlert> {
        val list = holidayRepository.getRegistrationList(page = 1).getOrNull() ?: return emptyList()
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return list.mapNotNull { task ->
            val end = task.endDate.trim().substringBefore(' ').takeIf { it.length == 10 } ?: return@mapNotNull null
            val endDate = runCatching { LocalDate.parse(end, fmt) }.getOrNull() ?: return@mapNotNull null
            val days = endDate.toEpochDay() - today.toEpochDay()
            if (days !in 0..1) return@mapNotNull null
            MessageAlert(
                tag = MessageTag.RETURN,
                key = "${task.holidayId}_$end",
                title = AppText.str(R.string.msg_return_title),
                body = AppText.str(R.string.msg_return_body, task.name.ifBlank { end }, end),
                route = Routes.HOLIDAY_LIST,
            )
        }
    }

    private companion object {
        /** 首轮冷启动时最新的一批公告会一次性涌进来，压到 3 条以免刷屏 */
        const val NEW_NEWS_MAX = 3
    }
}
