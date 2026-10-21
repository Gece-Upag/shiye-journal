package com.shiye.journal

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守住 OkHttp 对 HTTP 头的行为假设。
 *
 * 这里验证的不是我们自己的代码，而是**依赖库的一个关键行为**：
 * API Key 里一旦出现换行，构建请求这一步就会抛 IllegalArgumentException，
 * 请求连发都发不出去。清洗凭据（见 [AiCredentialsTest]）就是建立在这个前提上。
 * 万一将来 OkHttp 放宽了这项校验、或有别的环节悄悄替我们清理了，这个测试会先响。
 */
class ApiKeySanityTest {

    private fun build(values: Map<String, String>) {
        Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .post("{}".toRequestBody())
            .apply { values.forEach { (k, v) -> addHeader(k, v) } }
            .build()
    }

    @Test
    fun 干净的Key能正常建请求() {
        build(mapOf("Authorization" to "Bearer sk-abc123"))
    }

    @Test
    fun 带换行的Key会抛IllegalArgumentException() {
        // 从网页复制 Key 时最常见的样子：末尾带一个换行
        var thrown = false
        try {
            build(mapOf("Authorization" to "Bearer sk-abc123\n"))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("含换行的 Key 必须让 OkHttp 拒绝 —— 凭据清洗就是建立在这个前提上", thrown)
    }
}
