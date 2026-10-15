package com.linxin.feature.sports

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系换算 —— 这一套是为跑步那条链自己写的，不依赖别处的实现。
 *
 * 为什么要有它：学校服务端存的那条 `runningRoute` 是**百度 BD-09**（官方 APK 里
 * `SportMapActivity.initMap()` 直接 `option.setCoorType("bd09ll")`，SDK 出来就是百度坐标，
 * 客户端一次都不转；明细页也是 `JSON.parse(runningRoute)` 后原样 `new BMap.Point(lng, lat)` 画线）。
 * 而我们内置地图用的是高德街道瓦片，瓦片网格是 **GCJ-02**。Android 定位给的是 **WGS-84**。
 * 三个系对不上，就必须有一个地方把它们说清楚。
 *
 * 我们的约定：**本地一律按 WGS-84 存**（定位原始值），只有两条边界做换算 ——
 * 上给学校时 WGS-84 → BD-09；画到高德瓦片上时 WGS-84 → GCJ-02。
 */
object SportsCoords {

    private const val PI = Math.PI
    private const val X_PI = PI * 3000.0 / 180.0

    /** 克拉索夫斯基椭球长半轴 */
    private const val A = 6378245.0

    /** 偏心率平方 */
    private const val EE = 0.00669342162296594323

    /** 一度纬度约等于多少米（算偏移量够用，不做椭球级精度） */
    const val METERS_PER_DEGREE = 111_320.0

    data class Point(val latitude: Double, val longitude: Double)

    /** 中国境外不偏移（境外没有 GCJ-02 这套事） */
    private fun outsideChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    /** WGS-84 → GCJ-02（火星坐标） */
    fun wgsToGcj(lat: Double, lng: Double): Point {
        if (outsideChina(lat, lng)) return Point(lat, lng)
        var dLat = shiftLat(lng - 105.0, lat - 35.0)
        var dLng = shiftLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return Point(lat + dLat, lng + dLng)
    }

    /**
     * GCJ-02 → WGS-84：没有闭式解，用"把这一点的偏移减掉"再回代校正两次。
     * 合肥/芜湖这一带残差在分米级，画线、量距离都够。
     */
    fun gcjToWgs(lat: Double, lng: Double): Point {
        if (outsideChina(lat, lng)) return Point(lat, lng)
        var wLat = lat
        var wLng = lng
        repeat(3) {
            val back = wgsToGcj(wLat, wLng)
            wLat += lat - back.latitude
            wLng += lng - back.longitude
        }
        return Point(wLat, wLng)
    }

    /** GCJ-02 → BD-09 */
    fun gcjToBd(lat: Double, lng: Double): Point {
        val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = Math.atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
        return Point(z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    /** BD-09 → GCJ-02 */
    fun bdToGcj(lat: Double, lng: Double): Point {
        val x = lng - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = Math.atan2(y, x) - 0.000003 * cos(x * X_PI)
        return Point(z * sin(theta), z * cos(theta))
    }

    /** WGS-84 → BD-09：交给学校的那条轨迹用这个 */
    fun wgsToBd(lat: Double, lng: Double): Point {
        val gcj = wgsToGcj(lat, lng)
        return gcjToBd(gcj.latitude, gcj.longitude)
    }

    /** BD-09 → WGS-84：把学校存的那条画回我们自己地图上用这个 */
    fun bdToWgs(lat: Double, lng: Double): Point {
        val gcj = bdToGcj(lat, lng)
        return gcjToWgs(gcj.latitude, gcj.longitude)
    }

    /** 两点大圆距离（米） */
    fun metersBetween(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
    ): Double {
        val radLat1 = lat1 * PI / 180.0
        val radLat2 = lat2 * PI / 180.0
        val dLat = radLat2 - radLat1
        val dLng = (lng2 - lng1) * PI / 180.0
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(radLat1) * cos(radLat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * 6_371_000.0 * kotlin.math.asin(sqrt(h))
    }

    /** 纬度方向挪 dMeters 米对应多少度 */
    fun latDelta(dMeters: Double): Double = dMeters / METERS_PER_DEGREE

    /** 经度方向挪 dMeters 米对应多少度（要按纬度压缩） */
    fun lngDelta(dMeters: Double, atLatitude: Double): Double =
        dMeters / (METERS_PER_DEGREE * cos(atLatitude * PI / 180.0))

    fun isSane(lat: Double, lng: Double): Boolean =
        abs(lat) <= 90.0 && abs(lng) <= 180.0 && !(lat == 0.0 && lng == 0.0)

    private fun shiftLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun shiftLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
