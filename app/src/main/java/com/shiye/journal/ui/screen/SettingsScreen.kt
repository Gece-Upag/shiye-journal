package com.shiye.journal.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.backup.BackupCodec
import com.shiye.journal.data.model.ThemeMode
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.SettingsViewModel
import com.shiye.journal.util.DateTimeUtil
import java.time.Instant
import java.time.ZoneId

/**
 * 设置页。
 *
 * 备份链路（规格 §7）：
 * - Markdown 导出：选文件夹 → 后台逐篇写出，显示 已完成/总数；
 * - 完整备份：选保存位置 → ZIP（JSON + 校验和），成功才更新"上次备份时间"；
 * - 从备份恢复：选文件 → 解析校验 → **先看摘要再二次确认** → 事务内原子替换。
 *
 * 上次备份与未备份天数只在页面展示，不弹窗、不自动备份（规格 §7.1）。
 * V1 全程不放 AI 入口：规格要求 V1 不实现 AI、不申请网络权限。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenAi: () -> Unit,
    onOpenAiNotes: () -> Unit,
    modifier: Modifier = Modifier,
    /** AI 当前状态的一句话摘要；null 表示这一版还没接 AI（不显示入口） */
    aiSummary: String? = null
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // SAF 三个入口。全部走系统文件选择器，不申请任何存储权限（规格 §7.2）
    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::exportMarkdown) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::createBackup) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::parseBackupForRestore) }

    val busy = step is SettingsViewModel.BackupStep.Busy || step is SettingsViewModel.BackupStep.MdExporting

    var aboutOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHead(eyebrow = "SETTINGS", title = "设置")

        // ---- 外观 ----
        ShiyeCard {
            Text(
                text = "外观",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            ThemeMode.entries.forEach { mode ->
                ThemeOptionRow(
                    title = when (mode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    subtitle = when (mode) {
                        ThemeMode.SYSTEM -> "随系统深色模式自动切换"
                        ThemeMode.LIGHT -> "始终使用浅色"
                        ThemeMode.DARK -> "始终使用深色"
                    },
                    selected = themeMode == mode,
                    onSelect = { viewModel.setThemeMode(mode) }
                )
            }
        }

        // ---- 数据：备份 / 恢复 / 导出 ----
        ShiyeCard {
            Text(
                text = "数据",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = backupSummary(lastBackupAt),
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            BackupActionRow(
                title = "导出 Markdown",
                subtitle = "把日记导出成可阅读的 .md 文件",
                enabled = !busy,
                onClick = { markdownLauncher.launch(null) }
            )
            BackupActionRow(
                title = "完整备份",
                subtitle = "全部数据打包成一个 ZIP，可用于恢复",
                enabled = !busy,
                onClick = { backupLauncher.launch(viewModel.suggestedBackupName()) }
            )
            BackupActionRow(
                title = "从备份恢复",
                subtitle = "选择本应用生成的备份文件，恢复会替换当前数据",
                enabled = !busy,
                onClick = { restoreLauncher.launch(arrayOf("*/*")) }
            )

            when (val s = step) {
                is SettingsViewModel.BackupStep.MdExporting -> {
                    Text(
                        text = "正在导出：${s.done} / ${s.total}",
                        style = ShiyeType.caption,
                        color = ShiyeExtras.accentStrong,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            color = ShiyeExtras.accentStrong,
                            trackColor = ShiyeExtras.surface2
                        )
                    }
                }

                SettingsViewModel.BackupStep.Busy -> Text(
                    text = "正在处理…",
                    style = ShiyeType.caption,
                    color = ShiyeExtras.accentStrong,
                    modifier = Modifier.padding(top = 10.dp)
                )

                else -> Unit
            }

            message?.let { m ->
                Text(
                    text = m.text,
                    style = ShiyeType.caption,
                    color = if (m.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        ShiyeExtras.accentStrong
                    },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        // ---- AI 辅助（V2，默认关闭） ----
        aiSummary?.let { summary ->
            ShiyeCard {
                Text(
                    text = "AI 辅助",
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                BackupActionRow(
                    title = "配置 AI",
                    subtitle = "选择本地模型或云端接口；关闭时不会联网",
                    enabled = true,
                    onClick = onOpenAi
                )
                BackupActionRow(
                    title = "AI 栏目",
                    subtitle = "查看保存下来的 AI 结果，生成最近七天的建议",
                    enabled = true,
                    onClick = onOpenAiNotes
                )
            }
        }

        // ---- 关于 ----
        val context = LocalContext.current
        ShiyeCard {
            Text(
                text = "关于",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "拾页 v${appVersionName(context.packageManager.getPackageInfoCompatSafe(context))}" +
                    " · 本地优先的个人日记与记账",
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            // 「关于」做成按钮而不是一段平铺的文字：正文较长，铺在设置页会把
            // 备份入口挤到屏幕外；收进弹窗后设置页一屏就能看完
            BackupActionRow(
                title = "关于拾页",
                subtitle = "了解这个应用的设计原则与数据处理方式",
                enabled = true,
                onClick = { aboutOpen = true }
            )
        }
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }

    // 恢复确认对话框：显示备份摘要 + 明确的替换警告（规格 §7.4 第 4/5/6 步）
    val preview = step as? SettingsViewModel.BackupStep.RestorePreview
    if (preview != null) {
        RestorePreviewDialog(
            payload = preview.payload,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore
        )
    }
}

/** 关于弹窗：把设计原则、数据处理方式、AI 的取舍一次说清楚。 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "关于拾页",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            // 正文较长，必须能滚：小屏上不滚的话「知道了」会被挤出屏幕
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "拾页是一款简洁、私密的个人记录工具，帮助你记录日常、整理收支、保存灵感，也留住生活中的点滴。",
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "我们坚持轻量、克制与隐私优先。你的日常记录默认保存在本地，无需注册或登录，也不进行云端同步。你可以自定义日记模板，随时搜索过往记录，并通过手动备份妥善保存自己的数据。",
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "AI 辅助功能完全由你自主选择。使用云端 AI 时，相关内容可能会发送至你配置的第三方服务，请在使用前确认。",
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "愿你在忙碌的生活中，给自己留下一页安静的空间，记录所想，珍藏所见。",
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "拾页 · 记录生活，也记录自己。",
                    style = ShiyeType.bodySecondary.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

/** 恢复确认对话框：先给人看备份里有什么，再问"是否替换当前数据"。 */
@Composable
private fun RestorePreviewDialog(
    payload: BackupCodec.Payload,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val exportedAt = Instant.ofEpochMilli(payload.exportedAt)
        .atZone(ZoneId.systemDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复这份备份？", style = ShiyeType.cardTitle) },
        text = {
            Column {
                Text(
                    text = "备份时间：%04d-%02d-%02d %02d:%02d".format(
                        exportedAt.year, exportedAt.monthValue,
                        exportedAt.dayOfMonth, exportedAt.hour, exportedAt.minute
                    ),
                    style = ShiyeType.bodySecondary
                )
                Text(
                    text = "数据版本：备份格式 v${payload.formatVersion} / 数据库 v${payload.dbVersion}",
                    style = ShiyeType.bodySecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "日记 ${payload.diaryEntries.size} 篇 · 模板 ${payload.templates.size} 套 · " +
                        "流水 ${payload.transactions.size} 笔 · 分类 ${payload.categories.size} 个 · " +
                        "账户 ${payload.accounts.size} 个 · 借款 ${payload.loans.size} 笔 · " +
                        "灵感 ${payload.inspirations.size} 条",
                    style = ShiyeType.bodySecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "恢复会替换当前的全部数据，此操作不可撤销。",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("替换当前数据") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 上次备份的展示行：时间 + 距今天数（规格 §7.1 要求的两项都只在这里显示）。 */
private fun backupSummary(lastBackupAt: Long?): String {
    if (lastBackupAt == null) return "上次备份：从未备份"
    val backupDateTime = Instant.ofEpochMilli(lastBackupAt).atZone(ZoneId.systemDefault())
    val backupDate = backupDateTime.toLocalDate()
    val days = DateTimeUtil.daysBetween(backupDate, DateTimeUtil.today())
    val time = "%04d-%02d-%02d %02d:%02d".format(
        backupDateTime.year, backupDateTime.monthValue,
        backupDateTime.dayOfMonth, backupDateTime.hour, backupDateTime.minute
    )
    return "上次备份：$time\n未备份天数：${if (days <= 0) "今天刚备份过" else "$days 天"}"
}

@Composable
private fun BackupActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ShiyeType.bodySecondary,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            Text(
                text = subtitle,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "›",
            style = ShiyeType.cardTitle,
            color = if (enabled) ShiyeExtras.accentStrong else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = ShiyeType.bodySecondary, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 版本号从 PackageManager 读，不需要为此打开 BuildConfig。 */
private fun appVersionName(info: android.content.pm.PackageInfo?): String =
    info?.versionName ?: "?"

private fun android.content.pm.PackageManager.getPackageInfoCompatSafe(context: android.content.Context): android.content.pm.PackageInfo? =
    runCatching { getPackageInfo(context.packageName, 0) }.getOrNull()
