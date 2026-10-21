package com.shiye.journal

import com.shiye.journal.domain.AiApplyDecision
import com.shiye.journal.domain.AiPrompts
import com.shiye.journal.domain.decideApply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词与采纳规则。
 *
 * 提示词本身没什么可断言的（改一个字就得改测试，没意义），
 * 这里按住的是**不能退让的那几条**：不许编造、借款不算收支、
 * 空字段不发、以及"用户在等待期间改了内容不能被覆盖"。
 */
class AiPromptsTest {

    @Test
    fun 润色提示词明确禁止改变语气和补写内容() {
        val messages = AiPrompts.polish("今天去爬山了")
        val text = messages.joinToString("\n") { it.content }
        assertTrue(text.contains("错别字"))
        assertTrue(text.contains("不要改变语气"))
        assertTrue(text.contains("增加新内容"))
        // 原文必须真的被带上了，否则模型无从下手
        assertTrue(text.contains("今天去爬山了"))
    }

    @Test
    fun 建议提示词明确要求不复述日记内容() {
        val text = AiPrompts.entryAdvice("今天爬山，很累但开心").joinToString("\n") { it.content }
        assertTrue(text.contains("建议"))
        assertTrue(text.contains("不要复述"))
        // 把刚写的东西再概括一遍没意义，所以禁止概括是硬要求
        assertTrue(text.contains("不要复述或概括日记内容"))
        assertTrue(text.contains("今天爬山，很累但开心"))
    }

    @Test
    fun 建议提示词限制总字数300字() {
        val text = AiPrompts.entryAdvice("正文").joinToString("\n") { it.content }
        assertTrue(text.contains("300 字"))
        // 必须写明是"总共"，否则模型容易理解成"每条 300 字"，写出来长得多
        assertTrue(text.contains("总共不超过 300 字"))
        assertTrue(text.contains("不是每条"))
    }

    @Test
    fun 建议提示词要求说透而不是一味求短() {
        // 用户反馈过"字数太少"：只给上限会让它越写越短，
        // 所以要同时说明"别为了简短而含糊"
        val text = AiPrompts.entryAdvice("正文").joinToString("\n") { it.content }
        assertTrue(text.contains("说清楚"))
        assertTrue(text.contains("不要凑字数"))
    }

    @Test
    fun 建议提示词把借款排除在收支之外() {
        val text = AiPrompts.advice("摘要").joinToString("\n") { it.content }
        assertTrue(text.contains("借入借出"))
        // 借款被当作收入来算，会得出完全错误的结论
        assertTrue(text.contains("不能当作消费或收入"))
    }

    @Test
    fun 默认提示词每一行都不带数字编号() {
        // 用户会**直接在输入框里看到这些内容**（默认就填在框里）。
        // 出现 "3. " 这样的行首编号，会让人以为前面还有 1、2 条被藏起来了，
        // 觉得提示词残缺 —— 所以编号一律不用，每行写成完整的一句话。
        val visible = listOf(
            AiPrompts.DEFAULT_POLISH_RULE,
            AiPrompts.DEFAULT_ENTRY_ADVICE_RULE,
            AiPrompts.DEFAULT_ADVICE_RULE
        )
        val numbered = Regex("^\\s*\\d+[.、)]")
        visible.forEach { rule ->
            rule.lines().forEach { line ->
                assertEquals(
                    "这一行带着编号，用户会以为提示词不完整：$line",
                    false,
                    numbered.containsMatchIn(line)
                )
            }
        }
    }

    @Test
    fun 默认提示词读起来是完整的话() {
        // 每行该是个完整的句子，而不是列表片段
        AiPrompts.DEFAULT_POLISH_RULE.lines().forEach { line ->
            assertTrue("这行太短，看着像半截：$line", line.trim().length >= 8)
        }
    }

    @Test
    fun 留空时用默认提示词() {
        val text = AiPrompts.polish("原文", "").joinToString("\n") { it.content }
        assertTrue(text.contains("错别字"))
        assertTrue(text.contains("不要改变语气"))
    }

