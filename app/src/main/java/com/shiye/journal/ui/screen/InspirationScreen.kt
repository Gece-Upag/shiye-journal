package com.shiye.journal.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.Inspiration
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.InnerRadius
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.SelectMark
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeDropdownMenu
import com.shiye.journal.ui.components.ShiyeMenuItem
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.util.copyToClipboard
import com.shiye.journal.ui.viewmodel.InspirationViewModel
import com.shiye.journal.util.DateTimeUtil

/**
 * 灵感页（规格 §6）。
 *
 * 表现形态是**自己给自己发的聊天**：气泡靠右、右上角收成小圆角，下方一行时间。
 * 只有一种角色（没有头像、没有对方），所以布局是"全靠右"，不做左右两种分支 ——
 * 原型里也是这样，硬造一个"对方"只会让人以为有社交功能。
 *
 * 交互：
 * - 底部输入框直接发（Enter 发送 / Shift+Enter 换行），发送后立刻写库、出现在顶部；
 * - 长按气泡弹菜单，可选**原地编辑**（内容回填输入框，输入条进入编辑态）或删除；
 * - 删除必须二次确认，确认后永久删除。
 *
 * 删一批：去**搜索页**（顶栏 ⌕），批量删除收在那里。
 * 首页顶栏不再挂这个入口 —— 它是"顺手就点得到"的位置，不适合放破坏性动作。
 */
