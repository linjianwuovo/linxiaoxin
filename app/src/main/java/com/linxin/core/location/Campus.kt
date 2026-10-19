package com.linxin.core.location

import com.linxin.feature.sports.domain.WgsPoint
import kotlin.math.cos

/**
 * 校区范围。中心点是从我们自己地图上取的近似值，边界只用来给假定位圈一个活动范围。
 * 原来这两个数藏在 feature/running 的 RunningCampus 里，那块已随旧实现一起删掉。
 */
object Campus {

    const val CENTER_LATITUDE = 31.13893
    const val CENTER_LONGITUDE = 118.628131

    private const val METERS_PER_LAT_DEGREE = 111_320.0
    private const val HALF_HEIGHT = 600.0
    private const val HALF_WIDTH = 600.0

    fun metersToLatitude(meters: Double): Double = meters / METERS_PER_LAT_DEGREE

    fun metersToLongitude(meters: Double, atLat: Double = CENTER_LATITUDE): Double =
        meters / (METERS_PER_LAT_DEGREE * cos(Math.toRadians(atLat)))

    val minLatitude = CENTER_LATITUDE - metersToLatitude(HALF_HEIGHT)
    val maxLatitude = CENTER_LATITUDE + metersToLatitude(HALF_HEIGHT)
    val minLongitude = CENTER_LONGITUDE - metersToLongitude(HALF_WIDTH)
    val maxLongitude = CENTER_LONGITUDE + metersToLongitude(HALF_WIDTH)

    fun pointAt(latOffsetMeters: Double, lngOffsetMeters: Double): WgsPoint = WgsPoint(
        latitude = CENTER_LATITUDE + metersToLatitude(latOffsetMeters),
        longitude = CENTER_LONGITUDE + metersToLongitude(lngOffsetMeters),
    )
}
