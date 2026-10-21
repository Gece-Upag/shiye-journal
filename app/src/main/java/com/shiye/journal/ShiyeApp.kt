package com.shiye.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiye.journal.domain.RecordKind
import com.shiye.journal.navigation.Navigator
import com.shiye.journal.navigation.Route
import com.shiye.journal.navigation.rememberNavigator
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.IconActionButton
import com.shiye.journal.ui.components.NameInputDialog
import com.shiye.journal.ui.components.ShiyeBottomBar
import com.shiye.journal.ui.components.ShiyeDropdownMenu
import com.shiye.journal.ui.components.ShiyeMenuItem
import com.shiye.journal.ui.components.ShiyeTopBar
import com.shiye.journal.ui.components.TopBarTextButton
import com.shiye.journal.ui.screen.AccountManageScreen
import com.shiye.journal.ui.screen.AiNotesScreen
import com.shiye.journal.ui.screen.AiSettingsScreen
import com.shiye.journal.ui.screen.CategoryManageScreen
import com.shiye.journal.ui.screen.DiaryEditorScreen
import com.shiye.journal.ui.screen.DiaryScreen
import com.shiye.journal.ui.screen.DiarySearchScreen
import com.shiye.journal.ui.screen.InspirationScreen
import com.shiye.journal.ui.screen.InspirationSearchScreen
import com.shiye.journal.ui.screen.LedgerScreen
import com.shiye.journal.ui.screen.LedgerSearchScreen
import com.shiye.journal.ui.screen.LedgerStatsScreen
import com.shiye.journal.ui.screen.LoanScreen
import com.shiye.journal.ui.screen.RecordEditorScreen
import com.shiye.journal.ui.screen.SettingsScreen
import com.shiye.journal.ui.screen.TemplateEditorScreen
import com.shiye.journal.ui.screen.TemplateListScreen
import com.shiye.journal.ui.theme.ShiyeTheme
import com.shiye.journal.ui.viewmodel.DiaryEditorViewModel
import com.shiye.journal.ui.viewmodel.DiaryViewModel
import com.shiye.journal.ui.viewmodel.InspirationViewModel
import com.shiye.journal.ui.viewmodel.LedgerViewModel
import com.shiye.journal.ui.viewmodel.RecordEditorViewModel
import com.shiye.journal.ui.viewmodel.SettingsViewModel
import com.shiye.journal.ui.viewmodel.TemplateEditorViewModel
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.launch

/**
 * 应用根 Composable。
 *
 * 结构：主题 → 导航宿主（顶部栏 + 内容 + 底部栏）。
 *
 * 两个需要说明的决定：
 * 1. **带参数的 ViewModel 在这层创建并按 key 复用**（[viewModel] 的 key 参数）。
 *    编辑页按日期、模板编辑页按模板 id 各拿一个实例，旋转屏幕时实例不重建，
 *    正在编辑的内容不会丢。
 * 2. **日记编辑页自带头部**（返回 + 日期 + 保存状态 + 更多菜单），
 *    所以这一层对编辑路由不放品牌栏，避免顶上堆两行。
 *
 * 返回键：当前 Tab 的栈里还有子页面时先弹栈，已经在根页面了就交还系统。
 */
