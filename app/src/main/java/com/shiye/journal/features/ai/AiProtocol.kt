package com.shiye.journal.features.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenAI 兼容接口的协议部分：**纯 Kotlin，可单测**。
 *
 * 只做三件事：拼 URL、拼请求体、解析响应。
 * 真正的网络动作在 [AiClient] 里，这里一行网络代码都没有 ——
 * 所以"地址怎么拼""返回怪一点怎么判"都能用单元测试按住。
 */
object AiProtocol {

    /** 解析用的 Json：服务端多返回些字段很正常，一律忽略 */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    /**
     * 由 Base URL 推出 chat completions 的完整地址。
     *
     * 用户在设置里可能填这几种，都要能work：
     * - `https://api.openai.com/v1`          → 补 `/chat/completions`
     * - `https://api.openai.com/v1/`         → 同上（末尾斜杠要吃掉）
     * - `http://10.0.2.2:11434/v1/chat/completions` → 原样
     *
     * 只认 http/https：别的协议（比如手滑填成 `ftp://`）一律拒绝，
     * 免得拿着一个奇怪的地址去发请求。
     */
    fun chatCompletionsUrl(baseUrl: String): String? {
        val raw = baseUrl.trim()
        if (raw.isEmpty()) return null
        val lower = raw.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null

        val trimmed = raw.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    /** 请求体 */
    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.2,
        /** 上限不是必须的，带上能挡住"模型一口气写两千字" */
        val max_tokens: Int = 1024,
        val stream: Boolean = false
    )

    @Serializable
    data class Message(
        val role: String,
        val content: String
    ) {
        companion object {
            fun system(content: String) = Message("system", content)
            fun user(content: String) = Message("user", content)
        }
    }

    @Serializable
    data class ChatResponse(
        val choices: List<Choice> = emptyList()
    )

    @Serializable
    data class Choice(
        val message: Message? = null,
        /** 部分兼容接口把内容放在这里而不是 message.content */
        val text: String? = null
    )

    fun encodeRequest(request: ChatRequest): String = json.encodeToString(request)

    /**
     * 解析响应，取出正文。
     *
     * 取不到就返回 null，由调用方转成 BAD_RESPONSE ——
     * 不在这里抛异常，是为了让"返回了 200 但结构不认识"和"网络炸了"走同一条失败通道。
     */
    fun extractReply(body: String): String? = runCatching {
        val parsed = json.decodeFromString<ChatResponse>(body)
        val first = parsed.choices.firstOrNull() ?: return null
        first.message?.content?.takeIf { it.isNotBlank() }
            ?: first.text?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 测试连接用的最小请求：只问一句，只要一点点回复 */
    fun pingRequest(model: String): ChatRequest = ChatRequest(
        model = model,
        messages = listOf(Message.user("hi")),
        max_tokens = 1
    )
}
