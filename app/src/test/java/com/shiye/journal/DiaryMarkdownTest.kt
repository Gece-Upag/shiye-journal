package com.shiye.journal

import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.data.model.DiaryField
import com.shiye.journal.data.model.DiaryStatus
import com.shiye.journal.data.model.FieldType
import com.shiye.journal.domain.DiaryMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单篇日记的 Markdown 导出。
 *
 * 规格 §7.2 对格式有明确示例，这里逐条钉死：
 * front matter 三个键、空字段保留标题、YAML 特殊字符要转义、换行要统一。
 */
class DiaryMarkdownTest {

    private fun entry(
        date: String = "2026-10-14",
        template: String = "日常",
        fields: List<Pair<String, String>> = emptyList()
    ) = DiaryEntry(
        id = "e1",
        date = date,
        templateId = "tpl-daily",
        templateName = template,
        templateVersion = 1,
        status = DiaryStatus.COMPLETE,
        fields = fields.mapIndexed { index, (label, content) ->
            DiaryField(
                id = "f$index",
                entryId = "e1",
                fieldKey = "k$index",
                label = label,
                type = FieldType.MULTI,
                required = false,
                sortOrder = index,
                content = content
            )
        },
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `文件名是 YYYY-MM-DDmd`() {
        assertEquals("2026-10-14.md", DiaryMarkdown.fileName(entry()))
    }

    @Test
    fun `基本格式与规格示例一致`() {
        val text = DiaryMarkdown.render(
            entry(fields = listOf("心情" to "平静", "正文" to "今天……"))
        )

        val expected = """
            ---
            date: 2026-10-14
            template: 日常
            template_version: 1
            ---

            ## 心情

            平静

            ## 正文

            今天……
        """.trimIndent().trim()

        assertEquals(expected, text.trim())
    }

    @Test
    fun `空字段保留标题`() {
        val text = DiaryMarkdown.render(
            entry(fields = listOf("正文" to "写了", "备注" to ""))
        )

        assertTrue("空字段也要留下标题", text.contains("## 备注"))
        assertTrue(text.contains("## 正文"))
    }

    @Test
    fun `模板名含 YAML 特殊字符时加引号转义`() {
        // 冒号在 YAML 里表示键值分隔，不转义会让 front matter 解析失败
        assertEquals("\"a: b\"", DiaryMarkdown.yamlScalar("a: b"))
        assertEquals("\"say \\\"hi\\\"\"", DiaryMarkdown.yamlScalar("say \"hi\""))
        assertEquals("\"#tag\"", DiaryMarkdown.yamlScalar("#tag"))
        assertEquals("\"\"", DiaryMarkdown.yamlScalar(""))
        // 普通中文不需要引号
        assertEquals("日常", DiaryMarkdown.yamlScalar("日常"))
        // 纯数字不加引号会被当成数字类型
        assertEquals("\"123\"", DiaryMarkdown.yamlScalar("123"))
    }

    @Test
    fun `换行统一为 LF`() {
        assertEquals("a\nb", DiaryMarkdown.normalizeNewlines("a\r\nb"))
        assertEquals("a\nb", DiaryMarkdown.normalizeNewlines("a\rb"))
        assertEquals("正文", DiaryMarkdown.normalizeNewlines("正文\n\n\n"))
    }

    @Test
    fun `字段顺序与原日记一致`() {
        val text = DiaryMarkdown.render(
            entry(fields = listOf("第一" to "1", "第二" to "2", "第三" to "3"))
        )
        assertTrue(text.indexOf("## 第一") < text.indexOf("## 第二"))
        assertTrue(text.indexOf("## 第二") < text.indexOf("## 第三"))
    }

    @Test
    fun `批量导出文件名_同一天多篇加序号`() {
        // 同一天的第 1 篇用基础名，第 2、3 篇加序号，保证一篇日记一个文件、互不覆盖
        assertEquals("2026-10-14.md", DiaryMarkdown.fileNameFor(entry(), 0))
        assertEquals("2026-10-14-2.md", DiaryMarkdown.fileNameFor(entry(), 1))
        assertEquals("2026-10-14-3.md", DiaryMarkdown.fileNameFor(entry(), 2))
    }
}
