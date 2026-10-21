package com.shiye.journal.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 自写导航状态机。
 *
 * 为什么不用 Navigation Compose：
 * - 它需要把参数拼进字符串路由，类型安全靠额外插件兜，参数一多就容易写错；
 * - 本项目只有四个 Tab 加若干子页面，规模用不上那一整套。
 *
 * 设计要点：
 * 1. **每个 Tab 一条独立返回栈**。在日记里翻到编辑页，切到记账再切回来，编辑页还在 ——
 *    这才是底部导航该有的行为；全局单栈会让人在切 Tab 后"回不去刚才的位置"。
 * 2. **整个导航状态可序列化**，旋转屏幕与进程重建都不丢页面（见 [Saver]）。
 * 3. 返回键只弹当前 Tab 的栈；栈里只剩根页面时返回键交给系统（退出应用）。
 */
class Navigator private constructor(
    initialTab: Tab,
    initialStacks: Map<Tab, List<Route>>
) {

    /** 底部四个 Tab */
    enum class Tab(val storageKey: String) {
        DIARY("diary"),
        LEDGER("ledger"),
        INSPIRATION("inspiration"),
        SETTINGS("settings");

        companion object {
            fun fromStorageKey(key: String?): Tab =
                entries.firstOrNull { it.storageKey == key } ?: DIARY
        }
    }

    /** 当前选中的 Tab */
    var currentTab: Tab by mutableStateOf(initialTab)
        private set

    /**
     * 每个 Tab 一条返回栈。栈底恒为该 Tab 的根路由，栈里至少有一个元素，
     * 因此 [currentRoute] 永远取得到值，不需要判空。
     */
    internal val stacks: Map<Tab, SnapshotStateList<Route>> = Tab.entries.associateWith { tab ->
        val initial = initialStacks[tab]
        mutableStateListOf<Route>().apply {
            if (initial.isNullOrEmpty()) {
                add(tab.rootRoute())
            } else {
                addAll(initial)
            }
        }
    }

    /** 当前显示的路由 */
    val currentRoute: Route get() = stacks.getValue(currentTab).last()

    /** 当前 Tab 是否还能往回退（栈里不止根页面） */
    val canGoBack: Boolean get() = stacks.getValue(currentTab).size > 1

    /** 切换 Tab。各 Tab 的返回栈互不影响，切回来时子页面还在。 */
    fun selectTab(tab: Tab) {
        currentTab = tab
    }

    /**
     * 在当前 Tab 的栈顶压入新页面。
     *
     * **重复压栈会被忽略**：手指连点两下「记一笔」，或者在列表上快速点两下，
     * 会往栈里压进两个一样的页面，用户要按两次返回才回得去。
     * 这里比较栈顶与待压入路由的 [Route.dedupeKey]，相同就当作重复点击丢弃。
     *
     * 为什么不是"比较整个 Route"：编辑页每次新建都带一个全新的 editorKey，
     * 整体比较永远不相等，等于把这层保护关掉了。
     *
     * @return 是否真的压进去了
     */
    fun push(route: Route): Boolean {
        val stack = stacks.getValue(currentTab)
        if (stack.lastOrNull()?.dedupeKey == route.dedupeKey) return false
        stack.add(route)
        return true
    }

    /**
     * 弹出当前 Tab 的栈顶。
     * @return 是否真的弹出了；返回 false 表示已经在根页面，调用方应把返回键交给系统。
     */
    fun pop(): Boolean {
        val stack = stacks.getValue(currentTab)
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** 一路退回当前 Tab 的根页面，用于"完成""取消"这类操作 */
    fun popToTabRoot() {
        val stack = stacks.getValue(currentTab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** 用新页面替换栈顶，用于"编辑完直接跳到详情"这类不希望留下中间页的场景 */
    fun replace(route: Route) {
        val stack = stacks.getValue(currentTab)
        if (stack.isEmpty()) {
            stack.add(route)
        } else {
            stack[stack.lastIndex] = route
        }
    }

    // ---------- 序列化（进程重建恢复用） ----------

    /** 序列化的中间结构。栈里存的是每个 Route 序列化后的 JSON 字符串。 */
    @Serializable
    private data class NavState(
        val tab: String,
        val stacks: Map<String, List<String>>
    )

    /**
     * 序列化当前导航状态。
     *
     * 生产代码走 [Saver]（由 rememberSaveable 调用），
     * 这个方法主要给单元测试验证"序列化 → 反序列化"往返一致，
     * 避免旋转屏幕后页面跳回首页这种问题只有真机转一下才发现。
     */
    internal fun serialize(): String {
        val state = NavState(
            tab = currentTab.storageKey,
            stacks = stacks.mapKeys { it.key.storageKey }
                .mapValues { (_, list) -> list.map { encodeRoute(it) } }
        )
        return Json.encodeToString(state)
    }

    companion object {

        private fun Tab.rootRoute(): Route = when (this) {
            Tab.DIARY -> Route.DiaryHome
            Tab.LEDGER -> Route.LedgerHome
            Tab.INSPIRATION -> Route.InspirationHome
            Tab.SETTINGS -> Route.SettingsHome
        }

        private fun encodeRoute(route: Route): String = Json.encodeToString<Route>(route)

        private fun decodeRoute(text: String): Route? =
            runCatching { Json.decodeFromString<Route>(text) }.getOrNull()

        /** 全新启动时的导航器 */
        operator fun invoke(): Navigator = Navigator(Tab.DIARY, emptyMap())

        /**
         * 从序列化字符串恢复。
         *
         * 解析失败（比如旧版本遗留的结构）一律退回全新状态，
         * 而不是让应用崩在启动阶段 —— 恢复不到原页面只是体验问题，崩溃是可用性问题。
         */
        fun deserialize(text: String?): Navigator {
            if (text.isNullOrBlank()) return invoke()
            val state = runCatching { Json.decodeFromString<NavState>(text) }.getOrNull() ?: return invoke()
            val tab = Tab.fromStorageKey(state.tab)
            val restored = Tab.entries.associateWith { t ->
                val routes = state.stacks[t.storageKey].orEmpty()
                    .mapNotNull { decodeRoute(it) }
                if (routes.isEmpty()) listOf(t.rootRoute()) else routes
            }
            return Navigator(tab, restored)
        }

        /**
         * 供 rememberSaveable 使用的存档器。
         *
         * 只往 Bundle 里放一个字符串：Bundle 有大小上限（约 1MB），
         * 整个返回栈塞进去本来也不安全，压成一段 JSON 后几百字节，稳。
         */
        val Saver = Saver<Navigator, ArrayList<String>>(
            save = { ArrayList(listOf(it.serialize())) },
            restore = { deserialize(it.firstOrNull()) }
        )
    }
}

/** 记住一个跨旋转与进程重建的导航器 */
@Composable
fun rememberNavigator(): Navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() }
