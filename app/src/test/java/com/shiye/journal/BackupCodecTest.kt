package com.shiye.journal

import com.shiye.journal.data.backup.BackupCodec
import com.shiye.journal.data.backup.BackupException
import com.shiye.journal.data.local.entity.CategoryEntity
import com.shiye.journal.data.local.entity.DiaryEntryEntity
import com.shiye.journal.data.local.entity.DiaryFieldEntity
import com.shiye.journal.data.local.entity.InspirationEntity
import com.shiye.journal.data.local.entity.InspirationPageEntity
import com.shiye.journal.data.local.entity.LoanEntity
import com.shiye.journal.data.local.entity.TemplateEntity
import com.shiye.journal.data.local.entity.TemplateFieldEntity
import com.shiye.journal.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份容器的编解码与校验规则。
 *
 * 备份是数据安全的最后防线，这里把所有"坏了就出大事"的路径都按住：
 * 往返一致、损坏拒绝、版本拒绝、缺文件拒绝。
 */
class BackupCodecTest {

    private fun payload(
        formatVersion: Int = BackupCodec.FORMAT_VERSION,
        dbVersion: Int = BackupCodec.DB_VERSION
    ) = BackupCodec.Payload(
        formatVersion = formatVersion,
        dbVersion = dbVersion,
        exportedAt = 1_760_000_000_000,
        diaryEntries = listOf(
            DiaryEntryEntity(
                id = "d1", date = "2026-10-15", templateId = "t1",
                templateName = "日常", templateVersion = 1,
                status = "complete", createdAt = 1L, updatedAt = 2L
            )
        ),
        diaryFields = listOf(
            DiaryFieldEntity(
                id = "f1", entryId = "d1", fieldKey = "body",
                label = "正文", type = "multi", required = true,
                sortOrder = 1, content = "今天写了一点东西"
            )
        ),
        templates = listOf(
            TemplateEntity(
                id = "t1", name = "日常", version = 1,
                isDefault = true, sortOrder = 0, createdAt = 1L, updatedAt = 1L
            )
        ),
        templateFields = listOf(
            TemplateFieldEntity(
                id = "tf1", templateId = "t1", fieldKey = "body",
                label = "正文", type = "multi", required = true, sortOrder = 1
            )
        ),
        transactions = listOf(
            TransactionEntity(
                id = "x1", type = "expense", amount = 2000,
                categoryId = "c1", categoryName = "餐饮", accountId = "a1", accountName = "现金",
                note = "午饭", date = "2026-10-15", time = "12:00",
                dateTime = 1_760_000_000_000, createdAt = 1L, updatedAt = 2L
            )
        ),
        loans = listOf(
            LoanEntity(
                id = "l1", direction = "borrow", person = "老王", amount = 50000,
                note = "", date = "2026-10-15", dateTime = 1_760_000_000_000,
                dueDate = null, status = "pending", settledAt = null,
                createdAt = 1L, updatedAt = 1L
            )
        ),
        categories = listOf(
            CategoryEntity(
                id = "c1", name = "餐饮", type = "expense", icon = "☕",
                sortOrder = 0, active = true, isPreset = true, createdAt = 1L
            )
        ),
        accounts = emptyList(),
        inspirations = listOf(
            InspirationEntity(id = "i1", content = "一个点子", createdAt = 1L, updatedAt = 1L)
        )
    )

    // ---------- 往返 ----------

    @Test
    fun pack_unpack_往返后数据一致() {
        val original = payload()
        val restored = BackupCodec.unpack(BackupCodec.pack(original))
        assertEquals(original, restored)
    }

