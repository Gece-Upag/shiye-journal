package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.domain.AiPrompts
import com.shiye.journal.features.ai.AiClient
import com.shiye.journal.features.ai.AiConfig
import com.shiye.journal.features.ai.AiConfigStore
import com.shiye.journal.features.ai.AiCredentials
import com.shiye.journal.features.ai.AiMode
import com.shiye.journal.features.ai.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 配置页：管理多套配置档案，并指定当前用哪一套。
 *
 * 三条原则（规格 §8）：
 * 1. **默认关闭**：没选任何档案时就是关闭状态，进这个页面本身不发任何请求；
 * 2. **只有「测试连接」会联网**，且必须用户点；
 * 3. **API Key 只进加密存储**，页面用掩码显示，日志一概不打（§9.3）。
 *
 * 交互上的一条决定：**"用哪套"和"改哪套"是两件事**。
 * 点列表里的名字是切换使用，点「编辑」才是改它 ——
 * 免得"只想改个名字，结果把它切成正在用的了"。
 */
/** 三类可自定义的提示词（和 AiPrompts 里的三个函数一一对应） */
enum class AiPromptKind {
    /** 润色某个字段 */
    POLISH,

    /** 一篇日记的建议（AI 总结） */
    ENTRY_ADVICE,

    /** 最近七天的建议 */
    ADVICE
}