@Composable
fun ShiyeApp(container: AppContainer) {
    // 四个 Tab 的 ViewModel 在 Activity 级别创建，旋转屏幕不会丢失
    val diaryViewModel: DiaryViewModel = viewModel(factory = container.diaryViewModelFactory)
    val ledgerViewModel: LedgerViewModel = viewModel(factory = container.ledgerViewModelFactory)
    val inspirationViewModel: InspirationViewModel = viewModel(factory = container.inspirationViewModelFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory)

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

    // AI 状态摘要。配置存在本机偏好里，读它不产生任何网络行为，
    // 所以放在壳里直接收集比塞进 SettingsViewModel 更省一层。
    //
    // 初值必须包在 remember 里：collectAsStateWithLifecycle 的 initialValue 只是个
    // 普通实参，不包的话每次重组都会重新求值一次 —— 那是一次偏好读取 + JSON 反序列化，
    // 而这段摘要每帧都被重组到（顶栏/设置页都要用），白烧 CPU。
    val aiConfig by container.aiConfigStore.activeConfig.collectAsStateWithLifecycle(
        initialValue = remember { container.aiConfigStore.currentActiveConfig() }
    )
    // 关着就只说"已关闭"；开着要把在用哪一套说出来，
    // 不然保存了好几套以后用户不知道当前走的是哪个
    val aiSummary = if (aiConfig.mode == com.shiye.journal.features.ai.AiMode.OFF) {
        "已关闭"
    } else {
        "在用「${aiConfig.displayName()}」· ${aiConfig.model}"
    }

    ShiyeTheme(themeMode = themeMode) {
        val navigator = rememberNavigator()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // 返回键：先退当前 Tab 的子页面，退不动了才交给系统
        BackHandler(enabled = navigator.canGoBack) {
            navigator.pop()
        }

        val route = navigator.currentRoute

        // 只在对应路由下创建，避免一进 App 就把所有子页面的 ViewModel 都建起来
        val editorViewModel: DiaryEditorViewModel? = (route as? Route.DiaryEditor)?.let {
            viewModel(
                key = "diary-editor-${it.date}",
                factory = container.diaryEditorViewModelFactory(it.date)
            )
        }
        // 两个编辑页的 ViewModel key 用路由自带的 editorKey，而不是业务 id：
        // "新建"时业务 id 恒为 null，用 id 当 key 会让第二次新建拿到上一次那个 ViewModel，
        // 里面残留着上一份输入和"已保存"标记（表现：页面一闪就关、一天只能记一笔）。
        val templateEditorViewModel: TemplateEditorViewModel? = (route as? Route.TemplateEditor)?.let {
            viewModel(
                key = "template-editor-${it.editorKey}",
                factory = container.templateEditorViewModelFactory(it.templateId)
            )
        }
        val recordEditorViewModel: RecordEditorViewModel? = (route as? Route.RecordEditor)?.let {
            viewModel(
                key = "record-editor-${it.editorKey}",
                factory = container.recordEditorViewModelFactory(
                    recordId = it.recordId,
                    kind = RecordKind.fromStorageKey(it.kind),
                    initialDate = it.initialDate
                )
            )
        }

        val showMessage: (String) -> Unit = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }

        // 编辑页自带头部（含返回），这里不放品牌栏
        val showBrandBar = route !is Route.DiaryEditor && route !is Route.RecordEditor
        // 表单类页面隐藏底部导航：软键盘已经占了大半屏，再压一排 Tab 会挡住输入框
        val showBottomBar = route !is Route.DiaryEditor && route !is Route.RecordEditor

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showBrandBar) {
                    ShiyeTopBar(
                        subtitle = subtitleOf(route),
                        actions = {
                            TopBarActions(
                                route = route,
                                navigator = navigator,
                                inspirationViewModel = inspirationViewModel
                            )
                        }
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    ShiyeBottomBar(
                        currentTab = navigator.currentTab,
                        onSelectTab = { navigator.selectTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                when (route) {
                    Route.DiaryHome -> DiaryScreen(
                        viewModel = diaryViewModel,
                        onOpenEntry = { date -> navigator.push(Route.DiaryEditor(date)) },
                        onOpenToday = {
                            // 有则打开、没有则创建，都由编辑页的 getOrCreateByDate 处理
                            navigator.push(Route.DiaryEditor(DateTimeUtil.todayString()))
                        }
                    )

                    is Route.DiaryEditor -> editorViewModel?.let {
                        DiaryEditorScreen(
                            viewModel = it,
                            onBack = { navigator.pop() },
                            onShowMessage = showMessage
                        )
                    }

                    Route.DiarySearch -> DiarySearchScreen(
                        viewModel = viewModel(factory = container.diarySearchViewModelFactory),
                        onOpenEntry = { date -> navigator.push(Route.DiaryEditor(date)) }
                    )

                    Route.TemplateList -> TemplateListScreen(
                        viewModel = viewModel(factory = container.templateViewModelFactory),
                        onOpenEditor = { templateId -> navigator.push(Route.TemplateEditor(templateId)) },
                        onShowMessage = showMessage
                    )

                    is Route.TemplateEditor -> templateEditorViewModel?.let {
                        TemplateEditorScreen(
                            viewModel = it,
                            onSaved = { navigator.pop() },
                            onCancel = { navigator.pop() }
                        )
                    }

                    Route.LedgerHome -> LedgerScreen(
                        viewModel = ledgerViewModel,
                        onAddRecord = { navigator.push(Route.RecordEditor()) },
                        onBackfillRecord = { date ->
                            navigator.push(Route.RecordEditor(initialDate = date))
                        },
                        onOpenRecord = { id -> navigator.push(Route.RecordEditor(recordId = id)) }
                    )

                    is Route.RecordEditor -> recordEditorViewModel?.let {
                        RecordEditorScreen(
                            viewModel = it,
                            onBack = { navigator.pop() }
                        )
                    }

                    Route.LedgerSearch -> LedgerSearchScreen(
                        viewModel = viewModel(factory = container.ledgerSearchViewModelFactory),
                        onOpenRecord = { id -> navigator.push(Route.RecordEditor(recordId = id)) }
                    )

                    Route.LedgerStats -> LedgerStatsScreen(
                        viewModel = viewModel(factory = container.ledgerStatsViewModelFactory)
                    )

                    Route.CategoryManage -> CategoryManageScreen(
                        viewModel = viewModel(factory = container.categoryManageViewModelFactory),
                        onShowMessage = showMessage
                    )

                    Route.AccountManage -> AccountManageScreen(
                        viewModel = viewModel(factory = container.accountManageViewModelFactory),
                        onShowMessage = showMessage
                    )

                    Route.LoanHome -> LoanScreen(
                        viewModel = viewModel(factory = container.loanViewModelFactory),
                        onAddLoan = { navigator.push(Route.RecordEditor(kind = RecordKind.BORROW.storageKey)) },
                        onEditLoan = { id -> navigator.push(Route.RecordEditor(recordId = id)) },
                        onShowMessage = showMessage
                    )

                    Route.InspirationHome -> InspirationScreen(
                        viewModel = inspirationViewModel,
                        onOpenSearch = { navigator.push(Route.InspirationSearch) }
                    )

                    Route.InspirationSearch -> InspirationSearchScreen(
                        viewModel = viewModel(factory = container.inspirationSearchViewModelFactory)
                    )

                    Route.SettingsHome -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onOpenAi = { navigator.push(Route.AiSettings) },
                        onOpenAiNotes = { navigator.push(Route.AiNotes) },
                        aiSummary = aiSummary
                    )

                    Route.AiSettings -> AiSettingsScreen(
                        viewModel = viewModel(factory = container.aiSettingsViewModelFactory),
                        onBack = { navigator.pop() }
                    )

                    Route.AiNotes -> AiNotesScreen(
                        viewModel = viewModel(factory = container.aiNotesViewModelFactory),
                        onBack = { navigator.pop() }
                    )

                }
            }
        }
    }
}

