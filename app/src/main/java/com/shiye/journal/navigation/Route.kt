package com.shiye.journal.navigation

import kotlinx.serialization.Serializable

/**
 * 路由定义。
 *
 * 用 sealed interface + @Serializable，是为了让**整个返回栈能序列化成一段 JSON**：
 * 旋转屏幕、切换深色模式、甚至系统杀掉进程后再回来，都能原样恢复到刚才那一页。
 * 这是自写导航相对"把页面状态塞进 ViewModel"最大的好处。
 *
 * 加新页面的做法：在这里加一个 @Serializable 的 data class / data object，
 * 然后到 [com.shiye.journal.ui.ShiyeApp] 的路由分发处补一个分支即可。
 *
 * 注意：**密封基类自身也必须标注 @Serializable**，否则子类不会被自动注册到
 * 多态序列化作用域里，序列化时会报
 * "Serializer for subclass 'Xxx' is not found in the polymorphic scope of 'Route'"。
 * 这个坑只在真正调用序列化时暴露（旋转屏幕、进程重建），编译期看不出来。
 */
@Serializable
sealed interface Route {

    /**
     * 用于"连点防重"的身份标识。
     *
     * 同一个页面连续被点两下（FAB、列表项）会往返回栈里压两个一样的页面，
     * 用户得按两次返回才能回去。Navigator.push 会比较栈顶与待压入路由的这个值，
     * 相同就直接忽略。
     *
     * **注意不能拿整个 Route 直接比**：编辑页带着每次新建都不同的 [Route.RecordEditor.editorKey]，
     * 直接比等于关掉了防重。
     */
    val dedupeKey: String
        // 默认用 toString()：data object 的 toString 就是它的名字，天然唯一又不需要反射。
        // 带参数的编辑页必须自己重写（见 RecordEditor / TemplateEditor / DiaryEditor）。
        get() = toString()

    /** 日记列表（日记 Tab 的根页面） */
    @Serializable
    data object DiaryHome : Route

    /**
     * 日记编辑页。
     *
     * 只带日期不带 id：一天只有一篇日记，日期就是天然主键，
     * 从列表点进来和从"今天"按钮进来走的是同一条路，不存在 id 对不上的情况。
     */
    @Serializable
    data class DiaryEditor(val date: String) : Route {

        override val dedupeKey: String get() = "DiaryEditor:$date"
    }

    /** 日记搜索页 */
    @Serializable
    data object DiarySearch : Route

    /** 模板管理列表 */
    @Serializable
    data object TemplateList : Route

    /**
     * 模板编辑页。
     *
     * @param templateId null 表示新建模板
     * @param editorKey 每次进页面都不同的标识，供 ViewModel 取 key 用。
     *   没有它的话"新建"永远是同一个 key，第二次新建会拿到上一次那个 ViewModel，
     *   里面还留着上一份输入 —— 新建第二个模板时会看到第一个模板的字段。
     */
    @Serializable
    data class TemplateEditor(
        val templateId: String? = null,
        val editorKey: String = java.util.UUID.randomUUID().toString()
    ) : Route {

        override val dedupeKey: String get() = "TemplateEditor:$templateId"
    }

    /** 记账首页（记账 Tab 的根页面） */
    @Serializable
    data object LedgerHome : Route

    /**
     * 记一笔 / 编辑一条记录。
     *
     * 一张表单同时覆盖流水与借款提醒（规格 §5.2 的分组选择），
     * 所以这里只有"记录 id"这一种参数：id 指向流水就进普通收支模式，
     * 指向借款就进借款模式，**由加载时的实际记录决定**，不额外维护类型参数。
     *
     * @param recordId null 表示新建
     * @param kind 新建时的初始类型；编辑已有记录时以记录本身为准
     * @param initialDate 新建时的初始日期（yyyy-MM-dd），用于「补账单」；
     *   null 表示今天。编辑已有记录时以记录本身为准。
     *   带默认值的另一个用处是**兼容旧返回栈**：升级前序列化到磁盘的路由里没有这个字段，
     *   反序列化时会用默认值而不是直接失败。
     * @param editorKey 每次进页面都不同的标识，供 ViewModel 取 key 用（理由同 TemplateEditor）。
     *   **这一条修过一个真 bug**：新建时 recordId 恒为 null，ViewModel 的 key 就恒为
     *   "record-editor-null"，第二次点「记一笔」会拿到上一次那个 ViewModel ——
     *   它里面 `saved` 还是 true，页面一出现就被 LaunchedEffect 判定"已保存"而立刻返回，
     *   看上去就是"点了新建，界面闪一下就关了，一天只能记一笔"。
     */
    @Serializable
    data class RecordEditor(
        val recordId: String? = null,
        val kind: String = "expense",
        val initialDate: String? = null,
        val editorKey: String = java.util.UUID.randomUUID().toString()
    ) : Route {

        override val dedupeKey: String get() = "RecordEditor:$recordId:$kind:$initialDate"
    }

    /**
     * 记账搜索页。
     *
     * 批量删除也收在这里：首页是"看"，搜索页是"找出来再处理"，
     * 要删一批的时候本来就是先搜出来。一个条件都不填 = 列出全部流水。
     */
    @Serializable
    data object LedgerSearch : Route

    /** 统计与结算（日 / 周 / 月 / 年） */
    @Serializable
    data object LedgerStats : Route

    /** 分类管理 */
    @Serializable
    data object CategoryManage : Route

    /** 账户管理 */
    @Serializable
    data object AccountManage : Route

    /** 借入借出提醒 */
    @Serializable
    data object LoanHome : Route

    /** 灵感列表（灵感 Tab 的根页面） */
    @Serializable
    data object InspirationHome : Route

    /** 灵感搜索页 */
    @Serializable
    data object InspirationSearch : Route

    /** 设置（设置 Tab 的根页面） */
    @Serializable
    data object SettingsHome : Route

    /**
     * AI 辅助配置（V2，规格 §8）。
     *
     * 从设置页进入。这个页面**本身不发任何请求** ——
     * 只有用户点了「测试连接」才发一次，符合 §8.2 的"任何请求都必须由用户明确操作触发"。
     */
    @Serializable
    data object AiSettings : Route

    /** AI 栏目：查看已保存的 AI 结果，并生成最近七天的建议（规格 §8.5 / §8.6） */
    @Serializable
    data object AiNotes : Route
}
