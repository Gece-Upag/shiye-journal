package com.shiye.journal.features.ai

/**
 * AI 失败的**分类**。
 *
 * 规格 §8.1 要求"网络异常、请求超时、模型不存在、认证失败和响应格式错误
 * 必须给出清晰提示"。分类的意义就在这里：笼统一句"请求失败"没用，
 * 用户得知道是该检查网络、还是 Key 填错了、还是模型名打错了。
 */
enum class AiErrorKind {
    /** 还没配置（或配置不完整） */
    NOT_CONFIGURED,

    /** 连不上：地址错、没网、DNS 挂了 */
    UNREACHABLE,

    /** 超时 */
    TIMEOUT,

    /** 401 / 403：Key 不对或没权限 */
    AUTH,

    /** 404：模型不存在 */
    MODEL_NOT_FOUND,

    /** 返回了但不是能用的内容：非 2xx、JSON 坏、结构不认识 */
    BAD_RESPONSE,

    /** 地址本身不合法 */
    BAD_URL,

    /**
     * 凭据里有不能放进 HTTP 头的字符。
     *
     * 和 BAD_URL 分开是**被真实 bug 教会**的：这两个以前合并报成"地址格式不对"，
     * 结果用户对着一个完全正确的 DeepSeek 地址反复检查，
     * 真正的问题（复制 Key 时带进来的换行）却没人提。
     */
    BAD_CREDENTIAL,

    /** 输入是空的，没什么可做的 */
    EMPTY_INPUT
}

/**
 * 一次失败的完整信息。
 *
 * [detail] 只放**技术线索**（状态码、异常类名），绝不放请求体 ——
 * 请求体里有用户日记正文（规格 §9.3）。
 */
data class AiFailure(
    val kind: AiErrorKind,
    val detail: String = ""
) {
    /**
     * 给用户看的一句话。说清"是什么问题 + 下一步该干什么"。
     *
     * @param localTarget 目标是不是本地模型。告诉了它，连不上时才能给出
     *   **局域网专属**的排查步骤 —— 手机上连电脑的模型，卡住的原因和云端完全不同：
     *   不是地址填错，而是不在一个网络 / Ollama 只听 127.0.0.1 / 防火墙拦了端口。
     */
    fun message(localTarget: Boolean = false): String = when (kind) {
        AiErrorKind.NOT_CONFIGURED ->
            "还没配置 AI。到「设置 → AI 辅助」里选模式、填上地址和模型名。"

        AiErrorKind.UNREACHABLE ->
            buildString {
                append("连不上这个地址")
                if (detail.isNotBlank()) append("（$detail）")
                append(
                    if (localTarget) {
                        "。手机连电脑上的模型要过三道坎：① 手机和电脑在同一个网络（同一 Wi-Fi，或用手机开热点让电脑连）；" +
                            "② Ollama 设了 OLLAMA_HOST=0.0.0.0 并重启；③ 电脑防火墙放行了这个端口。"
                    } else {
                        "。检查一下地址是否填对、服务是否开着、网络是否通。"
                    }
                )
            }

        AiErrorKind.TIMEOUT ->
            buildString {
                append("等了 ${detail.ifBlank { "很久" }} 也没等到回复。")
                append(
                    if (localTarget) {
                        "本地模型首次加载要几十秒，可以把超时调到 60 秒以上再试；" +
                            "一直这样则换个小一点的模型。"
                    } else {
                        "可以调大超时，或换个更轻的模型再试。"
                    }
                )
            }

        AiErrorKind.AUTH ->
            "认证失败：API Key 可能填错了、已过期，或者这个 Key 没有该模型的权限。"

        AiErrorKind.MODEL_NOT_FOUND ->
            buildString {
                append("找不到这个模型")
                if (detail.isNotBlank()) append("「$detail」")
                append("。模型名要和服务那边完全一致，包括大小写和版本号。")
            }

        AiErrorKind.BAD_RESPONSE ->
            buildString {
                append("返回的内容用不了")
                if (detail.isNotBlank()) append("（$detail）")
                append("。这个地址可能不是 OpenAI 兼容接口。")
            }

        AiErrorKind.BAD_URL ->
            "地址格式不对。要带协议头，例如 http://192.168.1.10:11434/v1"

        AiErrorKind.BAD_CREDENTIAL ->
            "API Key 里有不能用的字符，多半是复制时混进了换行或看不见的符号。" +
                "请重新粘贴一次（或直接手打），注意别选中行尾的换行。"

        AiErrorKind.EMPTY_INPUT ->
            "这里还是空的，没什么可处理的。"
    }
}

/**
 * HTTP 层抛出的原始错误。
 *
 * 客户端负责把它翻译成 [AiFailure]，UI 只见 [AiFailure]。
 * 这样"HTTP 细节"和"人话提示"各管一摊，也便于单测只覆盖翻译规则。
 */
class AiHttpException(
    val statusCode: Int? = null,
    /**
     * 失败原因。**不能叫 cause**：Throwable 已经有 cause 了，
     * 同名会遮蔽父类成员而编译不过。
     */
    val reason: AiHttpCause,
    message: String = ""
) : Exception(message) {

    enum class AiHttpCause {
        /** 连不上 / DNS / 连接被拒 */
        IO,

        /** 超时 */
        TIMEOUT,

        /** 地址不合法 */
        BAD_URL,

        /**
         * API Key 里含非法字符。
         *
         * 必须是独立的一类：把它当成"地址不对"来报，
         * 用户会去查地址，而问题其实在 Key —— 这个误导真实发生过。
         */
        BAD_CREDENTIAL,

        /** 拿到了响应但不是 2xx */
        HTTP_STATUS,

        /** 拿到了 2xx 但内容读不出来 */
        UNREADABLE_BODY
    }
}
