package com.linxin.core.location

import android.location.Location
import com.linxin.feature.running.domain.RunningCampus
import com.linxin.feature.running.domain.TrackPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 应用内部的假 GPS：只在安大信自己进程里生效，不碰系统、不碰别的 App。
 *
 * 为什么不走系统那套 test provider：那条路要他在开发者选项里把安大信选成"模拟位置应用"，
 * 而且注出去的点会同时给别的 App（包括安小信）—— 那是整机级的副作用。
 * 这里改成只喂给我们自己的 `LocationProvider`：开的时候 `locationUpdates()` /
 * `getLastKnownLocation()` 拿到的就是这份轨迹，关掉就立刻回到真 GPS，别的 App 完全不受影响。
 *
 * 用途：验跑步那条实况通知（进度条、胶囊文案）不用真去跑一圈。
 * 注意它**不会**让学校那边看到任何东西 —— 提交、打卡那些写操作还是要人自己点。
 */
@Singleton
class InternalGpsFeed @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var last: Location? = null

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _point = MutableStateFlow<TrackPoint?>(null)
    val point: StateFlow<TrackPoint?> = _point.asStateFlow()

    private val _fedCount = MutableStateFlow(0)
    val fedCount: StateFlow<Int> = _fedCount.asStateFlow()

    /** 开喂之后 `getLastKnownLocation()` 走这个；没开返回 null，让调用方回到真定位 */
    fun snapshot(): Location? = if (_isOn.value) last else null

    /** 定在一个点上，每秒挪一点点，免得轨迹是死的 */
    fun startHold(point: TrackPoint) {
        stopJob()
        isOnNow = true
        _isOn.value = true
        _fedCount.value = 0
        job = scope.launch {
            var tick = 0
            while (isActive) {
                val jitter = if (tick % 4 == 0) 0.000008 else 0.0
                emit(point.latitude + jitter, point.longitude - jitter, accuracy = 4.5f)
                delay(1000L)
                tick++
            }
        }
    }

    /**
     * 沿一条录好的路线走。节奏用模板自己存的 `durationSeconds` 除点数 ——
     * 那就是当初录这条路线的真实步频，不另外编一个速度。走完一圈从头再来，直到点停止。
     */
    fun startRoute(points: List<TrackPoint>, durationSeconds: Long) {
        stopJob()
        if (points.size < 2) return
        isOnNow = true
        _isOn.value = true
        _fedCount.value = 0
        val seconds = if (durationSeconds > 0) durationSeconds else points.size.toLong()
        val perPointMs = (seconds * 1000L / points.size).coerceIn(200L, 4000L)
        job = scope.launch {
            var index = 0
            while (isActive) {
                val point = points[index % points.size]
                emit(point.latitude, point.longitude, accuracy = 3.5f)
                delay(perPointMs)
                index++
            }
        }
    }

    /**
     * 绕圈跑：在学校中心旁边现造一圈 320 米的矩形环线，一秒发一个点、每点约 2 米（≈7.2 km/h），
     * 走完一圈接着走，直到点停止。
     *
     * 为什么要单独一档：定住那种模式下相邻点只差 0.9 米，会被 `RunningTracker` 那道
     * "小于 1.5 米不算一段"的抖动过滤全吃掉，距离永远显示 0.00 —— 想虚拟跑就必须真的在动。
     * 7.2 km/h 也正好落在学校那套 `minSpeed=2 / maxSpeed=15` 的配速窗口里。
     */
    fun startLaps() {
        stopJob()
        val lap = lapRingMeters()
        if (lap.size < 4) return
        isOnNow = true
        _isOn.value = true
        _fedCount.value = 0
        job = scope.launch {
            var index = 0
            while (isActive) {
                val point = lap[index % lap.size]
                emit(point.latitude, point.longitude, accuracy = 3.5f)
                delay(LAP_TICK_MS)
                index++
            }
        }
    }

    /** 320 米一圈的环线，相邻两点约 2 米（米制偏移直接走 RunningCampus，省得自己算球面） */
    private fun lapRingMeters(): List<TrackPoint> {
        val corners = listOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, LAP_LONG_METERS),
            doubleArrayOf(-LAP_SHORT_METERS, LAP_LONG_METERS),
            doubleArrayOf(-LAP_SHORT_METERS, 0.0),
        )
        val out = ArrayList<TrackPoint>(corners.size * (LAP_STEP_METERS.toInt() + 1))
        for (i in corners.indices) {
            val from = corners[i]
            val to = corners[(i + 1) % corners.size]
            val length = kotlin.math.hypot(to[0] - from[0], to[1] - from[1])
            val steps = (length / LAP_STEP_METERS).toInt().coerceAtLeast(1)
            for (s in 0 until steps) {
                val ratio = s.toDouble() / steps
                out += RunningCampus.pointAt(
                    from[0] + (to[0] - from[0]) * ratio,
                    from[1] + (to[1] - from[1]) * ratio,
                )
            }
        }
        return out
    }

    fun stop() {
        stopJob()
        isOnNow = false
        _isOn.value = false
        last = null
        _point.value = null
    }

    /** 喂点时给 `LocationProvider.locationUpdates()` 用的流：按调用方要的间隔回吐 */
    fun updates(intervalMs: Long): Flow<Location> = callbackFlow {
        while (isActive) {
            snapshot()?.let { trySend(it) }
            delay(intervalMs)
        }
    }

    private fun stopJob() {
        job?.cancel()
        job = null
    }

    companion object {
        /**
         * 给不吃 Hilt 的那几处界面（更多页那一行）留的直读开关。
         * 状态流才是准的，这个只用来在页面重新可见时标一下"还在喂"。
         */
        @Volatile
        var isOnNow: Boolean = false
            private set

        /** 环线两条边：160 米 × 96 米，一圈 512 米，凑不进 400 米标准也无所谓，绕着走就行 */
        private const val LAP_LONG_METERS = 160.0
        private const val LAP_SHORT_METERS = 96.0

        /** 相邻两点约 2 米：刚好越过 `RunningTracker` 那道 1.5 米的抖动过滤 */
        private const val LAP_STEP_METERS = 2.0

        /** 一步一秒 → 约 7.2 km/h，在学校 2–15 的配速窗口内 */
        private const val LAP_TICK_MS = 1000L
    }

    private fun emit(latitude: Double, longitude: Double, accuracy: Float) {
        val location = Location("linxin-internal").apply {
            this.latitude = latitude
            this.longitude = longitude
            altitude = 20.0
            this.accuracy = accuracy
            speed = 2.4f
            bearing = (_fedCount.value * 3 % 360).toFloat()
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
        last = location
        _point.value = TrackPoint(latitude = latitude, longitude = longitude, timestampMillis = System.currentTimeMillis())
        _fedCount.value = _fedCount.value + 1
    }
}