private fun subtitleOf(route: Route): String = when (route) {
    Route.DiaryHome -> "记录今天"
    is Route.DiaryEditor -> "编辑"
    Route.DiarySearch -> "搜索"
    Route.TemplateList -> "模板"
    is Route.TemplateEditor -> "模板字段"
    Route.LedgerHome -> "收支清楚"
    is Route.RecordEditor -> "记一笔"
    Route.LedgerSearch -> "搜索"
    Route.LedgerStats -> "统计"
    Route.CategoryManage -> "分类"
    Route.AccountManage -> "账户"
    Route.LoanHome -> "借入借出"
    Route.InspirationHome -> "随手记下"
    Route.InspirationSearch -> "搜索"
    Route.SettingsHome -> "偏好与数据"
    Route.AiSettings -> "AI 辅助"
    Route.AiNotes -> "AI 栏目"
}

/**
 * 顶部栏右侧的页面级操作。
 *
 * 日记列表页放"搜索"和"模板管理"两个入口（原型也是这两个），
 * 记账页放"搜索 + 菜单"（从右往左数：菜单第一、搜索第二，与日记页同一个次序），
 * 灵感页放"搜索 + 换页"，子页面统一放一个返回箭头 ——
 * 与系统返回键行为一致，只是给个更直观的落点。
 *
 * **批量删除不在顶栏**：记账和灵感的批量删除都收进了各自的搜索页
 * （[BulkDeleteBar]，按钮在结果区头部左边）。删一批东西本来就是"先找出来再删"，
 * 而顶栏是"顺手就点得到"的位置，把破坏性动作挂在那儿不合适。
 */
