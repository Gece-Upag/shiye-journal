package com.shiye.journal

import com.shiye.journal.features.ai.AiClient
import com.shiye.journal.features.ai.AiConfig
import com.shiye.journal.features.ai.AiErrorKind
import com.shiye.journal.features.ai.AiFailure
import com.shiye.journal.features.ai.AiHttpEngine
import com.shiye.journal.features.ai.AiHttpException
import com.shiye.journal.features.ai.AiMode
import com.shiye.journal.features.ai.AiProtocol
import com.shiye.journal.features.ai.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 客户端与协议。
 *
 * 网络部分用假引擎替换，所以这里测的全是**规则**：
 * 地址怎么拼、响应怎么读、HTTP 异常翻译成什么人话。
 * 规则错了用户会看到一个莫名其妙的提示，比功能不可用更糟。
 */
class AiClientTest {

    private fun cloud(
        baseUrl: String = "https://api.example.com/v1",
        model: String = "test-model",
        key: String = "sk-test"
    ) = AiConfig(
        mode = AiMode.CLOUD,
        baseUrl = baseUrl,
        model = model,
        provider = "Test",
        apiKey = key
    )

    // ---------- 地址拼装 ----------

    @Test
    fun chatUrl_补全chat_completions() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiProtocol.chatCompletionsUrl("https://api.openai.com/v1")
        )
    }

    @Test
    fun chatUrl_吃掉末尾斜杠() {
        // 用户从浏览器地址栏复制过来经常带斜杠，不处理会拼出 //chat/completions
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiProtocol.chatCompletionsUrl("https://api.openai.com/v1/")
        )
    }

    @Test
    fun chatUrl_已经写全了就原样返回() {
        val full = "http://10.0.2.2:11434/v1/chat/completions"
        assertEquals(full, AiProtocol.chatCompletionsUrl(full))
    }

    @Test
    fun deepseek的官方地址能用() {
        // 用户实际在用、且确认 Key 和模型名都没问题的服务商，不能有意外
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiProtocol.chatCompletionsUrl("https://api.deepseek.com/v1")
        )
    }

    @Test
    fun 网址带端口号也能用() {
        assertEquals(
            "http://192.168.1.10:8080/v1/chat/completions",
            AiProtocol.chatCompletionsUrl("http://192.168.1.10:8080/v1")
        )
    }

    @Test
    fun 用户手滑带了首尾空白也能通() {
        // 复制粘贴最常见的样子：前后各粘了一点空白
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiProtocol.chatCompletionsUrl("  https://api.deepseek.com/v1  ")
        )
    }

    @Test
    fun 没有协议头就拒绝() {
        // 拿这种地址去发请求只会得到一个费解的报错，不如一开始就拦下
        assertNull(AiProtocol.chatCompletionsUrl("api.openai.com/v1"))
        assertNull(AiProtocol.chatCompletionsUrl(""))
    }

    // ---------- 响应解析 ----------

    @Test
    fun extractReply_标准结构() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"你好"}}]}
        """.trimIndent()
        assertEquals("你好", AiProtocol.extractReply(body))
    }

    @Test
    fun extractReply_兼容放在text字段的接口() {
        val body = """{"choices":[{"text":"另一处"}]}"""
        assertEquals("另一处", AiProtocol.extractReply(body))
    }

    @Test
    fun extractReply_多余字段要忽略() {
        val body = """
            {"id":"x","created":1,"usage":{"total_tokens":9},
             "choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"ok"}}]}
        """.trimIndent()
        assertEquals("ok", AiProtocol.extractReply(body))
    }

    @Test
    fun extractReply_结构不认识时返回null() {
        assertNull(AiProtocol.extractReply("""{"error":{"message":"nope"}}"""))
        assertNull(AiProtocol.extractReply("不是 JSON"))
        assertNull(AiProtocol.extractReply("""{"choices":[]}"""))
    }

    // ---------- 开关 ----------

    @Test
    fun chat_关闭时直接拒绝且不碰网络() {
        var called = false
        val client = AiClient { _, _, _, _ -> called = true; "" }
        val result = client.chat(AiConfig(mode = AiMode.OFF), listOf(AiProtocol.Message.user("hi")))
        assertTrue(result is AiResult.Failure)
        assertEquals(AiErrorKind.NOT_CONFIGURED, (result as AiResult.Failure).failure.kind)
        assertEquals(false, called)
    }

    @Test
    fun chat_配置不全也拒绝() {
        var called = false
        val client = AiClient { _, _, _, _ -> called = true; "" }
        // 开了云端但没填模型名
        val result = client.chat(
            AiConfig(mode = AiMode.CLOUD, baseUrl = "https://x.com/v1", model = ""),
            listOf(AiProtocol.Message.user("hi"))
        )
        assertEquals(AiErrorKind.NOT_CONFIGURED, (result as AiResult.Failure).failure.kind)
        assertEquals(false, called)
    }

    @Test
    fun chat_地址不合法时报地址错() {
        val client = AiClient { _, _, _, _ -> "" }
        val result = client.chat(cloud(baseUrl = "ftp://x.com/v1"), listOf(AiProtocol.Message.user("hi")))
        assertEquals(AiErrorKind.BAD_URL, (result as AiResult.Failure).failure.kind)
    }

    // ---------- 错误翻译 ----------

    @Test
    fun 认证失败_401映射到AUTH() {
        val client = AiClient(engine = { _, _, _, _ ->
            throw AiHttpException(statusCode = 401, reason = AiHttpException.AiHttpCause.HTTP_STATUS)
        })
        val f = (client.chat(cloud(), listOf(AiProtocol.Message.user("hi"))) as AiResult.Failure).failure
        assertEquals(AiErrorKind.AUTH, f.kind)
        assertTrue(f.message().contains("API Key"))
    }

    @Test
    fun 模型不存在_404映射到MODEL_NOT_FOUND_且带上模型名() {
        val client = AiClient(engine = { _, _, _, _ ->
            throw AiHttpException(statusCode = 404, reason = AiHttpException.AiHttpCause.HTTP_STATUS)
        })
        val f = (client.chat(cloud(model = "qwen2.5"), listOf(AiProtocol.Message.user("hi")))
                as AiResult.Failure).failure
        assertEquals(AiErrorKind.MODEL_NOT_FOUND, f.kind)
        assertTrue(f.message().contains("qwen2.5"))
    }

    @Test
    fun Key里有换行时报的是凭据问题而不是地址问题() {
        // 这条对应一个真实的误导：以前把这种情况报成"地址格式不对"，
        // 用户对着完全正确的 DeepSeek 地址反复检查，真正的问题却在 Key 上
        val client = AiClient(engine = { _, _, _, _ ->
            throw AiHttpException(reason = AiHttpException.AiHttpCause.BAD_CREDENTIAL)
        })
        val dirty = cloud(key = "sk-abc\n")
        val f = (client.chat(dirty, listOf(AiProtocol.Message.user("hi"))) as AiResult.Failure).failure
        assertEquals(AiErrorKind.BAD_CREDENTIAL, f.kind)
        assertTrue(f.message().contains("Key"))
        assertTrue(f.message().contains("换行"))
    }

    @Test
    fun 脏Key在发出去之前就被洗干净() {
        var sentHeaders: Map<String, String> = emptyMap()
        val client = AiClient(engine = { _, headers, _, _ ->
            sentHeaders = headers
            """{"choices":[{"message":{"role":"a","content":"ok"}}]}"""
        })
        // 从网页复制的样子：末尾带换行
        client.chat(cloud(key = "sk-realkey\n"), listOf(AiProtocol.Message.user("hi")))
        assertEquals("Bearer sk-realkey", sentHeaders["Authorization"])
    }

    @Test
    fun 超时映射到TIMEOUT_且说出多少秒() {
        val client = AiClient(engine = { _, _, _, _ ->
            throw AiHttpException(reason = AiHttpException.AiHttpCause.TIMEOUT, message = "read timeout")
        })
        val f = (client.chat(cloud().copy(timeoutSeconds = 15), listOf(AiProtocol.Message.user("hi")))
                as AiResult.Failure).failure
        assertEquals(AiErrorKind.TIMEOUT, f.kind)
        assertTrue(f.message().contains("15"))
    }

    @Test
    fun 连不上映射到UNREACHABLE() {
        val client = AiClient(engine = { _, _, _, _ ->
            throw AiHttpException(
                reason = AiHttpException.AiHttpCause.IO,
                message = "找不到主机"
            )
        })
        val f = (client.chat(cloud(), listOf(AiProtocol.Message.user("hi"))) as AiResult.Failure).failure
        assertEquals(AiErrorKind.UNREACHABLE, f.kind)
        assertTrue(f.message().contains("找不到主机"))
    }

    @Test
    fun 服务端500映射到BAD_RESPONSE_且带上状态码() {
        val client = AiClient(engine = { _, _, _, _ ->
            throw AiHttpException(statusCode = 502, reason = AiHttpException.AiHttpCause.HTTP_STATUS)
        })
        val f = (client.chat(cloud(), listOf(AiProtocol.Message.user("hi"))) as AiResult.Failure).failure
        assertEquals(AiErrorKind.BAD_RESPONSE, f.kind)
        assertTrue(f.message().contains("502"))
    }

    @Test
    fun 返回200但结构不认识时映射到BAD_RESPONSE() {
        val client = AiClient(engine = { _, _, _, _ -> """{"error":"nope"}""" })
        val f = (client.chat(cloud(), listOf(AiProtocol.Message.user("hi"))) as AiResult.Failure).failure
        assertEquals(AiErrorKind.BAD_RESPONSE, f.kind)
    }

    // ---------- 凭据 ----------

    @Test
    fun 本地模式不带Authorization头() {
        var sentHeaders: Map<String, String> = emptyMap()
        val client = AiClient(engine = { _, headers, _, _ ->
            sentHeaders = headers
            """{"choices":[{"message":{"role":"a","content":"ok"}}]}"""
        })
        val local = AiConfig(
            mode = AiMode.LOCAL,
            baseUrl = "http://10.0.2.2:11434/v1",
            model = "qwen2.5"
        )
        client.chat(local, listOf(AiProtocol.Message.user("hi")))
        // 本地服务不需要 Key，也不该把别的服务的 Key 发给它
        assertTrue(sentHeaders["Authorization"] == null)
    }

    @Test
    fun 云端模式带Bearer头() {
        var sentHeaders: Map<String, String> = emptyMap()
        val client = AiClient(engine = { _, headers, _, _ ->
            sentHeaders = headers
            """{"choices":[{"message":{"role":"a","content":"ok"}}]}"""
        })
        client.chat(cloud(key = "sk-abc"), listOf(AiProtocol.Message.user("hi")))
        assertEquals("Bearer sk-abc", sentHeaders["Authorization"])
    }

    @Test
    fun 成功时结果去掉首尾空白() {
        val client = AiClient(engine = { _, _, _, _ ->
            """{"choices":[{"message":{"role":"a","content":"  润色结果\n"}}]}"""
        })
        val r = client.chat(cloud(), listOf(AiProtocol.Message.user("hi")))
        assertEquals("润色结果", (r as AiResult.Success).text)
    }
}
