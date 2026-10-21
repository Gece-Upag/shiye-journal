package com.shiye.journal.domain

import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.Transaction

/**
 * 一个分类在某段时间里的金额与占比（规格 §5.7）。
 *
 * @param ratio 0f~1f。**总额为 0 时一律 0f**，不做除法 —— 这是规格明确要求的除零保护。
 */
data class CategoryStat(
    val categoryId: String,
    val name: String,
    val icon: String,
    /** 单位：分 */
    val cents: Long,
    val ratio: Float
)

/** 找不到分类时的兜底名字。改名显示也能让老流水不至于顶着一个空白。 */
const val UNKNOWN_CATEGORY_NAME = "未分类"
const val UNKNOWN_CATEGORY_ICON = "◇"

/**
 * 按分类汇总。
 *
 * 三条规则值得说明：
 * 1. **归档分类照常显示**：归档只是"不能再选"，历史流水仍归入它，统计少一块反而更迷惑。
 * 2. **找不到分类的行折到「未分类」**：宁可显示一个明确的名字，也不要让行消失
 *    （账会看着少了钱）。
 * 3. **占比以本次列表的实际合计为分母**，不用外层传进来的总数：
 *    两者如果来自两次不同查询，一旦不一致，占比加起来就不是 100%，很难查。
 *
 * @param transactions 已经按方向（收入或支出）过滤好的流水
 * @param categories 全部分类（含归档），用于把 id 换成名字与图标
 */
fun buildCategoryStats(
    transactions: List<Transaction>,
    categories: List<Category>
): List<CategoryStat> {
    if (transactions.isEmpty()) return emptyList()

    val byId = categories.associateBy { it.id }

    // 同一分类可能有多笔，先累加；负数金额（理论上不会有）也照加不误，不做静默截断
    val totals = LinkedHashMap<String, Long>()
    transactions.forEach { transaction ->
        totals[transaction.categoryId] = (totals[transaction.categoryId] ?: 0L) + transaction.amount
    }

    val sum = totals.values.sum()
    return totals.map { (categoryId, cents) ->
        val category = byId[categoryId]
        CategoryStat(
            categoryId = categoryId,
            name = category?.name ?: UNKNOWN_CATEGORY_NAME,
            icon = category?.icon ?: UNKNOWN_CATEGORY_ICON,
            cents = cents,
            ratio = if (sum == 0L) 0f else cents.toFloat() / sum.toFloat()
        )
    }.sortedWith(compareByDescending<CategoryStat> { it.cents }.thenBy { it.name })
}