@Composable
fun InspirationScreen(
    viewModel: InspirationViewModel,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val pageMessage by viewModel.pageMessage.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    var inputText by remember { mutableStateOf("") }
    // 长按待操作的那条；非 null 时展开菜单
    var menuTarget by remember { mutableStateOf<Inspiration?>(null) }
    // 二次确认独立于菜单：两个弹层叠在一起会互相抢焦点
    var pendingDelete by remember { mutableStateOf<Inspiration?>(null) }

    // 进入编辑态时把原内容填进输入框；退出编辑态时清空，避免上一条的内容残留
    LaunchedEffect(editing?.id) {
        inputText = editing?.content.orEmpty()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PageHead(eyebrow = "IDEA CATCHER", title = "灵感")
            Text(
                text = "「${currentPage?.name ?: "灵感页"}」· 想到什么就先记下来，不用整理。",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (items.isEmpty()) {
            // 空状态也占满剩余空间，把输入条压到屏幕底部 ——
            // 否则它会紧跟在空状态文字下面悬在半空，看起来像坏了一样
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                EmptyState(
                    title = "还没有灵感",
                    hint = "随手一句话也可以，先记下来再说。",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            // reverseLayout 让"最新的在最上面"，同时新发的一条直接滚到视野里；
            // 普通的 LazyColumn 需要手工 scrollToItem(0)，这里省掉这一步
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                reverseLayout = true
            ) {
                items(items = items, key = { it.id }) { inspiration ->
                    Box {
                        InspirationBubble(
                            inspiration = inspiration,
                            isEditing = editing?.id == inspiration.id,
                            onLongClick = { menuTarget = inspiration }
                        )
                        ShiyeDropdownMenu(
                            expanded = menuTarget?.id == inspiration.id,
                            onDismissRequest = { menuTarget = null }
                        ) {
                            ShiyeMenuItem(
                                text = "复制这条",
                                onClick = {
                                    copyToClipboard(context, inspiration.content, "灵感")
                                    menuTarget = null
                                }
                            )
                            ShiyeMenuItem(
                                text = "编辑这条",
                                onClick = {
                                    viewModel.beginEdit(inspiration.id)
                                    menuTarget = null
                                }
                            )
                            ShiyeMenuItem(
                                text = "删除这条",
                                isDanger = true,
                                onClick = {
                                    pendingDelete = inspiration
                                    menuTarget = null
                                }
                            )
                        }
                    }
                }
            }
        }

        // 页操作结果（删页）：过几秒自己消失，不自动清会一直挂在输入条上方
        if (pageMessage != null) {
            LaunchedEffect(pageMessage) {
                kotlinx.coroutines.delay(2_500)
                viewModel.clearPageMessage()
            }
            Text(
                text = pageMessage.orEmpty(),
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }

        // 错误提示：空内容/超长点发送时给出原因，而不是什么反馈都没有
        if (errorMessage != null) {
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(2_000)
                viewModel.clearError()
            }
            Text(
                text = errorMessage.orEmpty(),
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }

        InspirationInputBar(
            text = inputText,
            isEditing = editing != null,
            onTextChange = { inputText = it },
            onSubmit = {
                // submit 返回 false 表示被规则拦下，输入框内容保留让用户改
                if (viewModel.submit(inputText)) {
                    inputText = ""
                }
            },
            onCancelEdit = {
                viewModel.cancelEdit()
                inputText = ""
            },
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "永久删除这条灵感？",
            message = "删除后无法恢复，也不会包含在备份中。\n「${previewOf(item.content)}」",
            confirmText = "永久删除",
            onConfirm = {
                viewModel.delete(item.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** 弹窗里只显示开头一小段，太长了会顶出屏幕 */
private fun previewOf(content: String): String =
    if (content.length > 20) "${content.take(20)}…" else content

/**
 * 一条灵感气泡。
 *
 * 用 Surface 自己画而不是复用 [ShiyeCard]：卡片是整宽、四角同圆角，
 * 气泡是窄、右上角收窄（像原型的 .bubble.own），两种形态硬凑会不伦不类。
 */
@Composable
fun InspirationBubble(
    inspiration: Inspiration,
    isEditing: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 5.dp,
        bottomEnd = 18.dp,
        bottomStart = 18.dp
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (selectable) Arrangement.Start else Arrangement.End
    ) {
        if (selectable) {
            SelectMark(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 12.dp)
            )
        }
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = shape,
                color = if (isEditing) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    ShiyeExtras.accentSoft
                },
                border = BorderStroke(
                    1.dp,
                    if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            ) {
                Text(
                    text = inspiration.content,
                    style = ShiyeType.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }

            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing) {
                    Text(
                        text = "编辑中",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = DateTimeUtil.formatInspirationTime(inspiration.createdAt),
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (inspiration.updatedAt > inspiration.createdAt) {
                    Text(
                        text = "已编辑",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 底部输入条。
 *
 * 编辑态下三点不同：上方一行「编辑灵感」说明、按钮文案变「保存」、多出一个"取消"。
 * 都是为了让"我现在是在改一条旧的"这件事一眼可见 ——
 * 否则很容易以为自己在发新的，结果把原来的那条覆盖了。
 */
@Composable
private fun InspirationInputBar(
    text: String,
    isEditing: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编辑灵感",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "取消",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onCancelEdit)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(InnerRadius)),
                placeholder = {
                    Text(
                        text = "记点什么…",
                        style = ShiyeType.input,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = ShiyeType.input.copy(color = MaterialTheme.colorScheme.onSurface),
                maxLines = 5,
                // 原型的约定：Enter 发送、Shift+Enter 换行。
                // 软键盘上体现为回车键变成"发送"；硬键盘 Shift+Enter 仍输入换行
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ShiyeExtras.surface2,
                    unfocusedContainerColor = ShiyeExtras.surface2,
                    disabledContainerColor = ShiyeExtras.surface2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(InnerRadius))
                    .background(
                        if (text.isBlank()) ShiyeExtras.surface2 else MaterialTheme.colorScheme.primary
                    )
                    .clickable(onClick = onSubmit)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEditing) "保存" else "发送",
                    style = ShiyeType.cardTitle,
                    color = if (text.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.White
                    }
                )
            }
        }

        if (!isEditing) {
            Text(
                text = "Enter 发送 · Shift + Enter 换行",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}
