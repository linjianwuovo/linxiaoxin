package com.linxin.feature.sports.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.linxin.feature.sports.domain.AppealPage
import com.linxin.feature.sports.domain.AppealRow
import com.linxin.feature.sports.domain.RecordDetail
import com.linxin.feature.sports.domain.RecordPage
import com.linxin.feature.sports.domain.RecordRow
import com.linxin.feature.sports.domain.RecordTotals
import com.linxin.feature.sports.domain.RunRules
import com.linxin.feature.sports.domain.WgsPoint

/**
 * 体育那几个接口的响应 → 模型。
 *
 * 外壳统一是 `{flag,result,rows,total,data}`。取值一律"候选键名挨个试"：
 * 列表和约束那几条我们有真实报文可核，明细和维护记录两条**没有**（这台测试账号
 * 列表里没有可看的无效记录，`getApplyRecordList` 的 rows 一直是空），
 * 所以那两条的键名是从官方渲染函数里读出来的变量名推的，试不到就留空、不瞎补。
 */
internal object SportsParse {

    /** 服务端说没成功时抛这个，界面上直接显示 message */
    class SportsApiException(message: String) : Exception(message)

    fun requireFlag(json: JsonObject, fallback: String) {
        val flag = json.get("flag") ?: return
        val ok = when {
            flag.isJsonNull -> true
            flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean -> flag.asBoolean
            else -> flag.asString.equals("true", ignoreCase = true)
        }
        if (!ok) {
            throw SportsApiException(text(json, "result", "msg").ifBlank { fallback })
        }
    }

    /**
     * 约束 + 汇总：`extraDetailInfo` 一条给全，`extraInfo` 给 extraId 和计划里程，
     * `currentTerm` 给学期标题，`checkDsStudent` 给年级。
     */
    fun rules(
        grade: String,
        planJson: JsonObject?,
        detailJson: JsonObject?,
        termJson: JsonObject?,
    ): RunRules {
        // 官方把这两坨塞在 data 下面（data.extraInfo / data.extraDetail），根上没有
        val extraInfo = section(planJson, "extraInfo")
        val detail = section(detailJson, "extraDetail")
        val year = text(termJson ?: JsonObject(), "XN")
        val termNo = text(termJson ?: JsonObject(), "XQ")
        val termTitle = if (year.isBlank()) {
            ""
        } else {
            val next = (year.toIntOrNull() ?: 0) + 1
            "$year-$next-${termNo.ifBlank { "1" }}"
        }
        return RunRules(
            grade = grade,
            termTitle = termTitle,
            planStart = text(detail, "extraStartDate"),
            planEnd = text(detail, "extraEndDate"),
            planMileKm = num(pick(detail, "planMile").ifBlank { pick(extraInfo, "planMile") }),
            completeMileKm = num(pick(detail, "completeMile").ifBlank { text(extraInfo, "completeMile") }),
            surplusMileKm = num(pick(detail, "surplusMile")),
            todayMileKm = num(pick(detail, "todayMile")),
            todayDate = pick(detail, "todayDate"),
            bestMileKm = num(pick(detail, "maxMile")),
            bestMileDate = pick(detail, "maxMileDate"),
            singleMinKm = num(pick(detail, "mixOnceMile"), 1.0),
            dailyMaxKm = num(pick(detail, "maxDayMile"), 3.0),
            minSpeedKmh = num(pick(detail, "minSpeed"), 2.0),
            maxSpeedKmh = num(pick(detail, "maxSpeed"), 15.0),
            memberId = pick(detail, "memberId"),
            extraId = pick(extraInfo, "extraId"),
        )
    }

    fun totals(json: JsonObject): RecordTotals {
        val body = json.child("totalInfo") ?: json
        return RecordTotals(
            totalMileKm = num(pick(body, "totalMile")),
            groupMileKm = num(pick(body, "groupMile")),
            groupCount = pick(body, "groupCnt").toIntOrNull() ?: 0,
            extraMileKm = num(pick(body, "extraMile")),
            morningMileKm = num(pick(body, "morningExtraMile")),
        )
    }

    fun records(json: JsonObject): RecordPage {
        val rows = json.rows().map { row ->
            RecordRow(
                id = pick(row, "id"),
                name = pick(row, "exerciseName").ifBlank { "跑步" },
                type = pick(row, "exerciseType"),
                mileKm = num(pick(row, "exerciseMile")),
                coefficient = pick(row, "exerciseMileCoefficient"),
                startText = pick(row, "startDate"),
                endText = pick(row, "endDate"),
                stateCode = pick(row, "exerciseState"),
            )
        }
        return RecordPage(rows, json.str("total").toIntOrNull() ?: rows.size)
    }

    fun detail(json: JsonObject, checkId: String): RecordDetail {
        val body = json.child("basicInfo") ?: json.child("extraCheck") ?: json.firstRow() ?: json
        return RecordDetail(
            checkId = pick(body, "checkId", "id").ifBlank { checkId },
            startText = pick(body, "startDate", "startTime"),
            endText = pick(body, "endDate", "endTime"),
            mileKm = num(pick(body, "mile", "exerciseMile")),
            speedKmh = num(pick(body, "speed", "exerciseSpeed")),
            timeText = pick(body, "time", "duration", "exerciseTime"),
            stateCode = pick(body, "state", "exerciseState"),
            remark = pick(body, "remark", "reason"),
            trackBd09 = track(pick(body, "runningRoute", "runningRoutes", "path")),
            handleTime = pick(body, "opDate", "handleTime"),
            handleUser = pick(body, "opUser", "handleUser"),
            handleRemark = pick(body, "opRemark", "handleRemark"),
            applyTime = pick(body, "applyDate", "applyTime"),
            applyReason = pick(body, "applyReason"),
            auditTime = pick(body, "auditDate", "auditTime"),
            auditUser = pick(body, "auditUser"),
            auditState = pick(body, "auditState"),
            auditRemark = pick(body, "auditRemark"),
        )
    }

