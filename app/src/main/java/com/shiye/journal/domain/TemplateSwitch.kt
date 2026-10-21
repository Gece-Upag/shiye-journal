package com.shiye.journal.domain

import com.shiye.journal.data.model.DiaryField
import com.shiye.journal.data.model.FieldType
import com.shiye.journal.data.model.TemplateField

/**
 * 切换模板时的字段合并结果。
 *
 * @property fields 合并后的字段列表，可直接整体写回 diary_fields。
 * @property retainedCount 无法对应到新模板、被原样保留下来的旧字段数量。
 *           大于 0 时 UI 必须明确告诉用户，不能静默处理。
 */
data class SwitchPlan(
    val fields: List<DiaryField>,
    val retainedCount: Int
)

/**
 * 切换模板的合并规则（纯函数，便于单测）。
 *
 * 规格 §4.3：切换模板时必须避免静默丢失已有内容；匹配的字段内容应尽可能保留；
 * 无法对应的旧字段内容必须保留或在切换前明确提示，不能直接删除。
 *
 * 这里选的是**保留**（比"提示后丢弃"更安全）：无法对应的旧字段连内容一起留在日记里，
 * 排在字段列表末尾。日记存的是字段快照，本来就不要求字段集合与当前模板一致，
 * 所以这样做不需要改表结构。
 *
 * 匹配顺序：先按 [DiaryField.fieldKey]（模板里定的稳定标识，如 "body"），
 * 再退化到按 [DiaryField.label]（同"正文"改了 key 的情况）。这跟 UI 原型的做法一致。
 *
 * @param current 日记当前的字段快照（带用户已填内容）
 * @param target 新模板的字段定义（按 sortOrder 排好）
 * @param entryId 目标日记 id
 * @param idFactory 生成新字段 id 的函数；不在这里直接 new UUID 是为了让测试可控
 */
fun planTemplateSwitch(
    current: List<DiaryField>,
    target: List<TemplateField>,
    entryId: String,
    idFactory: () -> String = { java.util.UUID.randomUUID().toString() }
): SwitchPlan {
    val matchedOldIds = HashSet<String>()

    val next = target
        .sortedBy { it.sortOrder }
        .mapIndexed { index, templateField ->
            val old = current.firstOrNull { it.fieldKey == templateField.fieldKey }
                ?: current.firstOrNull { it.label == templateField.label }
            if (old != null) matchedOldIds.add(old.id)
            DiaryField(
                id = idFactory(),
                entryId = entryId,
                fieldKey = templateField.fieldKey,
                label = templateField.label,
                type = templateField.type,
                required = templateField.required,
                sortOrder = index,
                content = old?.content.orEmpty()
            )
        }

    // 只有"写了东西"的旧字段才值得保留；空字段留着只会让编辑页多几个空输入框
    val retained = current
        .filter { it.id !in matchedOldIds && it.content.isNotBlank() }
        .sortedBy { it.sortOrder }
        .mapIndexed { index, old ->
            old.copy(
                id = idFactory(),
                entryId = entryId,
                sortOrder = next.size + index
            )
        }

    return SwitchPlan(fields = next + retained, retainedCount = retained.size)
}

/**
 * 判断字段是否属于当前模板。
 *
 * 日记字段快照里没有"是否遗留"这一列（schema v1 已定死，不再加列），
 * 所以用"字段 key 是否还在当前模板里"来推导，供 UI 标注"原模板字段"。
 *
 * @param templateFieldKeys 当前模板的字段 key 集合；传空集时一律返回 false
 *                          （模板已被删除，此时标"遗留"没有意义）
 */
fun isLegacyField(field: DiaryField, templateFieldKeys: Set<String>): Boolean =
    templateFieldKeys.isNotEmpty() && field.fieldKey !in templateFieldKeys

/** 新字段的默认定义。UI 里点"添加字段"时用，保证默认就给一个多行字段类型。 */
fun newTemplateField(
    templateId: String,
    label: String = "",
    type: FieldType = FieldType.MULTI,
    required: Boolean = false,
    sortOrder: Int = 0,
    idFactory: () -> String = { java.util.UUID.randomUUID().toString() }
): TemplateField = TemplateField(
    id = idFactory(),
    templateId = templateId,
    // key 用 id：字段标签可以随便改，key 一旦定下就不再变，
    // 这样改标签不会让"切换模板时按 key 匹配"失效
    fieldKey = idFactory(),
    label = label,
    type = type,
    required = required,
    sortOrder = sortOrder
)
