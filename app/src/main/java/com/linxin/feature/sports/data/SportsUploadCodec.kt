package com.linxin.feature.sports.data

import android.util.Base64
import com.linxin.feature.sports.SportsCoords
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Cipher

/**
 * 跑步上传那条链的拼包 —— 形状是从官方 APK 的 `OkHttpHelper.upLoadSport(String,String)`
 * 逐条指令读出来的，不是猜的：
 *
 *   body 单键 `list` = JSON 数组、1 个对象、10 个键，顺序固定
 *     exerciseId, memberId, runningType, startDate, endDate, mile, time, speed, runningRoute, sourceInfo
 *   10 个**键名全部** RSA 加密（`Rsa` 那一路（运动用的是 APK 里另一把公钥，尾号 xZC1gwIDAQAB），密文 172 字符 base64 = RSA-1024）
 *   值里 **9 个加密，唯独 runningRoute 是明文 JSON 数组**
 *   exerciseId 为空或字符串 "null" 时：put(加密后的键名, "")，值不加密
 *   `sourceInfo` = `Build.MODEL`
 *
 * 抓包对照实证：同一台机官方上传解出来第 9 个值是
 * `[{"latitude":31.146501,"longitude":118.635061},…]`（明文），其余值是 172 字符 base64。
 * 我们之前把轨迹也加密了，服务器 mile/speed/time 都解得开、偏偏轨迹解不开，
 * 记录建出来了却画不出线，判「运动轨迹异常，无效」。**这个字段以后不许再跟着加密。**
 */
object SportsUploadCodec {

    /** 官方 APK 里跑步数据用的那把公钥（RSA-1024，X.509 SubjectPublicKeyInfo） */
    private const val SPORTS_PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQChZH5KNDLzeINf6N+fNMAv4BJb4J" +
            "nC3JTvSWAS4IVyGVI1xXQW4odl0hXl1AUuR4FbVFDCEIjp+YEeLELPMiTOlAPg9Ps7" +
            "vp4wrrB/J7bHdlkyx+b8YztPOlU/wDzqL5H5sxmivpKrHvBRUCSMfBpN2czkuUJhbE" +
            "9zgJMyxZC1gwIDAQAB"

    private const val TRANSFORM = "RSA/None/PKCS1Padding"

    /** 1024 位密钥一块 128 字节，PKCS#1 v1.5 占 11 字节 */
    private const val BLOCK = 117

    /** 官方原生上传表单里那五个明文参数之一；APK 里就是硬编码 32 个 "1"，不是设备号也不是密钥 */
    const val APP_ID = "11111111111111111111111111111111"

    data class Run(
        val exerciseId: String,
        val memberId: String,
        val runningType: String,
        val startText: String,
        val endText: String,
        val mileKm: Double,
        val timeSeconds: Long,
        val speedKmh: Double,
        /** WGS-84，拼包时统一换成学校那套 BD-09 */
        val trackWgs: List<Pair<Double, Double>>,
        val deviceModel: String,
    )

    /** 拼出 `list` 那个字段的值 */
    fun buildListField(run: Run): String {
        val array = JSONArray()
        val obj = JSONObject()

        // 顺序照官方，一个都不许调
        putField(obj, "exerciseId", run.exerciseId)
        putField(obj, "memberId", run.memberId)
        putField(obj, "runningType", run.runningType)
        putField(obj, "startDate", run.startText)
        putField(obj, "endDate", run.endText)
        putField(obj, "mile", "%.2f".format(run.mileKm))
        putField(obj, "time", run.timeSeconds.toString())
        putField(obj, "speed", "%.2f".format(run.speedKmh))
        obj.put(encrypt("runningRoute"), trackJson(run.trackWgs))
        putField(obj, "sourceInfo", run.deviceModel)

        array.put(obj)
        return array.toString()
    }

    /** 官方对空值的处理：键名照加密，值直接给空串（不加密） */
    private fun putField(obj: JSONObject, key: String, value: String) {
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.equals("null", ignoreCase = true)) {
            obj.put(encrypt(key), "")
        } else {
            obj.put(encrypt(key), encrypt(normalized))
        }
    }

    /**
     * 轨迹 JSON：官方存的是 BD-09、六位小数、键顺序 latitude 在前。
     * 抓到的实样长这样：`[{"latitude":31.146501,"longitude":118.635061},…]`
     */
    private fun trackJson(trackWgs: List<Pair<Double, Double>>): String {
        val sb = StringBuilder("[")
        trackWgs.forEachIndexed { index, (lat, lng) ->
            val bd = SportsCoords.wgsToBd(lat, lng)
            if (index > 0) sb.append(",")
            sb.append("{\"latitude\":")
                .append(String.format(Locale.US, "%.6f", bd.latitude))
                .append(",\"longitude\":")
                .append(String.format(Locale.US, "%.6f", bd.longitude))
                .append("}")
        }
        return sb.append("]").toString()
    }

    fun encrypt(plain: String): String {
        val keyBytes = Base64.decode(SPORTS_PUBLIC_KEY, Base64.DEFAULT)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        val data = plain.toByteArray(Charsets.UTF_8)
        val out = ArrayList<Byte>(data.size + 256)
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + BLOCK, data.size)
            out.addAll(cipher.doFinal(data, offset, end - offset).toList())
            offset = end
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
