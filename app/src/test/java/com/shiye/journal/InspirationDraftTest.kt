package com.shiye.journal

import com.shiye.journal.domain.InspirationDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 灵感草稿规则。
 *
 * 重点钉住两条：**空白不能发送**、**只加空格不算改动**。
 * 第二条决定列表上"已编辑"标记会不会乱冒。
 */
class InspirationDraftTest {

    @Test
    fun `空白内容不能发送`() {
        assertFalse(InspirationDraft.canSend(""))
        assertFalse(InspirationDraft.canSend("   "))
        assertFalse(InspirationDraft.canSend("\n\t "))
    }

    @Test
    fun `有内容就能发送`() {
        assertTrue(InspirationDraft.canSend("想到一个点子"))
        assertTrue(InspirationDraft.canSend("  a  "))
    }

    @Test
    fun `超长内容不能发送`() {
        val tooLong = "想".repeat(InspirationDraft.MAX_LENGTH + 1)
        assertFalse(InspirationDraft.canSend(tooLong))
    }

    @Test
    fun `刚好到上限仍能发送`() {
        val exact = "想".repeat(InspirationDraft.MAX_LENGTH)
        assertTrue(InspirationDraft.canSend(exact))
    }

    @Test
    fun `提示文案区分空白和超长`() {
        assertEquals("先写点什么吧", InspirationDraft.messageFor("  "))
        assertTrue(InspirationDraft.messageFor("想".repeat(2001))!!.contains("最多 2000 个字"))
        assertNull(InspirationDraft.messageFor("正常内容"))
    }

    @Test
    fun `前后空格不同不算改动`() {
        assertFalse(InspirationDraft.changed(original = "点子", next = "  点子  "))
    }

    @Test
    fun `正文变了才算改动`() {
        assertTrue(InspirationDraft.changed(original = "点子", next = "点子补充"))
    }

    @Test
    fun `normalize 去掉首尾空白`() {
        assertEquals("点子", InspirationDraft.normalize("  点子\n "))
        assertEquals("", InspirationDraft.normalize("   "))
    }
}
