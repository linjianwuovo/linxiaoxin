package com.linxin.core.messages

import android.content.Context
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.linxin.core.notification.CourseNotificationScheduler
import com.linxin.core.notification.NextClassWindowStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 定时轮询的执行体。WorkManager 只保证"会跑"，不保证准点；
 * 真正的去重靠 [MessagePoller] 里的 seen 记录，所以多跑一次不会重复提醒。
 *
 * 两种节奏：平时是 [MessageScheduler.PERIODIC] 那条 15 分钟周期任务（WorkManager 的周期任务
 * 最短就 15 分钟，再短设不了）；快开课的那 15 分钟里改走 [MessageScheduler.FAST] 这条一次性任务，
 * 每跑完一次自己再续一条 60 秒后的，窗口一过就自己停。谁把窗口点亮见 NextClassWindowStore。
 */
@HiltWorker
class MessagePollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val poller: MessagePoller,
    private val nextClass: NextClassWindowStore,
    private val scheduler: CourseNotificationScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val isFastChain = inputData.getString(KEY_CHAIN) == CHAIN_FAST
        val inPollWindow = runCatching {
            nextClass.minutesUntilStart(MessageScheduler.FAST_WINDOW_MINUTES) != null
        }.getOrDefault(false)
        // 任何一路异常都不该让调度器被当成失败而无限退避。
        // 一分钟那条链跑在课前 25 分钟里（要刷倒计时数字），但消息检查本身只在他说的
        // 15 分钟内才提速，别为了刷数字每分钟去敲学校三次接口。
        if (!isFastChain || inPollWindow) runCatching { poller.runOnce() }

        val countdownAlive = runCatching { scheduler.refreshCountdown() }.getOrDefault(false)
        if (countdownAlive || inPollWindow) MessageScheduler.armFast(applicationContext)
        else MessageScheduler.syncFast(applicationContext, on = false)
        return Result.success()
    }

    companion object {
        const val PERIODIC = "linxin_message_poll"
        const val ONCE = "linxin_message_check_once"
        const val FAST = "linxin_message_poll_class_window"
        const val KEY_CHAIN = "chain"
        const val CHAIN_FAST = "fast"
    }
}

object MessageScheduler {

    /** 课前多少分钟开始提速 */
    const val FAST_WINDOW_MINUTES = 15L

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 开关变了就同步一次调度；关掉时把周期任务彻底取消，不留个空转的 */
    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(MessagePollWorker.PERIODIC)
            manager.cancelUniqueWork(MessagePollWorker.FAST)
            return
        }
        manager.enqueueUniquePeriodicWork(
            MessagePollWorker.PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MessagePollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build(),
        )
    }

    /**
     * 课前窗口开/关。开着就起来一条：KEEP 是故意的——窗口里前台每分钟都会喊一次，
     * 但只应该有一条排着等，不能每次喊都重新计时。
     */
    fun syncFast(context: Context, on: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!on) {
            manager.cancelUniqueWork(MessagePollWorker.FAST)
            return
        }
        manager.enqueueUniqueWork(
            MessagePollWorker.FAST,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MessagePollWorker>()
                .setConstraints(networkConstraints)
                .build(),
        )
    }

    /** 跑完一次之后由 worker 自己续下一条，60 秒后再来 */
    fun armFast(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MessagePollWorker.FAST,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MessagePollWorker>()
                .setInitialDelay(60, TimeUnit.SECONDS)
                .setInputData(workDataOf(MessagePollWorker.KEY_CHAIN to MessagePollWorker.CHAIN_FAST))
                .setConstraints(networkConstraints)
                .build(),
        )
    }

    /** 设置页"立即检查一次"用；和周期任务互不覆盖 */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MessagePollWorker.ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MessagePollWorker>()
                .setConstraints(networkConstraints)
                .build(),
        )
    }
}
