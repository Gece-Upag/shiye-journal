package com.shiye.journal.features.ai

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * AI 的三种模式（规格 §8）。
 *
 * **默认是关闭的**：装完 App 一个字节都不会往外发。
 * 只有用户自己进设置页打开、填好地址，才可能发起请求。
 */
/**
 * 模式的序列化**必须走 [storageKey] 而不是枚举常量名**。
 *
 * 两个理由：
 * 1. 常量名（CLOUD）是实现细节，哪天改个名字，用户存的配置就全读不出来了；
 *    storageKey 是稳定的小写键，与旧版偏好里用的保持一致；
 * 2. 遇到不认识的值要**退回关闭**，而不是抛异常 ——
 *    一条记录坏掉不该连累整个列表，而"关掉"永远是安全的那一侧。
 */
object AiModeSerializer : KSerializer<AiMode> {
    override val descriptor = PrimitiveSerialDescriptor("AiMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AiMode) {
        encoder.encodeString(value.storageKey)
    }

    override fun deserialize(decoder: Decoder): AiMode =
        AiMode.fromStorageKey(decoder.decodeString())
}

@Serializable(with = AiModeSerializer::class)
enum class AiMode(val storageKey: String) {
    /** 关闭。日记/记账/灵感/设置全部照常用，零网络行为 */
    OFF("off"),

    /**
     * 本地模型：自己电脑上跑的 Ollama / LM Studio 之类。
     * 同样要填明确的地址 —— 规格 §8.2 要求"本地模型也需要明确请求的目标地址"。
     */
    LOCAL("local"),

    /** 云端 API：OpenAI 兼容接口 + API Key */
    CLOUD("cloud");

    companion object {
        fun fromStorageKey(key: String?): AiMode =
            entries.firstOrNull { it.storageKey == key } ?: OFF
    }
}

/**
 * AI 配置。
 *
 * 两个刻意的决定：
 * 1. **[apiKey] 不参与 `toString`/`equals` 之外的任何输出**，日志、备份、崩溃上报里都不许出现
 *    （规格 §8.1、§9.3）。它是这个数据类里唯一"见不得人"的字段。
 * 2. **[provider] 只是给人看的名字**，不参与任何请求逻辑 ——
 *    规格明确要求"不得将某个提供商写死为唯一实现"。
 */
@Serializable
data class AiConfig(
    /**
     * 档案 id。空串表示"还没存过"的草稿。
     *
     * 为什么要 id：用户会同时配好几家（DeepSeek 一个、本机 Ollama 一个），
     * 靠名字区分不可靠 —— 名字可以随时改，而 API Key 是按 id 存的，
     * 名字一改就把 Key 弄丢就太蠢了。
     */
    val id: String = "",

    /** 用户给这套配置起的名字，例如「DeepSeek」「家里的小模型」 */
    val name: String = "",

    val mode: AiMode = AiMode.OFF,
    /** 服务地址，例如 `http://192.168.1.10:11434/v1` 或 `https://api.openai.com/v1` */
    val baseUrl: String = "",
    /** 模型名称，例如 `qwen2.5` / `gpt-4o-mini` */
    val model: String = "",
    /** 云端服务商，仅作展示 */
    val provider: String = "",
    /** 单次请求超时，秒 */
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,

    /**
     * 润色的自定义任务要求。留空用默认。
     *
     * 三个提示词都**跟随档案**：换模型往往也想要不同的说话方式，
     * 而且不同模型对同一句提示词的敏感程度差很多。
     * 全都留空就完全等同默认行为，不想折腾的人不用管。
     */
    val polishRule: String = "",

    /** 一篇日记的建议（AI 总结）的自定义任务要求；留空用默认 */
    val entryAdviceRule: String = "",

    /** 最近七天建议的自定义任务要求；留空用默认 */
    val adviceRule: String = "",
    /** API Key。只有云端模式需要；本地模式留空 */
    val apiKey: String = ""
) {
    /** 是否可以发起请求：开着、有地址、有模型名 */
    val isUsable: Boolean
        get() = mode != AiMode.OFF && baseUrl.isNotBlank() && model.isNotBlank()

    val isCloud: Boolean get() = mode == AiMode.CLOUD

    /** 显示用：把地址里的 Key 痕迹去掉，避免截图/日志泄露 */
    fun safeSummary(): String = when (mode) {
        AiMode.OFF -> "已关闭"
        AiMode.LOCAL -> "本地模型 · $model"
        AiMode.CLOUD -> "${provider.ifBlank { "云端" }} · $model"
    }

    /** 这套配置在列表里叫什么。没起名字就退回模式名，总得让人认出来是哪套 */
    fun displayName(): String = name.ifBlank { safeSummary() }

    /** 去掉 Key 的副本。**落盘前一定要过一遍**：Key 有单独的加密存储 */
    fun withoutKey(): AiConfig = copy(apiKey = "")

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30

        /**
         * 超时下限：再短就没意义了 —— 本地模型光把模型读进显存就可能要几秒，
         * 设成 1 秒只会得到一堆没必要的超时提示。
         */
        const val MIN_TIMEOUT_SECONDS = 5

        /**
         * 超时上限：本地大模型首次加载要好几十秒，
         * 给到 10 分钟足够覆盖最慢的情况；再往上等下去用户自己也受不了。
         */
        const val MAX_TIMEOUT_SECONDS = 600

        /** 常见服务商的预设，只是填表时的快捷方式，用户可以随便改地址与模型 */
        val PRESETS: List<AiPreset> = listOf(
            AiPreset(
                label = "OpenAI 兼容接口",
                provider = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
                needsKey = true,
                mode = AiMode.CLOUD
            ),
            AiPreset(
                label = "DeepSeek",
                provider = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat",
                needsKey = true,
                mode = AiMode.CLOUD
            ),
            // 局域网 IP（192.168.1.100 是占位示例，用户要换成自己电脑的）。
            // 早先填的 10.0.2.2 是**模拟器**访问宿主的特例地址，真机上根本不通 ——
            // 手机 App 连电脑上的模型，走的是"同一个 Wi-Fi / 热点"这条局域网路径
            AiPreset(
                label = "电脑上 Ollama",
                provider = "Ollama",
                baseUrl = "http://192.168.1.100:11434/v1",
                model = "qwen2.5",
                needsKey = false,
                mode = AiMode.LOCAL
            ),
            AiPreset(
                label = "电脑上 LM Studio",
                provider = "LM Studio",
                baseUrl = "http://192.168.1.100:1234/v1",
                model = "local-model",
                needsKey = false,
                mode = AiMode.LOCAL
            )
        )
    }
}

/** 一个服务商预设。填表时一键带入，之后仍可随意修改。 */
data class AiPreset(
    val label: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val needsKey: Boolean,
    /** 属于哪种模式。UI 按**当前选的模式**过滤显示 ——
     *  本地模型模式下冒出"OpenAI 兼容接口"只会让人困惑 */
    val mode: AiMode
)
