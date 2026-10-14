package com.linxin.core.location

import android.location.Location
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

    fun stop() {
        stopJob()
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