    fun appeals(json: JsonObject): AppealPage {
        val rows = json.rows().map { row ->
            AppealRow(
                id = pick(row, "id", "applyId"),
                timeText = pick(row, "startDate", "exerciseTime", "checkTime"),
                mileKm = num(pick(row, "exerciseMile", "mile")),
                timeUsedText = pick(row, "time", "duration"),
                speedKmh = num(pick(row, "speed")),
                exerciseState = pick(row, "exerciseState", "state"),
                auditState = pick(row, "auditState"),
                reason = pick(row, "applyReason", "reason"),
            )
        }
        return AppealPage(rows, json.str("total").toIntOrNull() ?: rows.size)
    }

    /** 库里那条轨迹：BD-09、`[{"latitude":…,"longitude":…}]` */
    private fun track(raw: String): List<WgsPoint> {
        val text = raw.trim()
        if (!text.startsWith("[")) return emptyList()
        val array = runCatching { JsonParser.parseString(text).asJsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val lat = pick(obj, "latitude", "lat").toDoubleOrNull() ?: return@mapNotNull null
            val lng = pick(obj, "longitude", "lng").toDoubleOrNull() ?: return@mapNotNull null
            WgsPoint(latitude = lat, longitude = lng)
        }
    }

    /** 年级：`result` 直接是字符串「大一大二学生」 */
    fun gradeOf(json: JsonObject): String = pick(json, "result", "msg")

    /** 大一大二没有 startRunning.do，会话标识在 data.extraInfo.extraId 里 */
    fun extraIdOf(json: JsonObject?): String {
        json ?: return ""
        json.child("extraInfo")?.let { return it.str("extraId") }
        json.child("data")?.child("extraInfo")?.let { return it.str("extraId") }
        return pick(json, "extraId")
    }

    /** 大三大四 `startRunning.do` 回 rows[0].extraId */
    fun startIdOf(json: JsonObject?): String = json?.let { pick(it, "extraId") }.orEmpty()

    fun startMemberOf(json: JsonObject?): String = json?.let { pick(it, "memberId") }.orEmpty()

    /** 交完这一场，服务器给的记录 id；**没落库时是 "0"**，这个 0 必须当失败处理 */
    fun uploadedIdOf(json: JsonObject?): String {
        json ?: return ""
        json.child("data")?.let { data ->
            data.str("id").let { if (it.isNotBlank()) return it }
        }
        return pick(json, "id")
    }

    fun messageOf(json: JsonObject?): String = json?.let { pick(it, "result", "msg") }.orEmpty()

    fun textOf(json: JsonObject, vararg keys: String): String = pick(json, *keys)

    

    /** 根上找，找不到再往 data 里找一层 */
    private fun section(json: JsonObject?, name: String): JsonObject? {
        json ?: return null
        json.child(name)?.let { return it }
        return json.child("data")?.child(name)
    }

    private fun JsonObject.child(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.firstRow(): JsonObject? = rows().firstOrNull()

    private fun JsonObject.rows(): List<JsonObject> {
        val array = get("rows") ?: return emptyList()
        if (!array.isJsonArray) return emptyList()
        return array.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
    }

    private fun JsonObject.str(vararg keys: String): String {
        keys.forEach { key ->
            get(key)?.takeUnless { it.isJsonNull }?.let { element ->
                val value = plain(element)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    /** 先看根，再看 data，再看 rows[0] —— 官方这三层都出现过 */
    private fun pick(source: JsonObject?, vararg keys: String): String =
        source?.walkKeys(keys).orEmpty()

    /**
     * 取值：先根上的同名键，再往下钻嵌套对象（官方把东西塞在 data.totalInfo /
     * data.extraInfo 这种二层三层里都有过），最后试 rows[0]。钻的深度限 4 层，够且不瞎找。
     */
    private fun JsonObject.walkKeys(keys: Array<out String>): String {
        keys.forEach { key ->
            get(key)?.takeUnless { it.isJsonNull }?.let { return plain(it) }
        }
        return deepSearch(this, keys, 0)
    }

    private fun deepSearch(node: JsonObject, keys: Array<out String>, depth: Int): String {
        if (depth > 4) return ""
        node.entrySet().forEach { (name, value) ->
            if (name in keys && !value.isJsonNull) return plain(value)
        }
        node.entrySet().forEach { (_, value) ->
            when {
                value.isJsonObject -> {
                    val hit = deepSearch(value.asJsonObject, keys, depth + 1)
                    if (hit.isNotBlank()) return hit
                }
                value.isJsonArray -> value.asJsonArray.forEach { item ->
                    if (item.isJsonObject) {
                        val hit = deepSearch(item.asJsonObject, keys, depth + 1)
                        if (hit.isNotBlank()) return hit
                    }
                }
                else -> Unit
            }
        }
        return ""
    }

    private fun text(json: JsonObject?, vararg keys: String): String = pick(json, *keys)

    private fun plain(element: JsonElement): String = when {
        element.isJsonNull -> ""
        element.isJsonPrimitive -> element.asString
        else -> element.toString()
    }

    private fun num(raw: String, default: Double = 0.0): Double = raw.trim().toDoubleOrNull() ?: default
}
