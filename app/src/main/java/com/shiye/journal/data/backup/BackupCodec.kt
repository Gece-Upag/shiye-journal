package com.shiye.journal.data.backup

import com.shiye.journal.data.local.entity.AccountEntity
import com.shiye.journal.data.local.entity.CategoryEntity
import com.shiye.journal.data.local.entity.DiaryEntryEntity
import com.shiye.journal.data.local.entity.DiaryFieldEntity
import com.shiye.journal.data.local.entity.AiNoteEntity
import com.shiye.journal.data.local.entity.InspirationEntity
import com.shiye.journal.data.local.entity.InspirationPageEntity
import com.shiye.journal.data.local.entity.LoanEntity
import com.shiye.journal.data.local.entity.TemplateEntity
import com.shiye.journal.data.local.entity.TemplateFieldEntity
import com.shiye.journal.data.local.entity.TransactionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份容器的编解码与校验规则。
 *
 * 容器是 ZIP，里面两个文件：
 * - `backup.json`：全部结构化数据（恢复**只认这个**，Markdown 只是阅读用，不参与恢复）；
 * - `checksum.txt`：backup.json 的 SHA-256（大写十六进制）。
 *
 * 校验的意义：传输/存储损坏的备份**必须在解析前被拦下**，
 * 不能等到插了一半数据才发现文件是坏的 —— 那正是恢复流程最危险的时刻。
 *
 * 这个类是纯 JVM 的（只用 java.util.zip），可以直接单测，
 * 不碰 Context 和 SAF；SAF 的读写都由 [BackupManager] 包在外层。
 */
object BackupCodec {

    /** 当前写出的备份格式版本。结构发生不兼容变化时 +1。 */
    const val FORMAT_VERSION = 1

    /** 当前应用数据库 schema 版本（与 AppDatabase.version 保持一致）。 */
    const val DB_VERSION = 5

    internal const val ENTRY_DATA = "backup.json"
    internal const val ENTRY_CHECKSUM = "checksum.txt"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 备份内容。字段直接用 Entity：备份的本意就是"原样搬数据"，
     * 再造一层 DTO 只会引入两份映射和两处可能不一致的地方。
     */
    @Serializable
    data class Payload(
        val formatVersion: Int,
        val dbVersion: Int,
        val exportedAt: Long,
        val diaryEntries: List<DiaryEntryEntity>,
        val diaryFields: List<DiaryFieldEntity>,
        val templates: List<TemplateEntity>,
        val templateFields: List<TemplateFieldEntity>,
        val transactions: List<TransactionEntity>,
        val loans: List<LoanEntity>,
        val categories: List<CategoryEntity>,
        val accounts: List<AccountEntity>,
        val inspirations: List<InspirationEntity>,
        /**
         * 灵感页。带默认值是为了**能读旧备份**：
         * v2 备份没有这个键，反序列化时按空列表处理，
         * 再由恢复流程补一个默认页把老灵感接住（见 BackupManager.restore）。
         */
        val inspirationPages: List<InspirationPageEntity> = emptyList(),

        /**
         * AI 存档。同样带默认值，v3 及更早的备份没有这一块。
         *
         * 注意备份里**没有 AI 配置**：地址、模型名、尤其是 API Key 都属于本机偏好，
         * 换台机器恢复时不该把原机器的凭据和"AI 已开启"状态一起带过来（规格 §8.1）。
         */
        val aiNotes: List<AiNoteEntity> = emptyList()
    )

    // ---------- 校验结果 ----------

    /** 解析后的判定结果。Rejected 必须带着"人能看懂"的原因给 UI 显示。 */
    sealed interface Check {
        data class Ok(val payload: Payload) : Check
        data class Rejected(val reason: String) : Check
    }

    /**
     * 格式与数据版本校验。
     *
     * - 更新的格式/数据库版本 → 拒绝（旧应用读不了新结构，硬插等于损坏数据）；
     * - 更旧的版本 → 接受，交给之后版本的迁移逻辑（规格 §7.4：旧备份必须保留格式版本）。
     */
    fun validate(payload: Payload): Check {
        if (payload.formatVersion > FORMAT_VERSION) {
            return Check.Rejected(
                "备份格式版本（v${payload.formatVersion}）比当前应用（v$FORMAT_VERSION）更新，请先升级应用"
            )
        }
        if (payload.formatVersion < 1) {
            return Check.Rejected("备份格式版本异常（v${payload.formatVersion}）")
        }
        if (payload.dbVersion > DB_VERSION) {
            return Check.Rejected(
                "备份数据来自更新的应用版本（数据库 v${payload.dbVersion} > v$DB_VERSION），无法在此版本恢复"
            )
        }
        return Check.Ok(payload)
    }

    // ---------- 编解码 ----------

    fun encode(payload: Payload): String = json.encodeToString(payload)

    fun decode(text: String): Payload = json.decodeFromString(text)

    // ---------- ZIP 容器 ----------

    /** 打包成 ZIP 字节流：backup.json + checksum.txt */
    fun pack(payload: Payload): ByteArray {
        val data = encode(payload).toByteArray(Charsets.UTF_8)
        val checksum = sha256Hex(data)
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry(ENTRY_DATA))
            zos.write(data)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(ENTRY_CHECKSUM))
            zos.write(checksum.toByteArray(Charsets.US_ASCII))
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    /**
     * 解包并校验 ZIP。
     *
     * 任何一步不对（不是 ZIP、缺文件、校验和不匹配、JSON 坏、版本不支持）都抛
     * [BackupException]，由调用方转成 UI 提示 —— 绝不把半残数据交给恢复流程。
     */
    fun unpack(bytes: ByteArray): Payload {
        val entries = try {
            readZipEntries(bytes)
        } catch (e: Exception) {
            throw BackupException("这不是一个有效的备份文件（无法作为 ZIP 打开）", e)
        }
        val data = entries[ENTRY_DATA]
            ?: throw BackupException("备份文件缺少数据部分（$ENTRY_DATA），无法恢复")
        val checksum = entries[ENTRY_CHECKSUM]?.toString(Charsets.US_ASCII)?.trim()

        if (checksum != null) {
            val actual = sha256Hex(data)
            if (!actual.equals(checksum, ignoreCase = true)) {
                throw BackupException("备份文件已损坏（校验和不匹配），拒绝恢复以保护现有数据")
            }
        } else {
            throw BackupException("备份文件缺少校验信息（$ENTRY_CHECKSUM），无法确认完整性")
        }

        val payload = try {
            decode(data.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException("备份内容无法解析（JSON 损坏或结构不认识）", e)
        }
        return when (val check = validate(payload)) {
            is Check.Ok -> check.payload
            is Check.Rejected -> throw BackupException(check.reason)
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    result[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    /** SHA-256，大写十六进制。 */
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }
}

/** 备份/恢复链路的可预期失败。message 直接给人看。 */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
