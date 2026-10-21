package com.shiye.journal.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.DiaryStatus
import com.shiye.journal.data.model.FieldType
import com.shiye.journal.data.model.Template
import com.shiye.journal.domain.DiaryMarkdown
import com.shiye.journal.features.ai.AiFieldOption
import com.shiye.journal.features.ai.AiStage
import com.shiye.journal.features.ai.AiTaskKind
import com.shiye.journal.domain.SaveState
import com.shiye.journal.ui.components.AiFieldPickerDialog
import com.shiye.journal.ui.components.AiNoticeBar
import com.shiye.journal.ui.components.AiSummaryCard
import com.shiye.journal.ui.components.AiSummaryPickerDialog

import com.shiye.journal.ui.components.AiTaskDialog
import com.shiye.journal.ui.components.FieldAiButton
import com.shiye.journal.ui.components.HairlineDivider
import com.shiye.journal.ui.components.CardRadius
import com.shiye.journal.ui.components.IconActionButton
import com.shiye.journal.ui.components.InnerRadius
import com.shiye.journal.ui.components.SaveStateChip
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeDropdownMenu
import com.shiye.journal.ui.components.ShiyeMenuItem
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.util.copyToClipboard
import com.shiye.journal.ui.viewmodel.DiaryEditorViewModel
import com.shiye.journal.util.DateTimeUtil
import com.shiye.journal.util.writeTextToUri
import kotlinx.coroutines.launch

/** 多行字段的最小高度（行数）。规格 §4.3 要求"至少约 5 行"。 */
private const val MULTI_LINE_MIN = 5

/**
 * 日记编辑页。
 *
 * 顶部一行放：返回、只读日期、保存状态、更多菜单（规格 §4.3）。
 * 保存状态**一定带文字**，不靠颜色单独表达。
 *
 * 离开页面时通过 [DisposableEffect] 触发一次立即保存：
 * 规格要求"页面离开操作等待最后一次有效保存完成"，
 * 而这里的保存跑在 viewModelScope 上，Composable 销毁后依然能跑完。
 */