@Composable
private fun RowScope.TopBarActions(
    route: Route,
    navigator: Navigator,
    inspirationViewModel: InspirationViewModel
) {
    // 换页面时自动收起：把 remember 的 key 设成 route，等价于"每次换页重置状态"
    var menuExpanded by remember(route) { mutableStateOf(false) }

    when (route) {
        Route.DiaryHome -> {
            IconActionButton(text = "⌕", onClick = { navigator.push(Route.DiarySearch) })
            IconActionButton(text = "▦", onClick = { navigator.push(Route.TemplateList) })
        }

        Route.LedgerHome -> {
            // 从右往左数：◫（菜单）第一，⌕（搜索）第二 —— 与日记页的两个图标同一个次序。
            // 批量删除不在这里了：它跟着"搜出来再处理"走到搜索页，
            // 破坏性入口挂在首页顶栏太容易顺手点到
            IconActionButton(text = "⌕", onClick = { navigator.push(Route.LedgerSearch) })
            IconActionButton(text = "◫", onClick = { menuExpanded = true })
            LedgerMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onOpenStats = { navigator.push(Route.LedgerStats) },
                onOpenCategories = { navigator.push(Route.CategoryManage) },
                onOpenAccounts = { navigator.push(Route.AccountManage) },
                onOpenLoans = { navigator.push(Route.LoanHome) }
            )
        }

        Route.DiarySearch,
        Route.TemplateList,
        is Route.TemplateEditor -> IconActionButton(text = "‹", onClick = { navigator.pop() })

        Route.LedgerSearch,
        Route.LedgerStats,
        Route.CategoryManage,
        Route.AccountManage,
        Route.LoanHome,
        Route.InspirationSearch -> IconActionButton(text = "‹", onClick = { navigator.pop() })

        Route.InspirationHome -> {
            IconActionButton(text = "⌕", onClick = { navigator.push(Route.InspirationSearch) })
            PageSwitchButton(viewModel = inspirationViewModel)
        }

        Route.AiSettings,
        Route.AiNotes -> IconActionButton(text = "‹", onClick = { navigator.pop() })

        Route.SettingsHome,
        is Route.DiaryEditor,
        is Route.RecordEditor -> Unit
    }
}

/**
 * 灵感页切换按钮：显示当前页名，点开是页列表 + 新建 / 重命名 / 删除。
 *
 * 位置放在灵感页右上角（与搜索、批量删除同一排），
 * 因为"我现在在哪一页"必须一直看得见 —— 换页后忘了自己在哪，
 * 记到别的页里就找不回来了。
 */
@Composable
private fun RowScope.PageSwitchButton(viewModel: InspirationViewModel) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var namingTarget by remember { mutableStateOf<PageNaming?>(null) }
    var pendingDelete by remember { mutableStateOf<com.shiye.journal.data.model.InspirationPage?>(null) }

    TopBarTextButton(
        text = "${currentPage?.name ?: "灵感页"} ▾",
        onClick = { expanded = true }
    )

    ShiyeDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        pages.forEach { page ->
            ShiyeMenuItem(
                text = if (page.id == currentPage?.id) "✓ ${page.name}" else "    ${page.name}",
                onClick = {
                    expanded = false
                    viewModel.selectPage(page.id)
                }
            )
        }
        ShiyeMenuItem(
            text = "＋ 新建灵感页",
            onClick = {
                expanded = false
                namingTarget = PageNaming(null, "")
            }
        )
        ShiyeMenuItem(
            text = "✎ 重命名当前页",
            onClick = {
                expanded = false
                currentPage?.let { namingTarget = PageNaming(it.id, it.name) }
            }
        )
        ShiyeMenuItem(
            text = "删除当前页",
            isDanger = true,
            onClick = {
                expanded = false
                pendingDelete = currentPage
            }
        )
    }

    namingTarget?.let { target ->
        NameInputDialog(
            title = if (target.id == null) "新建灵感页" else "重命名灵感页",
            initial = target.initial,
            confirmText = if (target.id == null) "创建" else "保存",
            label = "页名",
            onDismiss = { namingTarget = null },
            onConfirm = { name ->
                namingTarget = null
                if (target.id == null) viewModel.createPage(name) else viewModel.renamePage(target.id, name)
            }
        )
    }

    pendingDelete?.let { page ->
        ConfirmDialog(
            title = "删除「${page.name}」？",
            message = "这一页里的全部灵感会一起被删除，无法恢复。",
            confirmText = "删除整页",
            onConfirm = {
                viewModel.deletePage(page.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** 新建/重命名弹窗复用的参数：id 为 null 表示新建 */
private data class PageNaming(val id: String?, val initial: String)

/**
 * 记账首页的菜单入口：统计 / 分类管理 / 账户管理 / 借入借出（规格 §5.1）。
 *
 * 之所以做成菜单而不是四个 Tab：这四个入口是"设置类"操作，
 * 每天都用的只有「记一笔」，把它做成右下角按钮更顺手。
 */
@Composable
private fun LedgerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenLoans: () -> Unit
) {
    ShiyeDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ShiyeMenuItem(text = "统计", onClick = { onDismiss(); onOpenStats() })
        ShiyeMenuItem(text = "分类管理", onClick = { onDismiss(); onOpenCategories() })
        ShiyeMenuItem(text = "账户管理", onClick = { onDismiss(); onOpenAccounts() })
        ShiyeMenuItem(text = "借入借出", onClick = { onDismiss(); onOpenLoans() })
    }
}
