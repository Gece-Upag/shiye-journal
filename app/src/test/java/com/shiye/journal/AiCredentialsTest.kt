package com.shiye.journal

import com.shiye.journal.features.ai.AiCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * API Key 的清洗。
 *
 * 起因是一个真实的误报：用户从网页复制 DeepSeek 的 Key，末尾带了个换行，
 * OkHttp 拒绝把它放进 HTTP 头，而当时这个错被说成"地址格式不对" ——
 * 于是用户对着一个完全正确的地址反复检查。
 * 这类脏字符必须在入库前就处理掉，处理不掉也要报对。
 */
class AiCredentialsTest {

    @Test
    fun 正常的Key原样保留() {
        assertEquals("sk-abc123XYZ", AiCredentials.sanitize("sk-abc123XYZ"))
        assertFalse(AiCredentials.hasInvisibleJunk("sk-abc123XYZ"))
    }

    @Test
    fun 末尾换行会被去掉() {
        // 从网页复制最常见的样子
        assertEquals("sk-abc123", AiCredentials.sanitize("sk-abc123\n"))
        assertTrue(AiCredentials.hasInvisibleJunk("sk-abc123\n"))
    }

    @Test
    fun 首尾空格与制表符也会被去掉() {
        assertEquals("sk-abc123", AiCredentials.sanitize("  sk-abc123\t "))
    }

    @Test
    fun 常见的三种行尾都处理() {
        assertEquals("sk-k", AiCredentials.sanitize("sk-k\n"))
        assertEquals("sk-k", AiCredentials.sanitize("sk-k\r"))
        assertEquals("sk-k", AiCredentials.sanitize("sk-k\r\n"))
    }

    @Test
    fun 中间的换行同样剔除() {
        // 有些后台会把长 Key 折行显示，复制下来就是两段
        assertEquals("sk-abcd", AiCredentials.sanitize("sk-ab\ncd"))
    }

    @Test
    fun 全是脏字符时得到空串() {
        assertEquals("", AiCredentials.sanitize("\n\r\t  "))
    }

    @Test
    fun 中间的正常空格要保留() {
        // 万一某个服务商的 Key 本身含分隔符，别因为我们改坏
        assertEquals("sk-a b c", AiCredentials.sanitize("sk-a b c"))
    }

    @Test
    fun 清洗后的结果一定能放进HTTP头() {
        // 这是最终目的：洗完不再触发 OkHttp 的校验
        val dirty = "sk-realkey\u0000\u001F\n"
        val clean = AiCredentials.sanitize(dirty)
        okhttp3.Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $clean")
            .build()
    }
}
