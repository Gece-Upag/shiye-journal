package com.shiye.journal

import com.shiye.journal.domain.AiSummaryEntry
import com.shiye.journal.domain.AiSummaryList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一篇日记里多条 AI 建议的存取。
 *
 * 这里最要紧的是**向下兼容**：那一列早先存的是纯文本，不是 JSON。
 * 解析不出来就当一条旧记录，绝不能让以前要过的建议凭空消失。
 */
class AiSummaryListTest {

    private fun entry(text: String, at: Long) = AiSummaryEntry(text = text, createdAt = at)

    @Test
    fun 空值解析成空列表() {
        assertTrue(AiSummaryList.parse("").isEmpty())
        assertTrue(AiSummaryList.parse("   ").isEmpty())
        assertTrue(AiSummaryList.parse("[]").isEmpty())
    }

    @Test
    fun 多条存取往返一致() {
        val list = listOf(entry("第一条", 2000L), entry("第二条", 1000L))
        val text = AiSummaryList.write(list)
        val back = AiSummaryList.parse(text)
        assertEquals(2, back.size)
        assertEquals("第一条", back[0].text)
        assertEquals(2000L, back[0].createdAt)
    }

    @Test
    fun 旧格式的纯文本不会被丢掉() {
        // 老版本这一列直接存建议内容。升级后必须还能看到，
        // 只是没有时间，退回用日记的更新时间兜底
        val back = AiSummaryList.parse("以前存下来的建议内容", fallbackTime = 12345L)
        assertEquals(1, back.size)
        assertEquals("以前存下来的建议内容", back[0].text)
        assertEquals(12345L, back[0].createdAt)
    }

    @Test
    fun 写入时按从晚到早排() {
        val text = AiSummaryList.writeNewestFirst(
            listOf(entry("旧", 1000L), entry("新", 3000L), entry("中", 2000L))
        )
        val back = AiSummaryList.parse(text)
        assertEquals("新", back[0].text)
        assertEquals("中", back[1].text)
        assertEquals("旧", back[2].text)
    }

    @Test
    fun 本次结果排在最前面() {
        val existing = listOf(entry("上次的", 1000L))
        val merged = AiSummaryList.merge(
            existing = existing,
            kept = setOf("上次的"),
            newResult = "这次的",
            now = 5000L
        )
        val text = AiSummaryList.writeNewestFirst(merged)
        val back = AiSummaryList.parse(text)
        assertEquals(2, back.size)
        assertEquals("这次的", back[0].text)
        assertEquals("上次的", back[1].text)
        // 旧条目沿用原来的时间，不能被记成"刚生成的"
        assertEquals(1000L, back[1].createdAt)
    }

    @Test
    fun 不勾本次时只保留勾中的旧建议() {
        val existing = listOf(entry("A", 1000L), entry("B", 2000L))
        val merged = AiSummaryList.merge(
            existing = existing,
            kept = setOf("B"),
            newResult = null,
            now = 5000L
        )
        assertEquals(1, merged.size)
        assertEquals("B", merged[0].text)
    }

    @Test
    fun 一条都不勾就清空() {
        val merged = AiSummaryList.merge(
            existing = listOf(entry("A", 1000L)),
            kept = emptySet(),
            newResult = null,
            now = 5000L
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun 内容为空的条目不会被存进去() {
        val back = AiSummaryList.parse("""[{"text":"","createdAt":1},{"text":"有内容","createdAt":2}]""")
        assertEquals(1, back.size)
        assertEquals("有内容", back[0].text)
    }
}
