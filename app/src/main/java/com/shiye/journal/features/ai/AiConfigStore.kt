package com.shiye.journal.features.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * AI 配置的存储：**多套档案 + 指定当前用哪一套**。
 *
 * 用户会同时配好几家（比如一个 DeepSeek、一个本机 Ollama），
 * 每次换着用都要重新填一遍地址和 Key 太折磨人了，所以整套配置都存下来，切换只点一下。
 *
 * 三个刻意的决定：
 * 1. **档案本身不含 API Key**，Key 按档案 id 单独存在加密区（[keyOf]）；
 *    列表本体用普通偏好存即可，也方便整体读写；
 * 2. **配置不进备份**（换台机器恢复数据时不该带过来原机器的凭据和开关）；
 * 3. **旧的单套配置会自动迁移**成一份档案，用户升级后不用重新填。
 *
 * ## 加密区必须**懒加载**（启动性能）
 *
 * [EncryptedSharedPreferences] 创建时要连 Android Keystore 生成/读取主密钥，
 * 这在真机上要几十到上百毫秒，而且**必须在调用的线程上完成**。
 * 原先它在构造函数里就建好，而配置对象是在首帧重组时被 [ShiyeApp] 取用的，
 * 等于把 Keystore 的开销压在了第一帧上 —— 哪怕用户根本没开 AI。
 *
 * 改成 lazy 之后：AI 关着（绝大多数情况）时 [currentActiveConfig] 走到
 * "没有启用的档案"就返回了，一次都不碰 [secure]，Keystore 完全不初始化；
 * 只有真的要读/写某个 Key、或用户进了 AI 设置页，才会第一次创建它。
 */
class AiConfigStore(context: Context) {

