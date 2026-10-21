package com.shiye.journal

import com.shiye.journal.data.model.DiaryField
import com.shiye.journal.data.model.FieldType
import com.shiye.journal.data.model.TemplateField
import com.shiye.journal.domain.isLegacyField
import com.shiye.journal.domain.planTemplateSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切换模板时的字段合并规则。
 *
 * 规格 §4.3 的核心是"不能静默丢内容"，这条最容易在重排/改名时悄悄出错，
 * 所以每个分支都在测试里钉死。
 */
class TemplateSwitchTest {

    private fun diaryField(
        id: String,
        key: String,
        label: String,
        content: String,
        order: Int
    ) = DiaryField(
        id = id,
        entryId = "entry-1",
        fieldKey = key,
        label = label,
        type = FieldType.MULTI,
        required = false,
        sortOrder = order,
        content = content
    )

    private fun templateField(key: String, label: String, order: Int) = TemplateField(
        id = "tf-$key",
        templateId = "tpl-2",
        fieldKey = key,
        label = label,
        type = FieldType.MULTI,
        required = false,
        sortOrder = order
    )

    private var idSeq = 0
    private fun nextId(): String = "new-${++idSeq}"

    @Test
    fun `按 fieldKey 匹配时保留内容`() {
        val current = listOf(
            diaryField("f1", "mood", "心情", "平静", 0),
            diaryField("f2", "body", "正文", "今天很充实", 1)
        )
        val target = listOf(
            templateField("body", "正文", 0),
            templateField("mood", "心情", 1)
        )

        val plan = planTemplateSwitch(current, target, "entry-1", ::nextId)

        assertEquals(0, plan.retainedCount)
        assertEquals(2, plan.fields.size)
        assertEquals("新模板的顺序优先", "正文", plan.fields[0].label)
        assertEquals("内容要跟着字段走", "今天很充实", plan.fields[0].content)
        assertEquals("心情", plan.fields[1].label)
        assertEquals("平静", plan.fields[1].content)
    }

    @Test
    fun `fieldKey 对不上时退化到按标签匹配`() {
        val current = listOf(diaryField("f1", "old-key", "心情", "不错", 0))
        val target = listOf(templateField("mood", "心情", 0))

        val plan = planTemplateSwitch(current, target, "entry-1", ::nextId)

        assertEquals(0, plan.retainedCount)
        assertEquals("按标签兜底后内容应保留", "不错", plan.fields.single().content)
    }

    @Test
    fun `无法对应且写了内容的旧字段必须保留`() {
        val current = listOf(
            diaryField("f1", "body", "正文", "今天很充实", 0),
            diaryField("f2", "note", "备注", "别忘了买牛奶", 1)
        )
        // 新模板只有 body，没有 note
        val target = listOf(templateField("body", "正文", 0))

        val plan = planTemplateSwitch(current, target, "entry-1", ::nextId)

        assertEquals("有一个旧字段无法对应", 1, plan.retainedCount)
        assertEquals("正文 + 保留的备注", 2, plan.fields.size)
        assertEquals("正文", plan.fields[0].label)
        assertEquals("今天很充实", plan.fields[0].content)
        assertEquals("保留的旧字段排在后面", "备注", plan.fields[1].label)
        assertEquals("旧字段内容不能丢", "别忘了买牛奶", plan.fields[1].content)
        assertEquals("保留字段的排序要接在新字段之后", 1, plan.fields[1].sortOrder)
    }

    @Test
    fun `空内容的旧字段不保留`() {
        val current = listOf(
            diaryField("f1", "body", "正文", "写了东西", 0),
            diaryField("f2", "note", "备注", "   ", 1) // 只有空白，等于没写
        )
        val target = listOf(templateField("body", "正文", 0))

        val plan = planTemplateSwitch(current, target, "entry-1", ::nextId)

        assertEquals("空白字段不该留下占位置", 0, plan.retainedCount)
        assertEquals(1, plan.fields.size)
    }

    @Test
    fun `合并后的字段 id 全部重新生成`() {
        val current = listOf(diaryField("f1", "body", "正文", "内容", 0))
        val target = listOf(templateField("body", "正文", 0))

        val plan = planTemplateSwitch(current, target, "entry-1", ::nextId)

        assertTrue("新字段不能复用旧字段 id，否则会撞主键", plan.fields.none { it.id == "f1" })
        assertEquals("entry-1", plan.fields.single().entryId)
    }

    @Test
    fun `遗留字段判定`() {
        val field = diaryField("f1", "note", "备注", "x", 0)
        assertTrue(isLegacyField(field, setOf("body", "mood")))
        assertFalse(isLegacyField(field, setOf("body", "note")))
        // 模板已被删除（key 集合为空）时不标遗留，否则整篇都会被标红
        assertFalse(isLegacyField(field, emptySet()))
    }
}
