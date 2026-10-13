package com.linxin.feature.leave.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 请假详情那条链里唯一能离线证的部分：把 dataJson（填的值）按 formJson（表单结构）翻成人话。
 * 结构样例照 2026-10-13 从 ywlz 那套 H5 chunk 里读到的节点形状写：
 * 节点带 model / label，选项在 properties.options[{label,value}]，容器靠 list / columns 嵌套。
 */
class LeaveDetailFieldsTest {

    private val formJson = """
        {
          "list": [
            { "model": "qjType", "label": "请假类型", "type": "radio",
              "properties": { "options": [
                { "label": "病假", "value": "1" },
                { "label": "事假", "value": "2" }
              ] } },
            { "model": "range", "label": "请假时间", "type": "dateRange" },
            { "columns": [
              { "model": "hours", "label": "请假时长", "type": "number" },
              { "model": "addr", "label": "去向", "type": "input" }
            ] },
            { "model": "secret", "label": "不该出现的空字段", "type": "input" }
          ]
        }
    """.trimIndent()

    @Test
    fun `values follow form order and radio values become their labels`() {
        val fields = leaveDetailFields(
            formJson,
            """{"hours":8,"range":["2026-10-14 08:00","2026-10-15 18:00"],"qjType":"1","addr":"家里","secret":""}""",
        )
        assertEquals(
            listOf(
                LeaveField("请假类型", "病假"),
                LeaveField("请假时间", "2026-10-14 08:00 ~ 2026-10-15 18:00"),
                LeaveField("请假时长", "8"),
                LeaveField("去向", "家里"),
            ),
            fields,
        )
    }

    @Test
    fun `integer numbers do not show a decimal tail`() {
        val fields = leaveDetailFields(formJson, """{"hours":8.0,"qjType":"2"}""")
        assertEquals("8", fields.first { it.label == "请假时长" }.value)
        assertEquals("事假", fields.first { it.label == "请假类型" }.value)
    }

    @Test
    fun `keys missing from the form tree still show up with their own name`() {
        val fields = leaveDetailFields(formJson, """{"qjType":"1","extraNote":"周三有课"}""")
        assertEquals("周三有课", fields.last { it.label == "extraNote" }.value)
    }

    @Test
    fun `a broken or missing form tree still renders values`() {
        val fields = leaveDetailFields("{ 这不是 JSON", """{"qjType":"1","hours":3}""")
        assertEquals(listOf("qjType", "hours"), fields.map { it.label })
        assertEquals(listOf("1", "3"), fields.map { it.value })
    }

    @Test
    fun `unparseable values give an empty list instead of throwing`() {
        assertTrue(leaveDetailFields(formJson, "null").isEmpty())
        assertTrue(leaveDetailFields(formJson, "").isEmpty())
        assertTrue(leaveDetailFields(null, null).isEmpty())
    }
}