    private val plain: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)

    /**
     * Key 专用的加密存储，**首次真正用到时才创建**。
     * 创建失败（极少数设备 Keystore 异常）时降级为 null，此时读 Key 会得到空串。
     */
    private val secureLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_AI_SECRET,
                MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    private val secure: SharedPreferences? get() = secureLazy.value

    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateLegacyIfNeeded()
    }

    // ---------------- 读 ----------------

    /** 全部档案（**不含 Key**），按加入先后排列 */
    val profiles: Flow<List<AiConfig>> = plain.observeChanges().map { currentProfiles() }

    fun currentProfiles(): List<AiConfig> = runCatching {
        json.decodeFromString<List<AiConfig>>(plain.getString(KEY_PROFILES, null) ?: "[]")
    }.getOrDefault(emptyList())

    /** 当前生效的档案 id；null = 关闭 AI */
    val activeProfileId: Flow<String?> = plain.observeChanges()
        .map { plain.getString(KEY_ACTIVE_ID, null) }

    fun currentActiveProfileId(): String? = plain.getString(KEY_ACTIVE_ID, null)

    /**
     * 当前**生效**的配置（含 Key），AI 关着时是 [AiMode.OFF]。
     *
     * 业务代码（润色、总结、建议）一律读这个，不关心有几套档案。
     */
    val activeConfig: Flow<AiConfig> = plain.observeChanges().map { currentActiveConfig() }

    fun currentActiveConfig(): AiConfig {
        val id = currentActiveProfileId() ?: return AiConfig(mode = AiMode.OFF)
        val profile = currentProfiles().firstOrNull { it.id == id } ?: return AiConfig(mode = AiMode.OFF)
        return profile.copy(apiKey = keyOf(id))
    }

    /** 某个档案的 Key（已解密） */
    fun keyOf(id: String): String = secure?.getString(keyFieldOf(id), "").orEmpty()

    // ---------------- 写 ----------------

    /**
     * 保存一套配置。
     *
     * 没有 id 就是新建（自动分配 id）；有 id 就覆盖那一份。
     */
    fun saveProfile(config: AiConfig): AiConfig {
        val isNew = config.id.isBlank()
        val saved = if (isNew) config.copy(id = newProfileId()) else config
        val others = currentProfiles().filter { it.id != saved.id }
        writeProfiles(others + saved.withoutKey())
        // Key 单独进加密区，顺手洗干净（从网页复制常带换行）
        writeKey(saved.id, AiCredentials.sanitize(saved.apiKey))
        // 新建的第一套自动启用：否则用户填完发现没生效，会以为没保存上
        if (isNew && currentActiveProfileId() == null) {
            setActive(saved.id)
        }
        return saved
    }

    /** 删除一套配置，连带删它的 Key */
    fun deleteProfile(id: String) {
        writeProfiles(currentProfiles().filter { it.id != id })
        secure?.edit { remove(keyFieldOf(id)) }
        if (currentActiveProfileId() == id) {
            // 删掉的是正在用的那套：退回关闭，而不是随便挑一套顶上
            setActive(null)
        }
    }

    /** 指定用哪一套；null 表示关闭 AI */
    fun setActive(id: String?) {
        plain.edit {
            if (id == null) remove(KEY_ACTIVE_ID) else putString(KEY_ACTIVE_ID, id)
        }
    }

    /**
     * 这台设备能不能安全存 Key（Keystore 异常时为 false）。
     *
     * 注意读它会**触发**加密区创建（首次调用有 Keystore 开销），
     * 所以只在 AI 设置页需要提示用户时用，别放进启动路径或每帧读的地方。
     */
    val canStoreKey: Boolean get() = secure != null

    // ---------------- 内部 ----------------

    private fun writeProfiles(list: List<AiConfig>) {
        plain.edit { putString(KEY_PROFILES, json.encodeToString(list.map { it.withoutKey() })) }
    }

    private fun writeKey(id: String, value: String) {
        secure?.edit { putString(keyFieldOf(id), value) }
    }

    private fun keyFieldOf(id: String) = "$KEY_PREFIX_API_KEY$id"

    private fun newProfileId(): String = java.util.UUID.randomUUID().toString()

    private fun SharedPreferences.observeChanges(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key.orEmpty()) }
        registerOnSharedPreferenceChangeListener(listener)
        trySend("")
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * 把旧版本"只有一套配置"的数据搬成一份档案。
     *
     * 判断依据是新键不存在而旧键存在；跑完**不删旧键** ——
     * 万一这次迁移没成功，下次还能再试，不至于把用户填过的地址弄丢。
     */
    private fun migrateLegacyIfNeeded() {
        if (plain.contains(KEY_PROFILES)) return
        val legacyMode = plain.getString(KEY_MODE, null) ?: return
        val mode = AiMode.fromStorageKey(legacyMode)
        val legacyKey = secure?.getString(KEY_API_KEY, "").orEmpty()

        val provider = plain.getString(KEY_PROVIDER, "").orEmpty()
        val migrated = AiConfig(
            id = LEGACY_PROFILE_ID,
            name = provider.ifBlank { if (mode == AiMode.LOCAL) "本地模型" else "默认配置" },
            mode = mode,
            baseUrl = plain.getString(KEY_BASE_URL, "").orEmpty(),
            model = plain.getString(KEY_MODEL, "").orEmpty(),
            provider = provider,
            timeoutSeconds = plain.getInt(KEY_TIMEOUT, AiConfig.DEFAULT_TIMEOUT_SECONDS)
        )
        writeProfiles(listOf(migrated))
        writeKey(migrated.id, AiCredentials.sanitize(legacyKey))
        // 原来开着就继续用这套；原来关着就保持关闭（配置留着，下次打开不用重填）
        if (mode != AiMode.OFF) {
            setActive(migrated.id)
        }
    }

    companion object {
        private const val PREFS_AI = "shiye_ai"
        private const val PREFS_AI_SECRET = "shiye_ai_secret"

        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
        private const val KEY_PREFIX_API_KEY = "api_key_"

        // 旧版单套配置用的键（只用于迁移，不再写入）
        private const val KEY_MODE = "mode"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_API_KEY = "api_key"

        /** 迁移产生的那份档案用固定 id，保证多次调用不会重复建 */
        const val LEGACY_PROFILE_ID = "profile-legacy"
    }
}