    @Test
    fun 自定义提示词会替换默认的任务要求() {
        // 只追加会和默认规则打架（"只改错别字" vs "改写成正式语气"），所以是替换
        val text = AiPrompts.polish("原文", "改写成正式的工作汇报语气").joinToString("\n") { it.content }
        assertTrue(text.contains("正式的工作汇报语气"))
        assertEquals(false, text.contains("只改正错别字和不通顺的句子"))
    }

    @Test
    fun 自定义提示词改不掉不许编造这条底线() {
        // 这是最关键的一条：用户随手写句提示词，不能让 AI 开始给日记编情节
        listOf(
            AiPrompts.polish("原文", "随便写，越长越好，可以加点细节"),
            AiPrompts.entryAdvice("原文", "尽管发挥想象力"),
            AiPrompts.advice("摘要", "编点有趣的故事给我看")
        ).forEach { messages ->
            val text = messages.joinToString("\n") { it.content }
            assertTrue("不添加原文没有的信息 这条必须始终存在", text.contains("不添加原文没有的信息"))
            assertTrue("不推测事实 这条必须始终存在", text.contains("不推测事实"))
        }
    }

    @Test
    fun 自定义提示词改不掉只输出结果这条底线() {
        val text = AiPrompts.polish("原文", "先给我解释一下你打算怎么改").joinToString("\n") { it.content }
        assertTrue(text.contains("不要解释"))
    }

    @Test
    fun 只有空白字符等同于留空() {
        // 用户不小心打了几个空格，不该被当成"自定义了"
        val text = AiPrompts.polish("原文", "   \n ").joinToString("\n") { it.content }
        assertTrue(text.contains("错别字"))
    }

    @Test
    fun 七天建议的自定义提示词不影响借款说明() {
        // "借款不计入收支"是对数据的说明，不是任务要求，
        // 即使自定义了规则也必须在 —— 否则模型会把借来的钱当收入
        val text = AiPrompts.advice("摘要", "分析我的消费结构").joinToString("\n") { it.content }
        assertTrue(text.contains("分析我的消费结构"))
        assertTrue(text.contains("借款已单独列出、不计入收支"))
    }

    @Test
    fun 请求带system与user两条消息() {
        val messages = AiPrompts.polish("x")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
    }

    @Test
    fun 整理日记文本时空字段不发出去() {
        val text = AiPrompts.entryText(
            listOf(
                "心情" to "不错",
                "正文" to "今天爬山",
                "备注" to "   "
            )
        )
        assertTrue(text.contains("【心情】"))
        assertTrue(text.contains("今天爬山"))
        // 空字段只浪费 token，还会让模型以为这篇就这些
        assertEquals(false, text.contains("【备注】"))
    }

    @Test
    fun 全部字段为空时文本也为空() {
        assertEquals("", AiPrompts.entryText(listOf("心情" to "", "正文" to "  ")))
    }

    // ---------- 采纳冲突 ----------

    @Test
    fun 内容没动过就直接替换() {
        assertEquals(
            AiApplyDecision.REPLACE,
            decideApply(snapshotAtRequest = "原文", currentContent = "原文", aiResult = "润色后")
        )
    }

    @Test
    fun 用户在等待期间改了内容需要再确认() {
        // 这是规格 §8.3 那条"不得自动覆盖用户仍在编辑中的新内容"的落点
        assertEquals(
            AiApplyDecision.NEEDS_CONFIRM,
            decideApply(snapshotAtRequest = "原文", currentContent = "原文我又加了一句", aiResult = "润色后")
        )
    }

    @Test
    fun 结果和当前内容一样就无需改动() {
        assertEquals(
            AiApplyDecision.NO_CHANGE,
            decideApply(snapshotAtRequest = "原文", currentContent = "润色后", aiResult = "润色后")
        )
    }

    @Test
    fun 用户改回原样不算冲突() {
        // 改了又撤销，当前内容与快照一致，直接替换即可
        assertEquals(
            AiApplyDecision.REPLACE,
            decideApply(snapshotAtRequest = "原文", currentContent = "原文", aiResult = "润色后")
        )
    }
}
