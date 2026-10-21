package com.shiye.journal.data.repository

import com.shiye.journal.data.SeedData
import com.shiye.journal.data.local.AppDatabase
import com.shiye.journal.data.local.dao.TemplateWithFields
import com.shiye.journal.data.local.entity.TemplateEntity
import com.shiye.journal.data.model.FieldType
import com.shiye.journal.data.model.Template
import com.shiye.journal.data.model.TemplateField
import com.shiye.journal.data.model.toDomainTemplate
import com.shiye.journal.data.model.toEntity
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 模板仓库。
 *
 * 模板是"结构"，日记是"内容"。改模板不影响已写日记（日记存的是快照），
 * 这一点由 [DiaryRepository] 的字段快照保证，本仓库只管模板自身。
 */
class TemplateRepository(private val database: AppDatabase) {

    private val templateDao = database.templateDao()

    fun observeAll(): Flow<List<Template>> =
        templateDao.observeAll().map { list -> list.map { it.toDomainTemplate() } }

    suspend fun getById(id: String): Template? = templateDao.getById(id)?.toDomainTemplate()

    /** 默认模板；一个都没设过时返回第一个，保证新建日记永远有模板可用 */
    suspend fun getDefault(): Template? =
        (templateDao.getDefault() ?: templateDao.getFirst())?.toDomainTemplate()

    suspend fun countAll(): Int = templateDao.countAll()

    /**
     * 新建模板。
     *
     * 约束：至少要有一个多行字段 —— 日记正文没有多行输入框会很难用，
     * 这条限制放在仓库层，UI 直接读这个返回值提示用户。
     */
    suspend fun create(name: String, fields: List<TemplateField>): Result<String> {
        validate(name, fields, null)?.let { return Result.failure(IllegalArgumentException(it)) }

        val now = DateTimeUtil.nowMillis()
        val id = java.util.UUID.randomUUID().toString()
        val sortOrder = templateDao.countAll()

        templateDao.upsertTemplate(
            TemplateEntity(
                id = id,
                name = name.trim(),
                version = 1,
                isDefault = false,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now
            )
        )
        templateDao.upsertFields(fields.map { it.copy(templateId = id).toEntity() })

        return Result.success(id)
    }

    /**
     * 更新模板名与字段。
     *
     * [version] +1：结构变了，之后新建的日记会记下新版本；
     * 旧日记保留旧版本号与旧字段快照，不会跟着变。
     */
    suspend fun update(template: Template, newName: String, fields: List<TemplateField>): Result<Unit> {
        validate(newName, fields, template.id)?.let { return Result.failure(IllegalArgumentException(it)) }

        val now = DateTimeUtil.nowMillis()
        templateDao.upsertTemplate(
            TemplateEntity(
                id = template.id,
                name = newName.trim(),
                version = template.version + 1,
                isDefault = template.isDefault,
                sortOrder = template.sortOrder,
                createdAt = template.createdAt,
                updatedAt = now
            )
        )
        // 字段整体替换：先删光再写入，比逐条 diff 简单且不会留下脏数据
        templateDao.deleteFieldsByTemplate(template.id)
        templateDao.upsertFields(fields.map { it.copy(templateId = template.id).toEntity() })
        return Result.success(Unit)
    }

    /** 删除模板。已写日记不受影响（它们存的是快照，不是外键引用）。 */
    suspend fun delete(id: String) {
        val wasDefault = templateDao.getById(id)?.template?.isDefault == true
        templateDao.deleteById(id)
        // 删掉的是默认模板时，把第一个补上，避免出现"没有默认模板"的空档
        if (wasDefault) {
            templateDao.getFirst()?.template?.id?.let { setDefault(it) }
        }
    }

    /** 设为默认。先清空再设置，保证全库最多一个默认模板。 */
    suspend fun setDefault(id: String) {
        templateDao.clearDefaultFlag()
        templateDao.markAsDefault(id, DateTimeUtil.nowMillis())
    }

    /**
     * 模板合法性校验，返回给人看的错误文案；通过时返回 null。
     *
     * 规则来自规格 §4.5：名称非空、字段标签非空、至少一个字段、
     * **至少包含一个多行字段**。最后一条是最容易被漏掉的 ——
     * 全是单行输入框的模板写不了正文，等于把日记功能废掉。
     *
     * 重名校验也在这里：同名模板会让"切换模板"时无法分辨，属于可预防的困惑。
     *
     * @param selfId 编辑已有模板时传它自己的 id，避免把自己判成重名
     */
    suspend fun validate(name: String, fields: List<TemplateField>, selfId: String?): String? {
        if (name.isBlank()) return "请填写模板名称"
        if (fields.isEmpty()) return "至少需要一个字段"
        if (fields.any { it.label.isBlank() }) return "请填写所有字段标签"
        if (fields.none { it.type == FieldType.MULTI }) return "至少需要一个多行字段"

        val duplicated = templateDao.getAllOnce().any {
            it.template.id != selfId && it.template.name == name.trim()
        }
        if (duplicated) return "模板名称不能重复"
        return null
    }

    /**
     * 删除模板是否安全。
     *
     * 规格要求"删除模板绝不能级联删除已有日记"（靠字段快照已经做到），
     * 但删掉最后一个模板会让新建日记无从下手，所以这里挡一道。
     */
    fun canDelete(totalCount: Int): Boolean = totalCount > 1

    /**
     * 首次启动时写入预置模板。
     * 只在模板数为 0 时执行，重复启动不会重复插入。
     */
    suspend fun ensureSeeded() {
        if (SeedData.shouldSeed(templateDao.countAll())) {
            templateDao.upsertTemplate(SeedData.templates()[0])
            templateDao.upsertTemplate(SeedData.templates()[1])
            templateDao.upsertFields(SeedData.templateFields())
        }
    }
}