    @Test
    fun json_往返_特殊字符不丢() {
        val original = payload().copy(
            diaryFields = listOf(
                DiaryFieldEntity(
                    id = "f1", entryId = "d1", fieldKey = "body",
                    label = "正文", type = "multi", required = true,
                    sortOrder = 1, content = "引号\" 反斜杠\\ 换行\n制表\t emoji ✅"
                )
            )
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals("引号\" 反斜杠\\ 换行\n制表\t emoji ✅", decoded.diaryFields.first().content)
    }

    // ---------- 损坏拒绝 ----------

    @Test
    fun unpack_内容被篡改时报校验和不匹配() {
        // 构造"数据与校验和对不上"的备份：数据是真 JSON，checksum 却是别的
        val data = BackupCodec.encode(payload()).toByteArray(Charsets.UTF_8)
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zos.write(data)
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("checksum.txt"))
            zos.write("0000DEADBEEF".toByteArray(Charsets.US_ASCII))
            zos.closeEntry()
        }
        try {
            BackupCodec.unpack(bos.toByteArray())
            throw AssertionError("应该拒绝校验和不匹配的备份")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("损坏"))
        }
    }

    @Test
    fun unpack_根本不是zip时安全拒绝() {
        try {
            BackupCodec.unpack("这不是备份文件".toByteArray())
            throw AssertionError("应该拒绝非 ZIP 内容")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("备份文件"))
        }
    }

    @Test
    fun unpack_缺校验文件时拒绝() {
        // 手工构造只有 backup.json 的 ZIP：没有 checksum.txt 就无法确认完整性
        val data = BackupCodec.encode(payload()).toByteArray(Charsets.UTF_8)
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zos.write(data)
            zos.closeEntry()
        }
        try {
            BackupCodec.unpack(bos.toByteArray())
            throw AssertionError("应该拒绝缺少校验信息的备份")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("校验"))
        }
    }

    // ---------- 版本 ----------

    @Test
    fun validate_未来格式版本拒绝() {
        val result = BackupCodec.validate(payload(formatVersion = BackupCodec.FORMAT_VERSION + 1))
        assertTrue(result is BackupCodec.Check.Rejected)
        assertTrue((result as BackupCodec.Check.Rejected).reason.contains("升级"))
    }

    @Test
    fun validate_未来数据库版本拒绝() {
        val result = BackupCodec.validate(payload(dbVersion = BackupCodec.DB_VERSION + 1))
        assertTrue(result is BackupCodec.Check.Rejected)
    }

    @Test
    fun validate_当前及更旧版本接受() {
        assertTrue(BackupCodec.validate(payload()) is BackupCodec.Check.Ok)
        // 旧版本备份允许恢复，之后版本再做迁移（规格 §7.4）
        assertTrue(BackupCodec.validate(payload(dbVersion = 1)) is BackupCodec.Check.Ok)
    }

    // ---------- 灵感页（数据库 v3 新增的那块） ----------

    @Test
    fun decode_没有灵感页键的旧备份按空列表处理() {
        // v3 之前备份出来的 JSON 里根本没有 inspirationPages 这个键。
        // 反序列化必须按默认值接住，否则老备份会整体解析失败 —— 那是数据安全事故。
        // 键名是**属性名**（camelCase）而不是数据库列名：序列化走的是 Kotlin 属性，
        // @ColumnInfo 只管 Room。照列名写出来的 JSON 一个字段都对不上
        val oldJson = """
            {"formatVersion":1,"dbVersion":2,"exportedAt":1760000000000,
             "diaryEntries":[],"diaryFields":[],"templates":[],"templateFields":[],
             "transactions":[],"loans":[],"categories":[],"accounts":[],
             "inspirations":[{"id":"i1","content":"老灵感","createdAt":1,"updatedAt":1}]}
        """.trimIndent()
        val decoded = BackupCodec.decode(oldJson)
        assertEquals(0, decoded.inspirationPages.size)
        // 老灵感没有 page_id，回落到默认页，恢复时由 BackupManager 补一个默认页接住
        assertEquals(InspirationPageEntity.DEFAULT_ID, decoded.inspirations.first().pageId)
    }

    @Test
    fun pack_unpack_灵感页与归属一起往返() {
        val original = payload().copy(
            inspirationPages = listOf(
                InspirationPageEntity(id = "p1", name = "小说", sortOrder = 0, createdAt = 5L)
            ),
            inspirations = listOf(
                InspirationEntity(
                    id = "i1", content = "一个点子", createdAt = 1L, updatedAt = 1L, pageId = "p1"
                )
            )
        )
        val restored = BackupCodec.unpack(BackupCodec.pack(original))
        assertEquals("小说", restored.inspirationPages.first().name)
        assertEquals("p1", restored.inspirations.first().pageId)
    }

    // ---------- 校验和 ----------

    @Test
    fun sha256_稳定且是64位大写十六进制() {
        val hex = BackupCodec.sha256Hex("hello".toByteArray())
        assertEquals(64, hex.length)
        assertEquals(hex, BackupCodec.sha256Hex("hello".toByteArray()))
        assertTrue(hex.all { it in "0123456789ABCDEF" })
    }
}
