package com.linxin.core.location

import android.location.Location
import com.linxin.feature.running.domain.GeoDistance
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

    /** 这一趟真发出去的点，界面拿去画"跑到哪儿了" */
    private val _trail = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trail: StateFlow<List<TrackPoint>> = _trail.asStateFlow()

    /** 开喂之后 `getLastKnownLocation()` 走这个；没开返回 null，让调用方回到真定位 */
    fun snapshot(): Location? = if (_isOn.value) last else null

    /**
     * 把一条稀疏的线打密：地图上点出来的拐点之间可能隔上百米，直接发会被
     * `RunningTracker` 那道"单段大于 120 米不算"的过滤丢掉，距离就一顿一顿的。
     * 默认每 2 米一个点 —— 跟真 GPS 一秒一个点的密度一个量级。
     */
    fun densify(points: List<TrackPoint>, stepMeters: Double = 2.0): List<TrackPoint> {
        if (points.size < 2) return points
        val out = ArrayList<TrackPoint>(points.size * 8)
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val meters = GeoDistance.metersBetween(a.latitude, a.longitude, b.latitude, b.longitude)
            val steps = (meters / stepMeters).toInt().coerceAtLeast(1)
            for (step in 0 until steps) {
                val ratio = step.toDouble() / steps
                out += TrackPoint(
                    latitude = a.latitude + (b.latitude - a.latitude) * ratio,
                    longitude = a.longitude + (b.longitude - a.longitude) * ratio,
                )
            }
        }
        out += points.last()
        return out
    }

    /**
     * 沿一条线走：节奏用这条线自己存的 `durationSeconds` 除点数 ——
     * 那就是当初录/画这条线时的用时，不另外编一个速度。走完一圈从头再来，直到点停止。
     */
    fun startRoute(points: List<TrackPoint>, durationSeconds: Long) {
        stopJob()
        if (points.size < 2) return
        isOnNow = true
        _isOn.value = true
        _fedCount.value = 0
        _trail.value = emptyList()
        val seconds = if (durationSeconds > 0) durationSeconds else points.size.toLong()
        val perPointMs = (seconds * 1000L / points.size).coerceIn(200L, 4000L)
        val wander = Wander(random = java.util.Random(seed()))
        job = scope.launch {
            var index = 0
            while (isActive) {
                val point = points[index % points.size]
                val off = wander.next()
                emit(
                    point.latitude + off.first / METERS_PER_LAT_DEGREE,
                    point.longitude + off.second /
                        (METERS_PER_LAT_DEGREE * kotlin.math.cos(Math.toRadians(point.latitude))),
                    accuracy = (3.0 + kotlin.math.abs(off.first) / 2.0).toFloat(),
                )
                delay(perPointMs)
                index++
            }
        }
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

        private const val TRAIL_MAX_POINTS = 1500

        /** 一个纬度度约等于这么多米，喂点算偏移用 */
        private const val METERS_PER_LAT_DEGREE = 111_320.0

        private fun seed(): Long = System.nanoTime()
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
        // 最多留 1500 个点（约 25 分钟），够看一屏，也不至于把内存吃满
        _trail.value = (_trail.value + TrackPoint(latitude, longitude)).takeLast(TRAIL_MAX_POINTS)
    }
}

/**
 * 随机偏移：真 GPS 从来不是贴着线上走的，是一米一米地晃。
 * 所以这里不是每点独立撒噪声（那样相邻点可能比 1.5 米还近，会被 tracker 的抖动过滤吃掉，
 * 距离反而不涨），而是让偏移本身做一次缓慢的随机游走：每步最多挪 0.6 米，
 * 离理想线最远 2.5 米 —— 看着就是"沿这条路跑，但不在一条直线上"。
 */
private class Wander(private val random: java.util.Random) {

    private var north = 0.0
    private var east = 0.0

    fun next(): Pair<Double, Double> {
        north = (north + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        east = (east + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        return north to east
    }

    private companion object {
        const val MAX_OFFSET = 2.5
    }
}
