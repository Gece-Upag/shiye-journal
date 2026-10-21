package com.shiye.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import com.shiye.journal.domain.DateInput
import com.shiye.journal.domain.DateInputResult
import com.shiye.journal.domain.PeriodKind
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.util.DateTimeUtil

/**
 * 聚焦后等多久再申请"滚进视野"。
 *
 * 键盘弹出会同时改可视区高度，Inset 变化那一帧容器高度还没更新，
 * 立刻 bringIntoView 会按旧高度算，滚不到位。一帧多点就够了。
 */
private const val IME_REVEAL_DELAY_MILLIS = 120L

/**
 * 输入框。
 *
 * 标签放在框外面而不是做成浮动标签：日记模板的字段标签（"今天学到什么"）普遍较长，
 * 浮动到框线上会挤成一团。外面单独一行更清楚，也方便在右侧挂"必填"角标。
 *
 * @param minLines 多行输入的最小高度。规格 §4.3 要求多行字段"至少约 5 行高度"。
 */
@Composable
fun ShiyeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    /** 标签行右端的可选动作（V2 用来挂「AI 润色」）。不给就不占地方 */
    action: (@Composable () -> Unit)? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default
) {
    ShiyeTextFieldShell(
        label = label,
        modifier = modifier,
        required = required,
        action = action,
        enabled = enabled,
        field = { fieldModifier, onFocusChanged ->
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = fieldModifier.onFocusChanged { onFocusChanged(it.isFocused) },
                enabled = enabled,
                textStyle = ShiyeType.input,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else minLines,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                placeholder = placeholder?.let {
                    {
                        Text(
                            text = it,
                            style = ShiyeType.input,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = OUTLINED_FIELD_COLORS
            )
        }
    )
}

/**
 * 需要**自己接管光标**时就用这个重载。
 *
 * 什么时候必须用它：框里的值不是用户直接敲的原文，而是被我们加工过的结果
 * （比如整数输入框丢掉非数字字符、日期输入框自动补 `-`）。
 * 文本一变长变短，[String] 那个重载会沿用旧的光标下标，
 * 表现为光标跑到被删掉的字符前面、"能点进去但字输不进"。
 */
@Composable
fun ShiyeTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default
) {
    ShiyeTextFieldShell(
        label = label,
        modifier = modifier,
        required = required,
        action = action,
        enabled = enabled,
        field = { fieldModifier, onFocusChanged ->
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = fieldModifier.onFocusChanged { onFocusChanged(it.isFocused) },
                enabled = enabled,
                textStyle = ShiyeType.input,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else minLines,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                placeholder = placeholder?.let {
                    {
                        Text(
                            text = it,
                            style = ShiyeType.input,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = OUTLINED_FIELD_COLORS
            )
        }
    )
}

/**
 * 输入框的公共外壳：标签行 + 键盘避让 + 圆角背景。
 *
 * 两个重载共用它 —— 复制两份样式代码迟早会改漏一处。
 */
@Composable
private fun ShiyeTextFieldShell(
    label: String,
    modifier: Modifier,
    required: Boolean,
    action: (@Composable () -> Unit)?,
    enabled: Boolean,
    field: @Composable (Modifier, (Boolean) -> Unit) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = ShiyeType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (required) {
                Text(
                    text = "必填",
                    style = ShiyeType.caption,
                    color = ShiyeExtras.accentStrong,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (action != null) {
                Box(modifier = Modifier.padding(start = 6.dp)) { action() }
            }
        }

        // 键盘弹起来时，把正在编辑的这个框顶到键盘上方。
        // 光靠滚动容器还不够：Compose 判断"是否可见"用的是它认为的可视区，
        // 如果页面没做 IME 避让，框虽然被键盘盖住了，系统仍判定"在视野内"而不滚。
        // 所以容器要 imePadding（缩小可视区），这里再主动申请一次 bringIntoView（滚到位）。
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        val relocationScope = rememberCoroutineScope()
        var focused by remember { mutableStateOf(false) }

        LaunchedEffect(focused) {
            if (focused) {
                // 等键盘把可视区压下来那一帧过去再请求，否则算出来的可视高度还是旧的
                relocationScope.launch {
                    delay(IME_REVEAL_DELAY_MILLIS)
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }

        field(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(InnerRadius))
                .bringIntoViewRequester(bringIntoViewRequester)
        ) { focused = it }
    }
}

private val OUTLINED_FIELD_COLORS
    @Composable
    get() = TextFieldDefaults.colors(
        focusedContainerColor = ShiyeExtras.surface2,
        unfocusedContainerColor = ShiyeExtras.surface2,
        disabledContainerColor = ShiyeExtras.surface2,
        errorContainerColor = ShiyeExtras.surface2,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.primary
    )

/** 下拉菜单项。统一"危险操作用红色"的约定，避免删除项看起来和别的一样。 */
@Composable
fun ShiyeMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = ShiyeType.bodySecondary,
                color = if (isDanger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = MenuDefaults.itemColors()
    )
}

/**
 * 下拉菜单容器。统一圆角与底色，避免各页面自己写一遍导致深浅色不一致。
 */
@Composable
fun ShiyeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        content = content
    )
}

/**
 * 日期选择框（可复用的那一个）。
 *
 * **可以点，也可以敲**：
 * - 平时就是一行日期（或 [emptyText]），点一下就地变成输入框并弹出数字键盘；
 * - 直接敲 8 位数字即可，分隔线自动补（敲满且是合法日期就自动落定，不用再按确定）；
 * - 右边「历」打开日历选择器，用日历点选；
 * - 进入输入态后「历」变成「✕」，点它放弃这次输入回到原值；
 * - 有 [onClear] 时旁边还有「清空」
 *   （日记搜索要求日期范围两端可独立清空，借款的到期日则是"可以不约"）。
 *
 * 数字键盘的位数上限与合法性判断统一走 [DateInput]，这里不自己解析日期。
 *
 * Material3 的 [DatePicker] 用的是 UTC 当天 0 点的毫秒值，
 * 与本地日期的换算统一走 [DateTimeUtil.toPickerMillis] / [DateTimeUtil.fromPickerMillis]，
 * 直接拿系统时区转会差一天。
 */
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "不限",
    onClear: (() -> Unit)? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun start() {
        // 空着开始，原值当占位符显示：这样敲第一个数字就是"重新输入"，
        // 不会出现在旧日期后面追加、又被 8 位上限悄悄截断的怪事
        field = TextFieldValue("")
        editing = true
        error = null
    }

    fun stop() {
        editing = false
        field = TextFieldValue("")
    }

    fun commit(input: String) {
        when (val result = DateInput.parseDate(input)) {
            is DateInputResult.Ok -> {
                error = null
                stop()
                onPick(DateTimeUtil.formatDate(result.date))
            }

            is DateInputResult.Error -> {
                error = result.message
            }
        }
    }

    /**
     * 接管光标位置：补出来的 `-` 不占光标位，否则敲到第 5 位数字时光标会被留在 `-` 前面，
     * 下一个数字插成了 2026-10-61。规则在 [DateInput.applyEdit]，有单测按着。
     */
    fun edit(raw: TextFieldValue) {
        val next = DateInput.applyEdit(field.text, raw.text, raw.selection.end)
        field = TextFieldValue(next.text, TextRange(next.caret))
        // 敲满 8 位且是真实存在的日子 → 直接落定
        if (DateInput.parseDate(next.text) is DateInputResult.Ok) commit(next.text)
    }

    // 进入输入态自动弹键盘
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    // 值被外部改了（比如在日历里另选了一个），收起输入态，免得框里同时挂着两个值
    LaunchedEffect(value) {
        if (editing) stop()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = ShiyeType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(InnerRadius))
                    .background(ShiyeExtras.surface2)
                    .clickable(enabled = !editing) { start() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (editing) {
                            BasicTextField(
                                value = field,
                                onValueChange = ::edit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { commit(field.text) }),
                                textStyle = ShiyeType.input.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (field.text.isEmpty()) {
                                Text(
                                    text = value.ifBlank { emptyText },
                                    style = ShiyeType.input,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = value.ifBlank { emptyText },
                                style = ShiyeType.input,
                                color = if (value.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    JumpActionButton(
                        text = if (editing) "✕" else "历",
                        tint = if (editing) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            ShiyeExtras.accentStrong
                        },
                        onClick = {
                            if (editing) {
                                error = null
                                stop()
                            } else {
                                showPicker = true
                            }
                        }
                    )
                }
            }
            if (onClear != null && value.isNotBlank()) {
                JumpActionButton(
                    text = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onClear
                )
            }
        }

        // 输错了给一句红字原因，说清为什么没生效
        if (error != null) {
            Text(
                text = error.orEmpty(),
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
            )
        }
    }

    if (showPicker) {
        val initial = DateTimeUtil.parseDate(value)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial?.let { DateTimeUtil.toPickerMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPicker = false
                        DateTimeUtil.fromPickerMillis(state.selectedDateMillis)?.let {
                            onPick(DateTimeUtil.formatDate(it))
                        }
                    }
                ) { Text("确定", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * 补以前内容的日期选择弹窗（日记的「补写回忆」、记账的「补账单」共用）。
 *
 * 三种改日期的方式都给全：左右箭头一天天地挪、直接敲数字、点「历」开系统日历。
 * 只给日历的话，要补三年前某一天得连点十几次翻月份，反而更慢。
 *
 * 只让选**今天或更早**：这是补记过去，选到未来会写出一篇日期排在列表最前面、
 * 却还没发生的记录，看着像排序坏了。
 *
 * 文案按调用方走（[title] / [confirmText] / [futureError]），
 * 默认值是日记那套；记账传自己的说法。
 */
@Composable
fun BackfillDateDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "补写哪一天？",
    confirmText: String = "开始写",
    futureError: String = "补写回忆只能选今天或更早的日子"
) {
    var date by remember { mutableStateOf(initialDate) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = remember { DateTimeUtil.todayString() }

    fun shift(days: Long) {
        val current = DateTimeUtil.parseDate(date) ?: return
        val next = current.plusDays(days)
        if (DateTimeUtil.formatDate(next) > today) {
            error = futureError
            return
        }
        error = null
        date = DateTimeUtil.formatDate(next)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JumpActionButton(text = "‹", tint = ShiyeExtras.accentStrong, onClick = { shift(-1) })
                    Text(
                        text = DateTimeUtil.formatDateShortWithWeekday(date),
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    JumpActionButton(text = "›", tint = ShiyeExtras.accentStrong, onClick = { shift(1) })
                }

                DatePickerField(
                    label = "日期",
                    value = date,
                    emptyText = "选择日期",
                    onPick = { picked ->
                        if (picked > today) {
                            error = futureError
                        } else {
                            error = null
                            date = picked
                        }
                    },
                    modifier = Modifier.padding(top = 10.dp)
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (date <= today) onConfirm(date)
                }
            ) { Text(confirmText, color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CardRadius)
    )
}

/**
 * 横向铺开的**选项贴片**，用于记一笔时的分类与账户选择。
 *
 * 用贴片而不是下拉：分类最多 8 个，一次看完再点比展开一层菜单快，
 * 也不会遮住下面的金额。
 */
@Composable
fun <T> ChipSelector(
    options: List<Pair<T, String>>,
    selectedId: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = ""
) {
    if (options.isEmpty()) {
        Text(
            text = emptyHint,
            style = ShiyeType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, label) ->
            val isSelected = id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(InnerRadius))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else ShiyeExtras.surface2
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = label,
                    style = ShiyeType.label,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 保存状态徽章。
 *
 * 规格要求"不得只靠颜色表达状态"，所以这里**一定有文字**，
 * 颜色只是辅助：失败 = 危险色 + "保存失败"；成功 = 强调色 + "已保存"。
 */
@Composable
fun SaveStateChip(state: com.shiye.journal.domain.SaveState, modifier: Modifier = Modifier) {
    val (text, background, content) = when (state) {
        com.shiye.journal.domain.SaveState.IDLE -> Triple(
            "未改动",
            ShiyeExtras.surface2,
            MaterialTheme.colorScheme.onSurfaceVariant
        )

        com.shiye.journal.domain.SaveState.SAVING -> Triple(
            "保存中…",
            ShiyeExtras.accentSoft,
            ShiyeExtras.accentStrong
        )

        com.shiye.journal.domain.SaveState.SAVED -> Triple(
            "已保存",
            ShiyeExtras.accentSoft,
            ShiyeExtras.accentStrong
        )

        com.shiye.journal.domain.SaveState.FAILED -> Triple(
            "保存失败",
            ShiyeExtras.dangerSoft,
            MaterialTheme.colorScheme.error
        )
    }
    StatusChip(text = text, modifier = modifier, backgroundColor = background, contentColor = content)
}

/**
 * 可以直接敲日期的周期跳转框（统计页顶部那一行）。
 *
 * 平时就是一行周期标题（如「2026年10月」），**点一下就地变成输入框**，
 * 直接敲数字：年 4 位 / 年月 6 位 / 年月日 8 位，随粒度放开；
 * 分隔线自动补，粘进来的杂字符会被滤掉。
 * 敲完按键盘「完成」或右边 ✓ 跳转，✕ 放弃回到标题。
 *
 * 为什么不再让人只用左右箭头一格格翻：想看半年前那个月要点一百多下，
 * 手输只要 8 个数字。箭头保留，只用来左右挪一格。
 *
 * @param errorMessage 上一次输入的问题（如"不是一个有效日期"）。进入编辑时通过 [onErrorShown] 清掉，
 *   不然旧提示会一直挂在新一轮输入上，看着像这次也错了。
 * @param onSubmit 交给调用方去解析并跳转；**返回 true 表示成功**，本组件据此收起输入框
 *   （失败就留在原地让人看清提示再改）。
 * @param onEditingChange 把"正在编辑"抛给外层，外层要据此让出宽度（收起左右箭头）
 */
@Composable
fun DateJumpField(
    kind: PeriodKind,
    title: String,
    errorMessage: String?,
    onSubmit: (String) -> Boolean,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    onEditingChange: (Boolean) -> Unit = {}
) {
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }

    fun start() {
        field = TextFieldValue("")
        editing = true
        onEditingChange(true)
        onErrorShown()
    }

    fun stop() {
        // 不需要手动 clearFocus / 收键盘：BasicTextField 随 editing=false 离开组合树，
        // 焦点与软键盘会自动释放。
        editing = false
        field = TextFieldValue("")
        onEditingChange(false)
    }

    /**
     * 接管光标位置：补出来的 `-` 不占光标位，否则敲到第 5 位数字时光标会被留在 `-` 前面。
     * 规则在 [DateInput.applyEdit]，有单测按着。
     */
    fun edit(raw: TextFieldValue) {
        val next = DateInput.applyEdit(field.text, raw.text, raw.selection.end)
        field = TextFieldValue(next.text, TextRange(next.caret))
    }

    fun commit() {
        // 交回外层解析；只有真跳成功了才收起，失败时提示还在，输入框也还在
        if (onSubmit(field.text)) stop()
    }

    // 进入编辑就把光标放进去，省掉"点了框还要再点一次才出键盘"
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    // 粒度换了，同一串数字的含义也变了（"2026" 在月粒度下是残缺的），直接收起免得误解
    LaunchedEffect(kind) {
        if (editing) stop()
    }

    Column(modifier = modifier) {
        if (editing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = ::edit,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    textStyle = ShiyeType.input.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(InnerRadius))
                                .background(ShiyeExtras.surface2)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (field.text.isEmpty()) {
                                Text(
                                    text = DateInput.placeholder(kind),
                                    style = ShiyeType.input,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    }
                )
                JumpActionButton(
                    text = "✓",
                    tint = ShiyeExtras.accentStrong,
                    onClick = { commit() }
                )
                JumpActionButton(
                    text = "✕",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { stop() }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(InnerRadius))
                    .background(ShiyeExtras.surface2)
                    .clickable { start() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = title, style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // 输错了给红字原因，平时给一句"原来可以直接输"的提示
        val hint = if (errorMessage == null) {
            "点上面的日期可以直接输入，如 ${DateInput.placeholder(kind)}"
        } else {
            errorMessage
        }
        Text(
            text = hint,
            style = ShiyeType.caption,
            color = if (errorMessage == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
        )
    }
}

/** 日期跳转框右边那两个小块。做得比按钮小一圈，为的是不抢标题的注意力。 */
@Composable
private fun JumpActionButton(
    text: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ShiyeExtras.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ShiyeType.cardTitle, color = tint)
    }
}