class AiSettingsViewModel(
    private val store: AiConfigStore,
    private val client: AiClient
) : ViewModel() {

    /** 全部档案（不含 Key） */
    val profiles: StateFlow<List<AiConfig>> = store.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.currentProfiles())

    /** 当前正在用哪一套；null = 关闭 */
    val activeId: StateFlow<String?> = store.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.currentActiveProfileId())

    /** 正在编辑的那份。id 为空 = 还没存过的新档案 */
    // 一进页面就带上默认提示词：编辑区此刻是"新建配置"的状态，
    // 展开提示词那一栏就该看到默认内容，而不是三个空框
    private val _draft = MutableStateFlow(AiConfig(mode = AiMode.CLOUD).withDefaultRules())
    val draft: StateFlow<AiConfig> = _draft.asStateFlow()

    /** 正在编辑的档案里已保存的 Key（掩码），用来显示"已经填过了" */
    private val _savedKeyMask = MutableStateFlow("")
    val savedKeyMask: StateFlow<String> = _savedKeyMask.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 刚才粘贴的 Key 里有没有被自动清掉的脏字符 */
    private val _keyCleaned = MutableStateFlow(false)
    val keyCleaned: StateFlow<Boolean> = _keyCleaned.asStateFlow()

    val canStoreKey: Boolean = store.canStoreKey

    // ---------- 列表操作 ----------

    /** 选用这一套 */
    fun use(id: String) {
        store.setActive(id)
        val name = store.currentProfiles().firstOrNull { it.id == id }?.displayName() ?: "这套配置"
        _message.value = "已切换到「$name」"
    }

    /** 关闭 AI：整套关掉，配置都留着 */
    fun turnOff() {
        store.setActive(null)
        _message.value = "已关闭 AI。不会再发起任何网络请求。"
    }

    /**
     * 把留空的提示词填上默认内容。
     *
     * **默认提示词直接摆在框里**让人在上面改，比给个空框友好得多 ——
     * 空白框没人知道该写什么，而照着默认改，一行字就能调出自己的风格。
     * 存的时候再规范化回去（见 [normalizeRules]），存储层仍然是"留空=默认"。
     */
    private fun AiConfig.withDefaultRules(): AiConfig = copy(
        polishRule = polishRule.ifBlank { AiPrompts.DEFAULT_POLISH_RULE },
        entryAdviceRule = entryAdviceRule.ifBlank { AiPrompts.DEFAULT_ENTRY_ADVICE_RULE },
        adviceRule = adviceRule.ifBlank { AiPrompts.DEFAULT_ADVICE_RULE }
    )

    /**
     * 存之前把"和默认一模一样"的内容还原成空串。
     *
     * 好处：以后我们改进默认提示词，老配置会自动用上新的；
     * 而用户真改过的那部分原样保留，不会被新默认值盖掉。
     */
    private fun AiConfig.normalizeRules(): AiConfig = copy(
        polishRule = polishRule.sameAsDefaultOrKeep(AiPrompts.DEFAULT_POLISH_RULE),
        entryAdviceRule = entryAdviceRule.sameAsDefaultOrKeep(AiPrompts.DEFAULT_ENTRY_ADVICE_RULE),
        adviceRule = adviceRule.sameAsDefaultOrKeep(AiPrompts.DEFAULT_ADVICE_RULE)
    )

    private fun String.sameAsDefaultOrKeep(default: String): String =
        if (trim() == default.trim()) "" else this

    /** 载入某一套到编辑区 */
    fun edit(id: String) {
        val profile = store.currentProfiles().firstOrNull { it.id == id } ?: return
        val key = store.keyOf(id)
        _draft.value = profile.copy(apiKey = key).withDefaultRules()
        _savedKeyMask.value = maskedKey(key)
        clearTransient()
    }

    /** 开始新建一套 */
    fun startNew() {
        _draft.value = AiConfig(mode = AiMode.CLOUD).withDefaultRules()
        _savedKeyMask.value = ""
        clearTransient()
    }

    fun delete(id: String) {
        store.deleteProfile(id)
        if (_draft.value.id == id) startNew()
        _message.value = "已删除这套配置"
    }

    private fun clearTransient() {
        _testResult.value = null
        _keyCleaned.value = false
    }

    // ---------- 编辑 ----------

    fun setName(value: String) = update(_draft.value.copy(name = value))

    fun setMode(mode: AiMode) = update(_draft.value.copy(mode = mode))

    fun setBaseUrl(value: String) = update(_draft.value.copy(baseUrl = value))

    fun setModel(value: String) = update(_draft.value.copy(model = value))

    fun setApiKey(raw: String) {
        val cleaned = AiCredentials.sanitize(raw)
        _keyCleaned.value = AiCredentials.hasInvisibleJunk(raw)
        update(_draft.value.copy(apiKey = cleaned))
    }

    /**
     * 超时秒数。夹到合法区间：太小等于必超时，太大等得人难受。
     *
     * 这里只管夹范围，**不管输入过程** ——
     * 输入过程中去改写文本会把光标弄乱（见 `NumberInput` 的说明）。
     */
    fun setTimeout(seconds: Int) {
        update(
            _draft.value.copy(
                timeoutSeconds = seconds.coerceIn(
                    AiConfig.MIN_TIMEOUT_SECONDS,
                    AiConfig.MAX_TIMEOUT_SECONDS
                )
            )
        )
    }

    // ---------- 自定义提示词 ----------

    fun setPolishRule(value: String) = update(_draft.value.copy(polishRule = value))

    fun setEntryAdviceRule(value: String) = update(_draft.value.copy(entryAdviceRule = value))

    fun setAdviceRule(value: String) = update(_draft.value.copy(adviceRule = value))

    /** 某一项恢复默认：把默认内容重新填回框里（存的时候会被规范成空） */
    fun resetRule(which: AiPromptKind) {
        update(
            when (which) {
                AiPromptKind.POLISH -> _draft.value.copy(polishRule = AiPrompts.DEFAULT_POLISH_RULE)
                AiPromptKind.ENTRY_ADVICE ->
                    _draft.value.copy(entryAdviceRule = AiPrompts.DEFAULT_ENTRY_ADVICE_RULE)

                AiPromptKind.ADVICE -> _draft.value.copy(adviceRule = AiPrompts.DEFAULT_ADVICE_RULE)
            }
        )
        _message.value = "已恢复默认提示词"
    }

    /** 提示词这一栏默认收起，点开才展开 —— 不是每个人都要动它 */
    fun togglePromptsExpanded() {
        _promptsExpanded.value = !_promptsExpanded.value
    }

    private val _promptsExpanded = MutableStateFlow(false)
    val promptsExpanded: StateFlow<Boolean> = _promptsExpanded.asStateFlow()

    /** 套用服务商预设：只填地址与模型名，Key 永远要用户自己粘 */
    fun applyPreset(baseUrl: String, model: String, provider: String) {
        update(_draft.value.copy(baseUrl = baseUrl, model = model, provider = provider))
    }

    private fun update(next: AiConfig) {
        _draft.value = next
        _testResult.value = null
    }

    /** 保存这一套（新建或覆盖） */
    fun save() {
        val draft = _draft.value
        if (draft.name.isBlank()) {
            _message.value = "给这套配置起个名字吧，不然以后分不清哪个是哪个"
            return
        }
        if (draft.mode == AiMode.CLOUD && draft.apiKey.isBlank() && !canStoreKey) {
            _message.value = "这台设备无法安全保存 API Key（Keystore 不可用）"
            return
        }
        val saved = store.saveProfile(draft.normalizeRules())
        // 存完之后框里仍显示默认内容（存的是空，显示的是默认），别让框突然空掉
        _draft.value = saved.copy(apiKey = draft.apiKey).withDefaultRules()
        _savedKeyMask.value = maskedKey(store.keyOf(saved.id))
        _message.value = when {
            draft.mode == AiMode.OFF -> "已保存。这一套是关闭状态。"
            // 保存完顺手启用：填了半天结果没生效会很困惑
            store.currentActiveProfileId() != saved.id -> {
                store.setActive(saved.id)
                "已保存，并切换到「${saved.displayName()}」。"
            }
            else -> "已保存。API Key 已加密存在本机，不会写进备份。"
        }
    }

    /** 测试连接：唯一会发起网络请求的地方，必须由用户点击触发 */
    fun testConnection() {
        val target = _draft.value
        if (!target.isUsable) {
            _testResult.value = "先把地址和模型名填完整"
            return
        }
        _testing.value = true
        _testResult.value = null
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { client.ping(target) }
            _testing.value = false
            _testResult.value = when (outcome) {
                is AiResult.Success -> "连通了，模型「${target.model}」有响应。"
                is AiResult.Failure -> outcome.failure.message(localTarget = target.mode == AiMode.LOCAL)
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        /** 页面显示用的 Key 掩码：能看出"填了 / 没填"和大概长度，又不泄露内容 */
        fun maskedKey(key: String): String = when {
            key.isBlank() -> ""
            key.length <= 8 -> "••••••••"
            else -> key.take(4) + "••••••••" + key.takeLast(4)
        }
    }
}
