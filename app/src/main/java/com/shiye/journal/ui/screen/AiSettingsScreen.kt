package com.shiye.journal.ui.screen

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.domain.AiPrompts
import com.shiye.journal.domain.NumberInput
import com.shiye.journal.R
import com.shiye.journal.features.ai.AiConfig
import com.shiye.journal.features.ai.AiMode
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.ShiyeButton
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.components.TopBarTextButton
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.AiPromptKind
import com.shiye.journal.ui.viewmodel.AiSettingsViewModel

/**
 * AI 辅助配置页（规格 §8.1）。
 *
 * 可以保存**多套配置**（比如一个 DeepSeek、一个本机 Ollama），
 * 只点一下就切换用哪套 —— 换着用还得重新填地址和 Key 太折磨人了。
 *
 * 三件事必须让用户看明白，否则"可选 AI"就是一句空话：
 * 1. **默认关闭**，关着的时候一个字节都不往外发；
 * 2. 每一套要填什么（名字、地址、模型名，云端还要 Key）；
 * 3. 填完能不能通（测试连接）。
 */
@Composable
fun AiSettingsScreen(
    viewModel: AiSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val savedKeyMask by viewModel.savedKeyMask.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val keyCleaned by viewModel.keyCleaned.collectAsStateWithLifecycle()
    val promptsExpanded by viewModel.promptsExpanded.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<AiConfig?>(null) }
    var guideOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // 头部只有标题：返回箭头由应用壳的顶栏统一提供
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            PageHead(eyebrow = "AI, ONLY IF YOU ASK", title = "AI 辅助")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AI 默认关闭。关闭时日记、记账、灵感全部离线运行，不会发起任何网络请求。" +
                    "只有你在日记里主动点润色或总结时才会请求。",
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 连本地模型对不少人来说是道坎，把完整教程直接放在这一页
            Text(
                text = "不会连电脑上的本地模型？点这里看完整教程 →",
                style = ShiyeType.bodySecondary,
                color = ShiyeExtras.accentStrong,
                modifier = Modifier.clickable { guideOpen = true }
            )

            // ---- 已保存的配置 ----
            ShiyeCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已保存的配置",
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TopBarTextButton(text = "＋ 新建", onClick = viewModel::startNew)
                }

                // 关闭这一项固定在最上面：它是默认状态，不该藏在列表里
                ProfileRow(
                    title = "不使用 AI（关闭）",
                    subtitle = "完全离线，不联网",
                    selected = activeId == null,
                    onSelect = viewModel::turnOff,
                    showActions = false,
                    onEdit = {},
                    onDelete = {}
                )

                if (profiles.isEmpty()) {
                    Text(
                        text = "还没保存过配置。点右上角「新建」，把地址和模型名填进去就能用了。",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                } else {
                    profiles.forEach { profile ->
                        ProfileRow(
                            title = profile.displayName(),
                            subtitle = "${profile.safeSummary()}　${profile.baseUrl}",
                            selected = activeId == profile.id,
                            onSelect = { viewModel.use(profile.id) },
                            showActions = true,
                            onEdit = { viewModel.edit(profile.id) },
                            onDelete = { pendingDelete = profile }
                        )
                    }
                }
            }

            // ---- 编辑区 ----
            ShiyeCard {
                val isNew = draft.id.isBlank()
                Text(
                    text = if (isNew) "新建配置" else "编辑「${draft.displayName()}」",
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ShiyeTextField(
                    value = draft.name,
                    onValueChange = viewModel::setName,
                    label = "名字",
                    placeholder = "例如：DeepSeek / 家里的小模型",
                    singleLine = true,
                    modifier = Modifier.padding(top = 10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Text(
                    text = "模式",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
                ModeOption(
                    title = "本地模型",
                    subtitle = "连自己电脑上跑的模型（Ollama / LM Studio 等）",
                    selected = draft.mode == AiMode.LOCAL,
                    onSelect = { viewModel.setMode(AiMode.LOCAL) }
                )
                ModeOption(
                    title = "云端 API",
                    subtitle = "连 OpenAI 兼容接口，需要 API Key",
                    selected = draft.mode == AiMode.CLOUD,
                    onSelect = { viewModel.setMode(AiMode.CLOUD) }
                )
                ModeOption(
                    title = "停用这一套",
                    subtitle = "存着但不用（选中它等于关闭 AI）",
                    selected = draft.mode == AiMode.OFF,
                    onSelect = { viewModel.setMode(AiMode.OFF) }
                )

                if (draft.mode != AiMode.OFF) {
                    Text(
                        text = "快捷填入（之后仍可改）",
                        style = ShiyeType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 只显示属于当前模式的预设：本地模型页面冒出"OpenAI 兼容接口"
                        // 会让人以为本地服务也要填 Key
                        AiConfig.PRESETS.filter { it.mode == draft.mode }.forEach { preset ->
                            TopBarTextButton(
                                text = preset.label,
                                onClick = {
                                    viewModel.applyPreset(preset.baseUrl, preset.model, preset.provider)
                                }
                            )
                        }
                    }

                    // 本地模式的连通性完全卡在这三件事上，不写清楚用户会以为 App 坏了
                    if (draft.mode == AiMode.LOCAL) {
                        Text(
                            text = "把地址里的 IP 换成电脑的局域网 IP（电脑上 ipconfig 查看）。" +
                                "手机和电脑要在同一个网络里 —— 连同一个 Wi-Fi，或用手机开热点让电脑连上。",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "电脑上还要做两件事：" +
                                "① Ollama 默认只听本机，需设 OLLAMA_HOST=0.0.0.0 后重启；" +
                                "② 防火墙放行 11434 端口。",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    ShiyeTextField(
                        value = draft.baseUrl,
                        onValueChange = viewModel::setBaseUrl,
                        label = "Base URL",
                        placeholder = if (draft.mode == AiMode.LOCAL) {
                            "http://10.0.2.2:11434/v1"
                        } else {
                            "https://api.deepseek.com/v1"
                        },
                        singleLine = true,
                        modifier = Modifier.padding(top = 10.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        )
                    )
                    ShiyeTextField(
                        value = draft.model,
                        onValueChange = viewModel::setModel,
                        label = "模型名称",
                        placeholder = "deepseek-chat",
                        singleLine = true,
                        modifier = Modifier.padding(top = 10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    TimeoutField(
                        seconds = draft.timeoutSeconds,
                        onCommit = viewModel::setTimeout,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (draft.mode == AiMode.CLOUD) {
                    ShiyeTextField(
                        value = draft.apiKey,
                        onValueChange = viewModel::setApiKey,
                        label = "API Key（重新输入即替换）",
                        placeholder = "sk-…",
                        singleLine = true,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    if (keyCleaned) {
                        Text(
                            text = "粘贴内容里带着换行或看不见的符号，已经自动去掉了 —— " +
                                "这些字符会让请求直接发不出去。",
                            style = ShiyeType.caption,
                            color = ShiyeExtras.accentStrong,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (savedKeyMask.isNotBlank()) {
                        Text(
                            text = "已保存：$savedKeyMask",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // 提示词默认收起：多数人不需要动它，摊开只会把这一页撑得很长
            ShiyeCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::togglePromptsExpanded),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "提示词（可改，也可不改）",
                            style = ShiyeType.cardTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (promptsExpanded) {
                                "默认提示词已经填好，直接在上面改就行"
                            } else {
                                "点开可以改 AI 的要求，默认内容已经填在框里了"
                            },
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = if (promptsExpanded) "收起" else "展开",
                        style = ShiyeType.caption,
                        color = ShiyeExtras.accentStrong
                    )
                }

                if (promptsExpanded) {
                    Text(
                        text = "你填的是「要 AI 做什么」，会替换默认要求。" +
                            "但「不许编造内容、只输出结果」这两条固定规则始终生效、改不掉 —— " +
                            "那是为了不让 AI 往你的日记里编东西。",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    PromptField(
                        title = "AI 润色",
                        hint = "润色某个字段时用",
                        value = draft.polishRule,
                        defaultValue = AiPrompts.DEFAULT_POLISH_RULE,
                        onValueChange = viewModel::setPolishRule,
                        onReset = { viewModel.resetRule(AiPromptKind.POLISH) }
                    )
                    PromptField(
                        title = "AI 总结（一篇日记的建议）",
                        hint = "编辑页菜单里触发",
                        value = draft.entryAdviceRule,
                        defaultValue = AiPrompts.DEFAULT_ENTRY_ADVICE_RULE,
                        onValueChange = viewModel::setEntryAdviceRule,
                        onReset = { viewModel.resetRule(AiPromptKind.ENTRY_ADVICE) }
                    )
                    PromptField(
                        title = "最近七天的建议",
                        hint = "AI 栏目页里触发",
                        value = draft.adviceRule,
                        defaultValue = AiPrompts.DEFAULT_ADVICE_RULE,
                        onValueChange = viewModel::setAdviceRule,
                        onReset = { viewModel.resetRule(AiPromptKind.ADVICE) }
                    )
                }
            }

            if (draft.mode != AiMode.OFF) {
                ShiyeCard {
                    Text(
                        text = "测试连接",
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "会向上面填的地址发一条最短的请求（正文只有 hi），" +
                            "用来确认地址、模型名和 Key 都对。",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    ShiyeButton(
                        text = if (testing) "测试中…" else "测试连接",
                        enabled = !testing,
                        onClick = viewModel::testConnection,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    testResult?.let { result ->
                        val isError = !result.startsWith("连通了")
                        Text(
                            text = result,
                            style = ShiyeType.caption,
                            color = if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                ShiyeExtras.accentStrong
                            },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShiyeButton(text = "保存这套配置", onClick = viewModel::save)
                    // 正在编辑的不是当前在用的那套时，给个直接切过去的入口
                    if (draft.id.isNotBlank() && activeId != draft.id && draft.mode != AiMode.OFF) {
                        TopBarTextButton(text = "改用这套", onClick = { viewModel.use(draft.id) })
                    }
                }
            }

            message?.let { m ->
                Text(
                    text = m,
                    style = ShiyeType.caption,
                    color = ShiyeExtras.accentStrong,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }

    if (guideOpen) {
        LocalModelGuideDialog(onDismiss = { guideOpen = false })
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = "删除「${profile.displayName()}」？",
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "这套配置和它的 API Key 会一起删掉。日记内容不受影响。",
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * 一个提示词输入框。
 *
 * 没填的时候显示默认的那一套（灰字占位），让用户一眼知道"现在用的是什么"，
 * 也方便照着改 —— 空白框让人无从下手。
 */
@Composable
private fun PromptField(
    title: String,
    hint: String,
    value: String,
    defaultValue: String,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 和默认一模一样 = 还没动过；改过（或清空了）才给"恢复默认"
    val customized = value.trim() != defaultValue.trim()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (customized) "$hint　已改成你自己的" else "$hint　当前是默认",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (customized) {
                TopBarTextButton(text = "恢复默认", onClick = onReset)
            }
        }
        ShiyeTextField(
            value = value,
            onValueChange = onValueChange,
            label = "发给 AI 的要求（可直接在上面改）",
            placeholder = defaultValue,
            singleLine = false,
            minLines = 4,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * 本地模型连接教程。
 *
 * 内容放在 `res/raw/local_model_guide.txt`：改文案不用动代码，
 * 也不必为了几段说明去写一堆字符串常量。
 */
@Composable
private fun LocalModelGuideDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val guide = remember {
        runCatching {
            context.resources.openRawResource(R.raw.local_model_guide)
                .bufferedReader().use { it.readText() }
        }.getOrDefault("教程加载失败了。")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "怎么连电脑上的本地模型",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = guide,
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * 超时秒数输入框。
 *
 * **必须自己持有 TextFieldValue**：框里的值是由一个整数派生出来的字符串，
 * 而我们又会在输入过程中清洗它（丢掉非数字、去掉多余前导零）。
 * 文本一改长度，普通的 `String` 重载会沿用旧光标下标 ——
 * 光标跑到被删掉的字符前面，看着能点进去，字却输不进去了。
 * （和日期框当年那个问题同因同源。）
 *
 * 另一个细节：这里在**失焦时**才把外部的值同步回来。
 * 输入过程中同步的话，用户敲"7"想接"0"组成 70，
 * 外部值一变就可能被覆盖重写回来，又是一次光标跳飞。
 */
@Composable
private fun TimeoutField(
    seconds: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var field by remember {
        mutableStateOf(TextFieldValue(seconds.toString(), TextRange(seconds.toString().length)))
    }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(seconds, focused) {
        if (!focused) {
            val text = seconds.toString()
            if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
        }
    }

    ShiyeTextField(
        value = field,
        onValueChange = { next ->
            val (cleaned, cursor) = NumberInput.sanitize(next.text, next.selection.start)
            field = TextFieldValue(cleaned, TextRange(cursor))
            // 删空的那一刻先不动配置：还不知道他要填什么，别急着替他写成 0
            if (cleaned.isNotEmpty()) {
                cleaned.toLongOrNull()?.toInt()?.let { raw ->
                    val clamped = raw.coerceIn(AiConfig.MIN_TIMEOUT_SECONDS, AiConfig.MAX_TIMEOUT_SECONDS)
                    onCommit(clamped)
                }
            }
        },
        label = "超时（秒）",
        placeholder = AiConfig.DEFAULT_TIMEOUT_SECONDS.toString(),
        singleLine = true,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        )
    )
}

/** 档案列表里的一行：点名字切换使用，右侧是编辑 / 删除 */
@Composable
private fun ProfileRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    showActions: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .clickable(onClick = onSelect)
        ) {
            Text(
                text = title,
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showActions) {
            TopBarTextButton(text = "编辑", onClick = onEdit)
            TopBarTextButton(text = "✕", onClick = onDelete)
        }
    }
}

@Composable
private fun ModeOption(
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
            Text(
                text = title,
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
