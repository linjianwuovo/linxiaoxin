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
     * 沿一条线跑，**三秒发一个点**（跟官方 `setScanSpan(3000)` 一个频率），
     * 一次走多远由配速决定 —— 这样"采集频率"和"速度"两件事分开，不会因为发得勤就显得跑得飞快。
     * 点与点之间按弧长在原始线上取（不是按下标跳），
     * 走到线尾就**倒着走回来**（像真人跑一段折返），绝不从终点瞬移回起点 ——
     * 那种一秒跳几百米的断点，服务器一眼就当轨迹异常。
     */
    fun startRoute(points: List<TrackPoint>, paceKmh: Double) {
        stopJob()
        val sampler = LineSampler(points)
        if (sampler.totalMeters < 20.0) return
        val target = (paceKmh / 3.6).coerceIn(SPEED_MIN_MS, SPEED_MAX_MS)
        // 一秒能走多远在目标上下小幅漂，别锁死成一个匀速的机器步；
        // 上下各留 15%，并且始终不越学校那条 2–15 km/h
        val speedLow = (target * 0.85).coerceAtLeast(SPEED_MIN_MS)
        val speedHigh = (target * 1.15).coerceAtMost(SPEED_MAX_MS)
        isOnNow = true
        _isOn.value = true
        _fedCount.value = 0
        _trail.value = emptyList()
        val wander = Wander(java.util.Random(System.nanoTime()))
        job = scope.launch {
            var traveled = 0.0
            var speed = target
            var forward = true
            while (isActive) {
                val base = sampler.at(traveled)
                val off = wander.next()
                emit(
                    base.latitude + off.first / METERS_PER_LAT_DEGREE,
                    base.longitude + off.second /
                        (METERS_PER_LAT_DEGREE * kotlin.math.cos(Math.toRadians(base.latitude))),
                    accuracy = (3.0 + kotlin.math.abs(off.first) / 2.0).toFloat(),
                )
                speed = (speed + (kotlin.random.Random.nextDouble() - 0.5) * 0.3).coerceIn(speedLow, speedHigh)
                traveled += (speed * SECONDS_PER_TICK).let { if (forward) it else -it }
                // 到端点反射回来，不瞬移
                if (traveled >= sampler.totalMeters) {
                    traveled = 2.0 * sampler.totalMeters - traveled
                    forward = false
                } else if (traveled <= 0.0) {
                    traveled = -traveled
                    forward = true
                }
                delay(TICK_MS)
            }
        }
    }

    /**
     * 按弧长在一条折线上取点：先累出每段长度，取的时候二分太浪费，直接线性找段。
     * 拐点稀疏也没关系 —— 我们是按"走了多少米"取位置，不是按第几个拐点跳。
     */
    private class LineSampler(source: List<TrackPoint>) {

        private val points: List<TrackPoint> = source
        private val cumulative: DoubleArray
        val totalMeters: Double

        init {
            val list = if (points.size >= 2) points else emptyList()
            cumulative = DoubleArray(list.size)
            var sum = 0.0
            for (i in 1 until list.size) {
                sum += GeoDistance.metersBetween(
                    list[i - 1].latitude, list[i - 1].longitude,
                    list[i].latitude, list[i].longitude,
                )
                cumulative[i] = sum
            }
            totalMeters = sum
        }

        fun at(meters: Double): TrackPoint {
            val list = points
            if (list.size < 2) return TrackPoint(0.0, 0.0)
            val target = meters % totalMeters
            var index = 1
            while (index < list.size && cumulative[index] < target) index++
            val to = if (index >= list.size) list.lastIndex else index
            val from = (to - 1).coerceAtLeast(0)
            val segLength = (cumulative[to] - cumulative[from]).coerceAtLeast(0.001)
            val ratio = ((target - cumulative[from]) / segLength).coerceIn(0.0, 1.0)
            val a = list[from]
            val b = list[to]
            return TrackPoint(
                a.latitude + (b.latitude - a.latitude) * ratio,
                a.longitude + (b.longitude - a.longitude) * ratio,
            )
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

        /**
         * 三秒一个点 —— 照官方那条 `LocationClientOption.setScanSpan(3000)` 来。
         * 2026-10-14 dexdump 扒出来的：官方原生跑步页（SportMapActivity）就是 3 秒问一次位置，
         * 回调里每来一个就 add 进 points，不做距离筛选。
         */
        private const val TICK_MS = 3000L

        /** 一个 tick 多少秒，走多远要按它乘 */
        private const val SECONDS_PER_TICK = TICK_MS / 1000.0

        /** 学校那条窗口：2–15 km/h，换算成米每秒 */
        private const val SPEED_MIN_MS = 0.6
        private const val SPEED_MAX_MS = 4.2

        /** 一个纬度度约等于这么多米，喂点算偏移用 */
        private const val METERS_PER_LAT_DEGREE = 111_320.0

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