@Composable
fun DiaryEditorScreen(
    viewModel: DiaryEditorViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val removed by viewModel.removed.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val aiTask by viewModel.aiTask.collectAsStateWithLifecycle()
    val aiNotice by viewModel.aiNotice.collectAsStateWithLifecycle()
    val aiCanUndo by viewModel.aiCanUndo.collectAsStateWithLifecycle()
    val aiSummaryEntries by viewModel.aiSummaryEntries.collectAsStateWithLifecycle()
    val keptSummaries by viewModel.keptSummaries.collectAsStateWithLifecycle()
    val keepCurrentSummary by viewModel.keepCurrentSummary.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var templatePickerOpen by remember { mutableStateOf(false) }

    /** 发给模型的目标（模型名 + 主机），"发送前告知"里要说清 */
    val aiTarget by viewModel.aiTarget.collectAsStateWithLifecycle()

    // 离开页面时把最后一次改动写出去
    DisposableEffect(Unit) {
        onDispose { viewModel.flushOnExit() }
    }

    // 每次进页面都对一次：上次退出后这篇被删掉了就按日期重新建一篇，
    // 否则残留的旧 id 会让自动保存失败、页面一进来就被弹回去
    LaunchedEffect(Unit) {
        viewModel.refreshIfMissing()
    }

    LaunchedEffect(message) {
        message?.let {
            onShowMessage(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(removed) {
        if (removed) {
            onShowMessage("这篇日记已被删除")
            viewModel.consumeRemoved()
            onBack()
        }
    }

    // 单篇导出用 SAF：让用户自己选保存位置，不申请全盘文件权限
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = viewModel.currentSnapshot()
        if (snapshot == null) {
            Toast.makeText(context, "日记还没加载完", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val text = DiaryMarkdown.render(snapshot)
        val fileName = DiaryMarkdown.fileName(snapshot)
        scope.launch {
            val ok = writeTextToUri(context, uri, text)
            // 用 Toast 而不是 snackbar：导出结果由系统文件选择器返回，
            // 此时页面可能已经不在前台，snackbar 有可能被吞掉
            Toast.makeText(
                context,
                if (ok) "已导出 $fileName" else "导出失败，请重试",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        EditorHeader(
            date = entry?.date.orEmpty(),
            templateName = entry?.templateName.orEmpty(),
            saveState = saveState,
            onBack = onBack,
            onOpenTemplatePicker = { templatePickerOpen = true },
            menuExpanded = menuExpanded,
            onMenuToggle = { menuExpanded = it },
            onMenuDismiss = { menuExpanded = false },
            onExport = {
                menuExpanded = false
                val snapshot = viewModel.currentSnapshot()
                if (snapshot == null) {
                    Toast.makeText(context, "日记还没加载完", Toast.LENGTH_SHORT).show()
                } else {
                    exportLauncher.launch(DiaryMarkdown.fileName(snapshot))
                }
            },
            onToggleComplete = {
                menuExpanded = false
                if (entry?.status == DiaryStatus.COMPLETE) viewModel.markDraft() else viewModel.markComplete()
            },
            isComplete = entry?.status == DiaryStatus.COMPLETE,
            aiEnabled = aiEnabled,
            onAiSummary = {
                menuExpanded = false
                viewModel.prepareSummary()
            }
        )

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

        when {
            entry == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "正在打开…",
                        style = ShiyeType.bodySecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // 键盘弹起时把可滚区压到键盘上方：不做这一步，Compose 认为
                        // 可视区还是整屏，正在编辑的那个字段虽然被键盘盖住了，
                        // 系统仍判定"在视野内"，页面不会往上顶
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    val current = entry!!
                    current.fields.forEach { field ->
                        if (viewModel.isLegacy(field)) {
                            LegacyFieldNotice()
                        }
                        ShiyeTextField(
                            value = contents[field.id] ?: field.content,
                            onValueChange = { viewModel.updateContent(field.id, it) },
                            label = field.label,
                            required = field.required,
                            singleLine = field.type == FieldType.SINGLE,
                            minLines = if (field.type == FieldType.MULTI) MULTI_LINE_MIN else 1,
                            // AI 关着的时候这个按钮根本不会出现：
                            // 不给一个"点了才发现不能用"的入口（规格 §8：AI 默认关闭）
                            action = if (aiEnabled) {
                                { FieldAiButton(text = "AI 润色", onClick = { viewModel.preparePolish(field.id) }) }
                            } else {
                                null
                            }
                        )
                    }

                    if (saveState == SaveState.FAILED) {
                        SaveFailedBanner(onRetry = viewModel::retrySave)
                    }

                    // AI 总结：AI 的话有自己的一栏，不混进正文（V2）
                    // 可能有多条（反复要过建议），从晚到早排，逐条可移除
                    if (aiSummaryEntries.isNotEmpty()) {
                        AiSummaryCard(
                            entries = aiSummaryEntries,
                            onClear = viewModel::clearAiSummary,
                            onRemove = viewModel::removeSummaryEntry
                        )
                    }

                    // AI 提示条：替换成功后给用户一个"撤销"的落点（规格 §8.3 支持回滚）
                    aiNotice?.let { notice ->
                        AiNoticeBar(
                            text = notice,
                            canUndo = aiCanUndo,
                            onUndo = viewModel::undoAiReplacement,
                            onDismiss = viewModel::dismissAiNotice
                        )
                    }

                    // 底部留白：键盘收起时最后一个输入框下面不至于贴着屏幕边
                    Box(modifier = Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }

    // 选字段弹窗的候选项（随正文变化：编辑中打开也要看到最新的内容）
    val fieldOptions = remember(entry, contents) {
        entry?.fields?.map { field ->
            AiFieldOption(
                id = field.id,
                label = field.label,
                content = contents[field.id] ?: field.content
            )
        } ?: emptyList()
    }

    // AI：选字段 / 确认发送 / 结果 / 失败 四个阶段的弹窗
    // 建议类拿到结果后走"取舍"对话框：把这次的和以前的摆一起让用户挑
    val showSummaryPicker =
        aiTask?.kind == AiTaskKind.SUMMARY && aiTask?.stage == AiStage.RESULT

    aiTask?.let { task ->
        if (task.stage == AiStage.PICK_FIELDS) {
            AiFieldPickerDialog(
                options = fieldOptions,
                picked = task.pickedFieldIds,
                onToggle = viewModel::togglePickedField,
                onToggleAll = viewModel::setAllPickedFields,
                onNext = viewModel::confirmPickedFields,
                onCancel = viewModel::cancelAi
            )
            return@let
        }
        if (showSummaryPicker) {
            AiSummaryPickerDialog(
                original = task.snapshot,
                current = task.result.orEmpty(),
                previous = aiSummaryEntries,
                keepCurrent = keepCurrentSummary,
                kept = keptSummaries,
                onToggleCurrent = viewModel::toggleKeepCurrentSummary,
                onToggleKept = viewModel::toggleKeptSummary,
                onSave = viewModel::saveSummaryToField,
                onCancel = viewModel::discardAiResult,
                onCopy = { copyToClipboard(context, task.result.orEmpty()) }
            )
            return@let
        }
        AiTaskDialog(
            task = task,
            target = aiTarget,
            onConfirmSend = viewModel::sendAi,
            onCancel = viewModel::cancelAi,
            onAdopt = {
                // 润色替换字段，建议存进「AI 总结」这一栏
                if (task.kind == AiTaskKind.POLISH) {
                    viewModel.adoptAiResult()
                } else {
                    viewModel.saveSummaryToField()
                }
            },
            onCopy = { copyToClipboard(context, task.result.orEmpty()) },
            onSaveToNotes = viewModel::saveResultToAiNotes,
            onDiscard = viewModel::discardAiResult,
            onRetry = viewModel::sendAi
        )
    }

    if (templatePickerOpen) {
        TemplatePickerDialog(
            templates = templates,
            currentTemplateId = entry?.templateId,
            onDismiss = { templatePickerOpen = false },
            onPick = { templateId ->
                templatePickerOpen = false
                viewModel.switchTemplate(templateId)
            }
        )
    }
}

@Composable
private fun EditorHeader(
    date: String,
    templateName: String,
    saveState: SaveState,
    isComplete: Boolean,
    onBack: () -> Unit,
    onOpenTemplatePicker: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onMenuDismiss: () -> Unit,
    onExport: () -> Unit,
    onToggleComplete: () -> Unit,
    aiEnabled: Boolean = false,
    onAiSummary: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp)
            .padding(top = 22.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconActionButton(text = "‹", onClick = onBack)
            Column {
                Text(
                    // 日期只读：规格 §4.1 明确日记创建后日期不可编辑
                    text = if (date.isBlank()) "日记" else DateTimeUtil.formatDateChinese(date),
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = templateName,
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SaveStateChip(state = saveState)
            IconActionButton(text = "▦", onClick = onOpenTemplatePicker)
            Box {
                IconActionButton(text = "···", onClick = { onMenuToggle(true) })
                ShiyeDropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
                    ShiyeMenuItem(
                        text = if (isComplete) "标记为草稿" else "标记为完成",
                        onClick = onToggleComplete
                    )
                    ShiyeMenuItem(text = "导出为 Markdown", onClick = onExport)
                    // AI 关着时这一项不出现，不给一个"点了才知道用不了"的入口
                    if (aiEnabled) {
                        ShiyeMenuItem(text = "AI 建议（存入 AI 总结）", onClick = onAiSummary)
                    }
                }
            }
        }
    }
}
// 复制走公共实现：ui/util/Clipboard.kt（灵感页也要用）
/** 切换模板后保留下来的旧字段，标注一下来源，避免用户以为是模板自带的 */
@Composable
private fun LegacyFieldNotice() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ShiyeExtras.accentSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "原模板字段 · 切换时保留",
            style = ShiyeType.caption,
            color = ShiyeExtras.accentStrong
        )
    }
}

@Composable
private fun SaveFailedBanner(onRetry: () -> Unit) {
    ShiyeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "保存失败",
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "内容还在编辑区里，可以重试。",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(InnerRadius))
                    .background(ShiyeExtras.dangerSoft)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "重试",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 切换模板的选择弹窗。
 *
 * 说明文案里就写清"无法对应的旧字段会保留"——规格要求切换前明确提示，
 * 与其切完再弹一次，不如在点之前就说清楚。
 */
@Composable
private fun TemplatePickerDialog(
    templates: List<Template>,
    currentTemplateId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(CardRadius))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            Text(
                text = "切换模板",
                style = ShiyeType.pageTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "同名或同标签的字段会保留内容；无法对应的旧字段会原样留在日记里，不会丢失。",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            templates.forEachIndexed { index, template ->
                val selected = template.id == currentTemplateId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(InnerRadius))
                        .background(if (selected) ShiyeExtras.accentSoft else Color.Transparent)
                        .clickable(enabled = !selected) { onPick(template.id) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = template.name,
                        style = ShiyeType.bodySecondary,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selected) "当前" else "${template.fields.size} 个字段",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index < templates.lastIndex) HairlineDivider()
            }
            Text(
                text = "取消",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp)
            )
        }
    }
}
