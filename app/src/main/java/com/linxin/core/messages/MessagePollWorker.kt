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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 定时轮询的执行体。WorkManager 只保证"会跑"，不保证准点；
 * 真正的去重靠 [MessagePoller] 里的 seen 记录，所以多跑一次不会重复提醒。
 */
@HiltWorker
class MessagePollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val poller: MessagePoller,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 任何一路异常都不该让调度器被当成失败而无限退避
        runCatching { poller.runOnce() }
        return Result.success()
    }

    companion object {
        const val PERIODIC = "linxin_message_poll"
        const val ONCE = "linxin_message_check_once"
    }
}

object MessageScheduler {

    /** 开关变了就同步一次调度；关掉时把周期任务彻底取消，不留个空转的 */
    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(MessagePollWorker.PERIODIC)
            return
        }
        manager.enqueueUniquePeriodicWork(
            MessagePollWorker.PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MessagePollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** 设置页"立即检查一次"用；和周期任务互不覆盖 */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MessagePollWorker.ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MessagePollWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }
}
