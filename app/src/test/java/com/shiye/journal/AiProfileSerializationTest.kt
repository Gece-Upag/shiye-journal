package com.shiye.journal

import com.shiye.journal.features.ai.AiConfig
import com.shiye.journal.features.ai.AiMode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配置档案的落盘格式。
 *
 * 多套配置是序列化成一个 JSON 数组存在偏好里的，格式错了整套都读不出来 ——
 * 用户会看到"我保存的配置全没了"，所以这块必须有往返测试守住。
 */
class AiProfileSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun deepseek() = AiConfig(
        id = "p1",
        name = "DeepSeek",
        mode = AiMode.CLOUD,
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
        provider = "DeepSeek",
        apiKey = "sk-secret"
    )

    private fun local() = AiConfig(
        id = "p2",
        name = "家里的小模型",
        mode = AiMode.LOCAL,
        baseUrl = "http://192.168.1.10:11434/v1",
        model = "qwen2.5"
    )

    @Test
    fun 多套配置存取往返一致() {
        val list = listOf(deepseek(), local())
        val text = json.encodeToString(ListSerializer(AiConfig.serializer()), list.map { it.withoutKey() })
        val back = json.decodeFromString(ListSerializer(AiConfig.serializer()), text)
        assertEquals(2, back.size)
        assertEquals("DeepSeek", back[0].name)
        assertEquals(AiMode.CLOUD, back[0].mode)
        assertEquals("qwen2.5", back[1].model)
        assertEquals(AiMode.LOCAL, back[1].mode)
    }

    @Test
    fun 落盘的内容里不含APIKey() {
        val text = json.encodeToString(AiConfig.serializer(), deepseek().withoutKey())
        // Key 有单独的加密存储，绝不能出现在列表 JSON 里
        assertEquals(false, text.contains("sk-secret"))
        // 反过来说，不加 withoutKey 就会带上 —— 这正是这个方法存在的意义
        assertEquals(
            true,
            json.encodeToString(AiConfig.serializer(), deepseek()).contains("sk-secret")
        )
    }

    @Test
    fun 旧版本缺字段也能读进来() {
        // 将来给 AiConfig 加字段时，老数据不能因此读不出来
        val old = """
            [{"id":"p1","name":"DeepSeek","mode":"cloud","baseUrl":"https://x/v1","model":"m"}]
        """.trimIndent()
        val back = json.decodeFromString(ListSerializer(AiConfig.serializer()), old)
        assertEquals(1, back.size)
        assertEquals(AiMode.CLOUD, back[0].mode)
        assertEquals(AiConfig.DEFAULT_TIMEOUT_SECONDS, back[0].timeoutSeconds)
    }

    @Test
    fun 模式无法识别时退回关闭() {
        // 手改坏了配置文件的极端情况：宁可关掉也不能拿着怪配置去发请求
        val broken = """[{"id":"p1","name":"x","mode":"???"}]""".trimMargin()
        val back = json.decodeFromString(ListSerializer(AiConfig.serializer()), broken)
        assertEquals(AiMode.OFF, back[0].mode)
    }

    @Test
    fun 没起名字时显示模式摘要() {
        assertEquals("本地模型 · qwen2.5", local().copy(name = "").displayName())
        assertEquals("家里的小模型", local().displayName())
    }

    @Test
    fun 预设都要归属到某个模式() {
        // 预设按当前模式过滤显示；漏了 mode 就会变成默认 OFF，那个预设永远不出现
        AiConfig.PRESETS.forEach { preset ->
            assertTrue(
                "${preset.label} 没有设模式",
                preset.mode == AiMode.LOCAL || preset.mode == AiMode.CLOUD
            )
        }
    }

    @Test
    fun 本机预设用的是局域网地址不是模拟器地址() {
        // 10.0.2.2 只有模拟器能通，真机连电脑要走局域网 IP
        AiConfig.PRESETS.filter { it.mode == AiMode.LOCAL }.forEach { preset ->
            assertEquals(false, preset.baseUrl.contains("10.0.2.2"))
            assertTrue(preset.baseUrl.startsWith("http://"))
        }
    }

    @Test
    fun 云端预设都是https() {
        AiConfig.PRESETS.filter { it.mode == AiMode.CLOUD }.forEach { preset ->
            assertTrue(preset.baseUrl.startsWith("https://"))
            assertTrue(preset.needsKey)
        }
    }

    @Test
    fun 关闭态的档案不算可用() {
        val off = deepseek().copy(mode = AiMode.OFF)
        assertEquals(false, off.isUsable)
        assertTrue(deepseek().isUsable)
    }
}
