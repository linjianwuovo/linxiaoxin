package com.linxin.core.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.linxin.feature.running.domain.TrackPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 把「FakeLocation 那类工具」做到我们自己家里：往系统注一条假 GPS。
 *
 * 为什么要内置：验跑步那条实况通知（进度条、胶囊文案）本来得真去跑一圈，而我们自己那个
 * 「路线模拟」是另一条路 —— 它直接生成轨迹提交，根本不经过跑步前台服务，拿它永远看不到实况。
 * 这里注进去的假点走的是系统 `GPS_PROVIDER`，我们自己的 `LocationProvider` 读的也正是它，
 * 于是跑步页、轨迹、实况通知这一整条链按真的一样走一遍。
 *
 * 一个硬前提：Android 只允许**开发者选项 → 选择模拟位置应用**里被选中的那个 App 注点。
 * 没被选中时 `addTestProvider` 直接抛 SecurityException，我们把它翻译成界面上一行状态，不假装能跑。
 *
 * 注进去的点是**整机级**的：别的 App 也会收到同一份假坐标。所以这颗开关不许自动恢复，
 * 用完必须点停止（退出这一页时也会兜底停掉）。
 */
@Singleton
class MockLocationFeeder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Status {
        IDLE,
        FEEDING,

        /** 开发者选项里没把安大信选成模拟位置应用 */
        NOT_SELECTED,
        NO_SERVICE,
    }

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** 最后一个注进去的点：界面上拿它证明"确实在动"，不靠猜 */
    private val _fedPoint = MutableStateFlow<TrackPoint?>(null)
    val fedPoint: StateFlow<TrackPoint?> = _fedPoint.asStateFlow()

    private val _fedCount = MutableStateFlow(0)
    val fedCount: StateFlow<Int> = _fedCount.asStateFlow()

    /**
     * 探一次能不能注：注册 test provider 再立刻摘掉，只为拿那个 SecurityException。
     * 不注点，所以不会把真 GPS 顶掉。
     */
    fun probe(): Status {
        if (_status.value == Status.FEEDING) return Status.FEEDING
        if (locationManager == null) return Status.NO_SERVICE
        val failure = runCatching { registerProvider() }.exceptionOrNull()
        runCatching { locationManager?.removeTestProvider(LocationManager.GPS_PROVIDER) }
        return if (failure == null) Status.IDLE else classify(failure)
    }

    /** 定在一个点上，每秒注一次，带一点点抖动免得轨迹是死的 */
    fun startHold(point: TrackPoint) {
        stopJob()
        if (!begin()) return
        _fedCount.value = 0
        job = scope.launch {
            var tick = 0
            while (isActive) {
                val jitter = if (tick % 4 == 0) 0.000008 else 0.0
                inject(point.latitude + jitter, point.longitude - jitter, accuracyMeters = 4.5f)
                delay(1000L)
                tick++
            }
        }
    }

    /**
     * 沿一条录好的路线走：节奏用模板自己存的 `durationSeconds` 除点数，
     * 那就是当初录这条路线的真实步频，不另外编一个速度。走完一圈从头再来，直到点停止。
     */
    fun startRoute(points: List<TrackPoint>, durationSeconds: Long) {
        stopJob()
        if (points.size < 2) return
        if (!begin()) return
        _fedCount.value = 0
        val seconds = if (durationSeconds > 0) durationSeconds else points.size.toLong()
        val perPointMs = (seconds * 1000L / points.size).coerceIn(200L, 4000L)
        job = scope.launch {
            var index = 0
            while (isActive) {
                val point = points[index % points.size]
                inject(point.latitude, point.longitude, accuracyMeters = 3.5f)
                delay(perPointMs)
                index++
            }
        }
    }

    fun stop() {
        stopJob()
        releaseProvider()
        _status.value = Status.IDLE
    }

    private fun begin(): Boolean {
        if (locationManager == null) {
            _status.value = Status.NO_SERVICE
            return false
        }
        val failure = runCatching { registerProvider() }.exceptionOrNull()
        if (failure != null) {
            _status.value = classify(failure)
            return false
        }
        _status.value = Status.FEEDING
        return true
    }

    private fun stopJob() {
        job?.cancel()
        job = null
    }

    @Suppress("DEPRECATION")
    private fun registerProvider() {
        val manager = locationManager ?: return
        if (!manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            // SDK 37 上这个重载是 provider + 七个布尔 + accuracy + powerUsage（老文档里那个
            // statusProvider 参数已经没了）。假点不需要卫星/网络/基站，海拔速度方向都给上。
            manager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                /* requiresNetwork */ false,
                /* requiresSatellite */ false,
                /* requiresCell */ false,
                /* hasMonetaryCost */ false,
                /* supportsAltitude */ true,
                /* supportsSpeed */ true,
                /* supportsBearing */ true,
                /* accuracy */ 1,
                /* powerUsage */ 1,
            )
        }
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        manager.setTestProviderStatus(
            LocationManager.GPS_PROVIDER,
            /* STATUS_AVAILABLE */ 1,
            null,
            System.currentTimeMillis(),
        )
    }

    @Suppress("DEPRECATION")
    private fun inject(latitude: Double, longitude: Double, accuracyMeters: Float) {
        val manager = locationManager ?: return
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            altitude = 20.0
            accuracy = accuracyMeters
            speed = 2.4f
            bearing = (fedCount.value * 3 % 360).toFloat()
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        val failure = runCatching { manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location) }.exceptionOrNull()
        if (failure != null) {
            // 注到一半被系统收回（比如他把模拟位置应用换成了别的 App）：立刻收手并摘掉 provider，
            // 不许留一条"看着还在注、其实已经不生效"的假状态
            stopJob()
            releaseProvider()
            _status.value = classify(failure)
            return
        }
        _fedPoint.value = TrackPoint(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = System.currentTimeMillis(),
        )
        _fedCount.value = _fedCount.value + 1
    }

    @Suppress("DEPRECATION")
    private fun releaseProvider() {
        val manager = locationManager ?: return
        runCatching { manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false) }
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    private fun classify(error: Throwable): Status {
        val message = error.message.orEmpty()
        return if (error is SecurityException || message.contains("mock", ignoreCase = true)) {
            Status.NOT_SELECTED
        } else {
            Status.NO_SERVICE
        }
    }
}
