package com.shiye.journal.features.ai

/**
 * HTTP 引擎。抽成接口只有一个目的：**让客户端的错误翻译能被单元测试覆盖**。
 *
 * 单测里塞一个假的引擎，就能验证"401 → 认证失败""超时 → 超时提示"
 * 这些映射规则，不必真的去连一个服务。
 */
fun interface AiHttpEngine {

    /**
     * 发一次 POST。
     * @throws AiHttpException 任何失败都以它抛出；不抛就表示拿到了 2xx 的响应体
     */
    @Throws(AiHttpException::class)
    fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutSeconds: Int
    ): String
}

/**
 * AI 客户端。
 *
 * 三条硬规矩（规格 §8.1 / §8.2 / §9.3）：
 * 1. **不引第三方 AI SDK**，只有一个 HTTP 客户端；
 * 2. **默认关闭**：[AiConfig.mode] 为 OFF 时直接拒绝，连地址都不会去解析；
 * 3. **不记录请求体** —— 里面是用户日记正文。日志里最多出现状态码。
 */
class AiClient(private val engine: AiHttpEngine) {

    /**
     * 发一轮对话。
     *
     * @return 成功返回模型回复；失败返回 [AiFailure]。**不用异常**，
     *         因为失败是这条链路里的正常分支，UI 要把它显示出来而不是崩掉。
     */
    fun chat(config: AiConfig, messages: List<AiProtocol.Message>): AiResult {
        if (config.mode == AiMode.OFF) {
            return AiResult.Failure(AiFailure(AiErrorKind.NOT_CONFIGURED))
        }
        if (!config.isUsable) {
            return AiResult.Failure(AiFailure(AiErrorKind.NOT_CONFIGURED))
        }
        val url = AiProtocol.chatCompletionsUrl(config.baseUrl)
            ?: return AiResult.Failure(AiFailure(AiErrorKind.BAD_URL))

        val request = AiProtocol.ChatRequest(model = config.model, messages = messages)
        val headers = buildMap {
            put("Content-Type", "application/json")
            // 本地模型通常不需要 Key，也就不带这个头 —— 不给无关地址送凭据。
            // 这里再洗一次：用户那条 Key 可能是从旧版本存下来的脏数据
            val key = AiCredentials.sanitize(config.apiKey)
            if (config.isCloud && key.isNotBlank()) {
                put("Authorization", "Bearer $key")
            }
        }

        return try {
            val body = engine.post(
                url = url,
                headers = headers,
                body = AiProtocol.encodeRequest(request),
                timeoutSeconds = config.timeoutSeconds
            )
            val reply = AiProtocol.extractReply(body)
            if (reply == null) {
                AiResult.Failure(AiFailure(AiErrorKind.BAD_RESPONSE, "结构不认识"))
            } else {
                AiResult.Success(reply.trim())
            }
        } catch (e: AiHttpException) {
            AiResult.Failure(translate(e, config))
        } catch (e: Exception) {
            // 兜底：任何没预见到的都归到"连不上"，至少提示得像人话
            AiResult.Failure(AiFailure(AiErrorKind.UNREACHABLE, e.javaClass.simpleName))
        }
    }

    /** 测试连接：发一个极小的请求，只看能不能通 */
    fun ping(config: AiConfig): AiResult =
        chat(config, AiProtocol.pingRequest(config.model).messages)

    /**
     * HTTP 异常 → 人话失败。
     *
     * 状态码的判断顺序是有讲究的：先认 401/403 与 404 这两个用户最容易自己修好的，
     * 剩下的非 2xx 才笼统归为"返回用不了"。
     */
    private fun translate(e: AiHttpException, config: AiConfig): AiFailure = when (e.reason) {
        AiHttpException.AiHttpCause.IO ->
            AiFailure(AiErrorKind.UNREACHABLE, e.message?.take(80).orEmpty())

        AiHttpException.AiHttpCause.TIMEOUT ->
            AiFailure(AiErrorKind.TIMEOUT, "${config.timeoutSeconds} 秒")

        AiHttpException.AiHttpCause.BAD_URL ->
            AiFailure(AiErrorKind.BAD_URL)

        AiHttpException.AiHttpCause.BAD_CREDENTIAL ->
            AiFailure(AiErrorKind.BAD_CREDENTIAL)

        AiHttpException.AiHttpCause.UNREADABLE_BODY ->
            AiFailure(AiErrorKind.BAD_RESPONSE, "读不出内容")

        AiHttpException.AiHttpCause.HTTP_STATUS -> when (e.statusCode) {
            401, 403 -> AiFailure(AiErrorKind.AUTH)
            404 -> AiFailure(AiErrorKind.MODEL_NOT_FOUND, config.model)
            in 400..499 -> AiFailure(AiErrorKind.BAD_RESPONSE, "HTTP ${e.statusCode}")
            in 500..599 -> AiFailure(
                AiErrorKind.BAD_RESPONSE,
                "服务端错误 HTTP ${e.statusCode}"
            )
            else -> AiFailure(AiErrorKind.BAD_RESPONSE, "HTTP ${e.statusCode}")
        }
    }
}

/** 一次 AI 调用的结果。成功就是一段文本，失败带着分类好的原因。 */
sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Failure(val failure: AiFailure) : AiResult
}
