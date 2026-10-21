package com.shiye.journal.data

import com.shiye.journal.data.local.entity.CategoryEntity
import com.shiye.journal.data.local.entity.DiaryEntryEntity
import com.shiye.journal.data.local.entity.DiaryFieldEntity
import com.shiye.journal.data.local.entity.TemplateEntity
import com.shiye.journal.data.local.entity.TemplateFieldEntity
import com.shiye.journal.data.local.entity.TransactionEntity

/**
 * 首次启动写入的预置数据。
 *
 * 原则：
 * - **只写"没有它就寸步难行"的东西**。日记要模板才能写，记账要分类才能记，
 *   所以这两样预置；账户因人而异，不预置，让用户自己建。
 * - 预置内容带固定 id（如 "c-food"），备份恢复时能对齐，不会因为重新生成 UUID 而对不上。
 * - 预置分类标记为 [isPreset]，只允许归档、不允许删除，避免用户误删后历史流水失去归属。
 * - 只在数据库为空时写入一次；已有数据时不重复插入（判断逻辑在 [SeedData.shouldSeed] 的调用方）。
 */
object SeedData {

    // ---------------- 模板 ----------------

    const val TEMPLATE_DAILY_ID = "tpl-daily"
    const val TEMPLATE_REVIEW_ID = "tpl-review"

    /** 「日常」模板：心情（单行）+ 正文（多行，必填）+ 备注（多行） */
    private val dailyTemplate = TemplateEntity(
        id = TEMPLATE_DAILY_ID,
        name = "日常",
        version = 1,
        isDefault = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private val dailyFields = listOf(
        TemplateFieldEntity(
            id = "tpl-daily-mood",
            templateId = TEMPLATE_DAILY_ID,
            fieldKey = "mood",
            label = "心情",
            type = DiaryFieldEntity.Type.SINGLE,
            required = false,
            sortOrder = 0
        ),
        TemplateFieldEntity(
            id = "tpl-daily-body",
            templateId = TEMPLATE_DAILY_ID,
            fieldKey = "body",
            label = "正文",
            type = DiaryFieldEntity.Type.MULTI,
            required = true,
            sortOrder = 1
        ),
        TemplateFieldEntity(
            id = "tpl-daily-note",
            templateId = TEMPLATE_DAILY_ID,
            fieldKey = "note",
            label = "备注",
            type = DiaryFieldEntity.Type.MULTI,
            required = false,
            sortOrder = 2
        )
    )

    /** 「每日复盘」模板：三问式，最后一问必填，保证复盘不会全空 */
    private val reviewTemplate = TemplateEntity(
        id = TEMPLATE_REVIEW_ID,
        name = "每日复盘",
        version = 1,
        isDefault = false,
        sortOrder = 1,
        createdAt = 0L,
        updatedAt = 0L
    )

    private val reviewFields = listOf(
        TemplateFieldEntity(
            id = "tpl-review-good",
            templateId = TEMPLATE_REVIEW_ID,
            fieldKey = "good",
            label = "今天做得好的事",
            type = DiaryFieldEntity.Type.MULTI,
            required = false,
            sortOrder = 0
        ),
        TemplateFieldEntity(
            id = "tpl-review-learn",
            templateId = TEMPLATE_REVIEW_ID,
            fieldKey = "learn",
            label = "今天学到什么",
            type = DiaryFieldEntity.Type.MULTI,
            required = false,
            sortOrder = 1
        ),
        TemplateFieldEntity(
            id = "tpl-review-next",
            templateId = TEMPLATE_REVIEW_ID,
            fieldKey = "next",
            label = "明天要做的事",
            type = DiaryFieldEntity.Type.MULTI,
            required = true,
            sortOrder = 2
        )
    )

    fun templates(): List<TemplateEntity> = listOf(dailyTemplate, reviewTemplate)

    fun templateFields(): List<TemplateFieldEntity> = dailyFields + reviewFields

    // ---------------- 分类 ----------------

    /**
     * 预置分类。
     *
     * 图标沿用 UI 原型的做法：一个字符，不用图片资源，
     * 好处是备份 JSON 里能直接看懂，也不占体积。
     */
    fun categories(now: Long): List<CategoryEntity> = listOf(
        // 支出（8 个，达到规格上限）
        CategoryEntity("c-food", "餐饮", TransactionEntity.Type.EXPENSE, "☕", 0, true, true, now),
        CategoryEntity("c-daily", "日用", TransactionEntity.Type.EXPENSE, "◍", 1, true, true, now),
        CategoryEntity("c-transport", "交通", TransactionEntity.Type.EXPENSE, "↗", 2, true, true, now),
        CategoryEntity("c-housing", "居住", TransactionEntity.Type.EXPENSE, "⌂", 3, true, true, now),
        CategoryEntity("c-fun", "娱乐", TransactionEntity.Type.EXPENSE, "✦", 4, true, true, now),
        CategoryEntity("c-medical", "医疗", TransactionEntity.Type.EXPENSE, "✚", 5, true, true, now),
        CategoryEntity("c-study", "学习", TransactionEntity.Type.EXPENSE, "✎", 6, true, true, now),
        CategoryEntity("c-other-expense", "其他", TransactionEntity.Type.EXPENSE, "◇", 7, true, true, now),

        // 收入（5 个）
        CategoryEntity("c-salary", "工资", TransactionEntity.Type.INCOME, "＋", 0, true, true, now),
        CategoryEntity("c-parttime", "兼职", TransactionEntity.Type.INCOME, "◔", 1, true, true, now),
        CategoryEntity("c-invest", "理财", TransactionEntity.Type.INCOME, "％", 2, true, true, now),
        CategoryEntity("c-redpacket", "红包", TransactionEntity.Type.INCOME, "◉", 3, true, true, now),
        CategoryEntity("c-other-income", "其他", TransactionEntity.Type.INCOME, "◇", 4, true, true, now)
    )

    /**
     * 是否要写入预置数据。
     *
     * 用"模板数为 0"来判断而不是 SharedPreferences 里的 flag：
     * flag 可能在清数据后残留，也可能被备份恢复覆盖，不如直接看库里有没有东西可靠。
     */
    suspend fun shouldSeed(templateCount: Int): Boolean = templateCount == 0

    /**
     * 用模板生成一篇空日记的字段快照。
     * 放在这里是因为"模板 → 日记字段"的复制规则全局只应有这一份实现。
     */
    fun buildEmptyFields(
        entryId: String,
        template: TemplateEntity,
        fields: List<TemplateFieldEntity>
    ): List<DiaryFieldEntity> = fields
        .sortedBy { it.sortOrder }
        .map { templateField ->
            DiaryFieldEntity(
                id = java.util.UUID.randomUUID().toString(),
                entryId = entryId,
                fieldKey = templateField.fieldKey,
                label = templateField.label,
                type = templateField.type,
                required = templateField.required,
                sortOrder = templateField.sortOrder,
                content = ""
            )
        }.also {
            // template 参数用于将来带上版本信息时的扩展，当前字段快照不存版本
            check(template.id.isNotBlank()) { "模板 id 不能为空" }
        }

    /** 新建日记实体的默认状态：草稿。写满必填项后由 Repository 提升为 COMPLETE。 */
    fun newEntryStatus(): String = DiaryEntryEntity.Status.DRAFT
}
