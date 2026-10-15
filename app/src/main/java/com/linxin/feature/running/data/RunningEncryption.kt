package com.linxin.feature.running.data

import com.linxin.core.auth.RSAUtils
import com.linxin.feature.running.domain.TrackPoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RunningUploadPayload(
    val exerciseId: String,
    val memberId: String,
    val runningType: String,
    val startDate: String,
    val endDate: String,
    val mileKm: Double,
    val timeSeconds: Long,
    val speedKmh: Double,
    val runningRoute: List<TrackPoint>,
    val sourceInfo: String,
)

@Singleton
class RunningEncryption @Inject constructor() {

    fun encryptUploadPayload(payload: RunningUploadPayload): String {
        val routeArray = JSONArray()
        payload.runningRoute.forEach { point ->
            routeArray.put(
                JSONObject()
                    .put("latitude", formatCoordinate(point.latitude))
                    .put("longitude", formatCoordinate(point.longitude))
            )
        }

        val plainValues = linkedMapOf(
            "exerciseId" to payload.exerciseId,
            "memberId" to payload.memberId,
            "runningType" to payload.runningType,
            "startDate" to payload.startDate,
            "endDate" to payload.endDate,
            "mile" to formatDecimal(payload.mileKm),
            "time" to payload.timeSeconds.toString(),
            "speed" to formatDecimal(payload.speedKmh),
            "runningRoute" to routeArray.toString(),
            "sourceInfo" to payload.sourceInfo,
        )

        // 官方原生那一条 addExtraCheckNew.do 实测：10 个键名全部加密、9 个值加密，
        // 唯独轨迹那个值是**明文 JSON 数组**直接放进去了。我们原先连轨迹一起加密，
        // 服务器那边读里程/速率/时长都正常（说明解密是对的），偏偏轨迹解析不出来，
        // 结果整条记录被判「运动轨迹异常，无效」。所以这里跟官方对齐：轨迹不加密。
        val encryptedObject = JSONObject()
        plainValues.forEach { (key, value) ->
            val encryptedKey = RSAUtils.encryptSportData(key)
            val encryptedValue = if (key == "runningRoute") value else RSAUtils.encryptSportData(value)
            encryptedObject.put(encryptedKey, encryptedValue)
        }

        return JSONArray().put(encryptedObject).toString()
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}
