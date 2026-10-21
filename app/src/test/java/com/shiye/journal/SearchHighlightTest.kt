package com.shiye.journal

import com.shiye.journal.domain.SearchHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索片段与高亮。
 *
 * 规格 §4.6：忽略英文大小写、中文直接包含匹配、高亮命中文字。
 * 这些是纯字符串处理，边界情况（关键词在末尾、空关键词、重复出现）都值得钉死。
 */
class SearchHighlightTest {

    @Test
    fun `英文忽略大小写`() {
        assertEquals(
            listOf(0 until 5),
            SearchHighlight.ranges("Hello world", "hello")
        )
        assertEquals(
            listOf(6 until 11),
            SearchHighlight.ranges("Hello world", "WORLD")
        )
    }

    @Test
    fun `中文直接包含匹配`() {
        assertEquals(listOf(2 until 4), SearchHighlight.ranges("今天天气不错", "天气"))
        assertTrue(SearchHighlight.ranges("今天天气不错", "下雨").isEmpty())
    }

    @Test
    fun `关键词出现多次时返回全部区间`() {
        // "abxxab"：a0 b1 x2 x3 a4 b5
        assertEquals(
            listOf(0 until 2, 4 until 6),
            SearchHighlight.ranges("abxxab", "ab")
        )
    }

    @Test
    fun `空关键词不高亮`() {
        assertTrue(SearchHighlight.ranges("任意内容", "").isEmpty())
        assertTrue(SearchHighlight.ranges("任意内容", "   ").isEmpty())
    }

    @Test
    fun `片段优先从命中位置往前截取`() {
        val long = "前缀".repeat(20) + "关键词" + "后缀".repeat(20)
        val excerpt = SearchHighlight.excerpt(long, "关键词")

        assertTrue("片段里必须包含关键词", excerpt.contains("关键词"))
        assertTrue("片段要被截断", excerpt.length <= SearchHighlight.EXCERPT_MAX_LENGTH + 2)
        assertTrue("前面被截断了要加省略号", excerpt.startsWith("…"))
    }

    @Test
    fun `命中位置靠前时不加前导省略号`() {
        val excerpt = SearchHighlight.excerpt("关键词就在开头", "关键词")
        assertEquals("关键词就在开头", excerpt)
    }

    @Test
    fun `空关键词时从头截取当摘要用`() {
        val text = "一".repeat(100)
        val excerpt = SearchHighlight.excerpt(text, "")
        assertTrue(excerpt.startsWith("…").not())
        assertTrue("超出长度要截断", excerpt.length <= SearchHighlight.EXCERPT_MAX_LENGTH + 1)
    }

    @Test
    fun `空文本返回空串`() {
        assertEquals("", SearchHighlight.excerpt("", "关键词"))
        assertEquals("", SearchHighlight.excerpt("   \n  ", "关键词"))
    }

    @Test
    fun `换行被压平成空格`() {
        val excerpt = SearchHighlight.excerpt("第一行\n第二行有关键词", "关键词")
        assertTrue(excerpt.contains("关键词"))
        assertTrue("片段里不该保留换行", excerpt.contains("\n").not())
    }
}
