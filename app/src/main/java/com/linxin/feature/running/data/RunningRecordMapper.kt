package com.linxin.feature.running.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.linxin.feature.running.domain.MaintainPage
import com.linxin.feature.running.domain.MaintainRow
import com.linxin.feature.running.domain.RunDetail
import com.linxin.feature.running.domain.RunRecordPage
import com.linxin.feature.running.domain.RunRecordRow
import com.linxin.feature.running.domain.RunTotals
import com.linxin.feature.running.domain.TrackPoint

/**
 * 体育那几条查询接口的解析。
 *
 * 列表的字段是抓包对出来的实样；明细那条我们只见过官方渲染函数里的变量名、
 * 没见过真实报文（这台账号没有可看的无效记录明细），所以取值一律按候选键名挨个试，
 * 试不到就留空 —— 界面上宁可少一行，不要瞎编一个值出来。
 */
internal object RunningRecordMapper {

    fun records(json: JsonObject): RunRecordPage {
        val rows = json.array("rows").mapNotNull { it.asObjectOrNull() }.map { row ->
            RunRecordRow(
                id = row.str("id"),
                name = row.str("exerciseName", "name"),
                type = row.str("exerciseType", "type"),
                mileKm = row.str("exerciseMile", "mile").toDoubleOrZero(),
                coefficient = row.str("exerciseMileCoefficient", "coefficient"),
                startText = row.str("startDate", "startTime"),
                endText = row.str("endDate", "endTime"),
                durationText = row.str("exerciseTime", "duration").ifBlank { null },
                checkTimeText = row.str("checkTime", "dkTime").ifBlank { null },
                stateCode = row.str("exerciseState", "state"),
            )
        }
        return RunRecordPage(rows = rows, total = json.str("total").toIntOrNull() ?: rows.size)
    }

    fun totals(json: JsonObject): RunTotals {
        val body = json.obj("totalInfo", "data") ?: json
        return RunTotals(
            totalMileKm = body.str("totalMile", "mile").toDoubleOrZero(),
            groupMileKm = body.str("groupMile").toDoubleOrZero(),
            groupCount = body.str("groupCnt").toIntOrNull() ?: 0,
            extraMileKm = body.str("extraMile").toDoubleOrZero(),
            morningMileKm = body.str("morningExtraMile").toDoubleOrZero(),
        )
    }

    fun detail(json: JsonObject, checkId: String): RunDetail {
        val body = json.obj("basicInfo", "extraCheck", "detail")
            ?: json.array("rows").let { a -> if (a.size() > 0) a[0].asObjectOrNull() else null }
            ?: json.obj("data")
            ?: json
        val route = parseTrack(body.str("runningRoute", "runningRoutes", "path"))
        return RunDetail(
            checkId = body.str("checkId", "id").ifBlank { checkId },
            titleText = listOf(
                body.str("startDate", "startTime"),
                body.str("endDate", "endTime"),
            ).filter { it.isNotBlank() }.joinToString("-"),
            mileKm = body.str("mile", "exerciseMile").toDoubleOrZero(),
            speedKmh = body.str("speed", "exerciseSpeed").toDoubleOrZero(),
            durationText = body.str("time", "duration", "exerciseTime"),
            stateCode = body.str("state", "exerciseState"),
            remark = body.str("remark", "reason", "msg"),
            trackPoints = route,
            handledAt = body.str("opDate", "handleTime", "dealDate"),
            handledBy = body.str("opUser", "handleUser", "dealUser"),
            handleRemark = body.str("opRemark", "handleRemark"),
            appliedAt = body.str("applyDate", "applyTime"),
            applyReason = body.str("applyReason", "applyResaon"),
            auditedAt = body.str("auditDate", "auditTime"),
            auditor = body.str("auditUser", "auditor"),
            auditOpinion = when (body.str("auditState")) {
                "1" -> "同意"
                "2" -> "驳回"
                "" -> ""
                else -> "待审核"
            },
            auditRemark = body.str("auditRemark"),
            auditState = body.str("auditState"),
        )
    }

    fun maintain(json: JsonObject, queryType: Int): MaintainPage {
        val rows = json.array("rows").mapNotNull { it.asObjectOrNull() }.map { row ->
            MaintainRow(
                id = row.str("id", "applyId"),
                queryType = queryType,
                exerciseType = row.str("exerciseType", "type"),
                timeText = row.str("startDate", "exerciseTime", "checkTime"),
                mileKm = row.str("exerciseMile", "mile").toDoubleOrZero(),
                durationText = row.str("time", "duration"),
                speedKmh = row.str("speed").toDoubleOrZero(),
                exerciseState = row.str("exerciseState", "state"),
                auditState = row.str("auditState"),
                reason = row.str("applyReason", "reason"),
            )
        }
        return MaintainPage(rows = rows, total = json.str("total").toIntOrNull() ?: rows.size)
    }

    /** 明细里的轨迹：官方存的是 BD-09 的 `[{"latitude":..,"longitude":..}]` 字符串 */
    private fun parseTrack(raw: String): List<TrackPoint> {
        val text = raw.trim()
        if (!text.startsWith("[")) return emptyList()
        val array = runCatching { JsonParser.parseString(text).asJsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val point = element.asObjectOrNull() ?: return@mapNotNull null
            val lat = point.str("latitude", "lat").toDoubleOrNull() ?: return@mapNotNull null
            val lng = point.str("longitude", "lng").toDoubleOrNull() ?: return@mapNotNull null
            TrackPoint(latitude = lat, longitude = lng)
        }
    }

    private fun JsonObject.array(key: String): JsonArray =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonObject.obj(vararg keys: String): JsonObject? {
        keys.forEach { key ->
            get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.let { return it }
        }
        return null
    }

    /** 官方这些接口里数字常常是字符串，统一按字符串取再各自转 */
    private fun JsonObject.str(vararg keys: String): String {
        keys.forEach { key ->
            get(key)?.takeUnless { it.isJsonNull }?.let { element ->
                val text = element.plain()
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    private fun JsonElement.plain(): String = when {
        isJsonNull -> ""
        isJsonPrimitive -> asString
        else -> toString()
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? =
        this?.takeIf { it.isJsonObject }?.asJsonObject

    private fun String.toDoubleOrZero(): Double = trim().toDoubleOrNull() ?: 0.0
}
