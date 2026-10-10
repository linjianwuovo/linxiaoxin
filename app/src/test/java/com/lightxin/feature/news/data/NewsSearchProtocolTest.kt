package com.linxin.feature.news.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 公告搜索的协议形状测试。数据全部是手写的合成 JSON，
 * 结构照抄安小信 `homeQuery.do` 的响应包装：外层 `flag/code/msg/data`，
 * `data` 里三段 `contactsVo / newsVo / serviceVo`，新闻条目字段与列表接口同名。
 */
class NewsSearchProtocolTest {

    @Test
    fun `parse homeQuery response and map newsVo to items`() {
        val json = """
            {"flag":true,"code":null,"msg":null,"data":{
              "contactsVo":[{"name":"示例"}],
              "newsVo":[
                {"id":"1001","bt":"关于期末考试安排的","publishPerson":"示例处","publishTime":"2026-06-30 10:00:00","image1":""},
                {"id":null,"bt":"没有 id 的行"},
                {"id":"1002","bt":"  标题带空格  ","publishPerson":null,"publishTime":null,"image1":"https://example.invalid/a.png"}
              ],
              "serviceVo":[]
            }}
        """.trimIndent()

        val resp = Gson().fromJson(json, HomeQueryResponse::class.java)
        val items = newsItemsFromRows(resp.data?.newsVo.orEmpty())

        assertEquals(true, resp.flag)
        // id 为 null 的行丢掉，不占位置
        assertEquals(2, items.size)
        assertEquals("关于期末考试安排的", items[0].title)
        assertEquals("示例处", items[0].publisher)
        assertEquals("2026-06-30 10:00:00", items[0].publishTime)
        // image1 是空串时不算封面
        assertNull(items[0].coverUrl)
        assertEquals("标题带空格", items[1].title)
        assertEquals("", items[1].publisher)
        assertEquals("https://example.invalid/a.png", items[1].coverUrl)
    }

    @Test
    fun `failed flag and null data still map to an empty list`() {
        val resp = Gson().fromJson(
            """{"flag":false,"code":null,"msg":"非法访问","data":null}""",
            HomeQueryResponse::class.java,
        )

        assertEquals(false, resp.flag)
        assertEquals("非法访问", resp.msg)
        assertEquals(emptyList<Any>(), newsItemsFromRows(resp.data?.newsVo.orEmpty()))
    }
}
