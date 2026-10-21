package com.shiye.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import com.shiye.journal.navigation.Navigator
import com.shiye.journal.ui.theme.ShiyeType

/**
 * 顶部栏。
 *
 * 左侧是品牌（✳ 图标 +「拾页」），右侧放页面级操作按钮。
 * 采用原型那种"半透明背景 + 滚动时浮起"的做法：底色用背景色，
 * 不加阴影，靠下面一条 1dp 的分隔线和内容区分。
 *
 * 必须自己做 [statusBarsPadding]：App 是 edge-to-edge 绘制
 * （[com.shiye.journal.MainActivity] 里关掉了 decor fit），
 * 不避让的话整个顶栏会顶进状态栏里 —— 时钟压在品牌文字上，
 * 右侧图标被状态栏窗口挡住，点了没反应。
 */
@Composable
fun ShiyeTopBar(
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 品牌图标沿用原型：一个方块里放 ✳，配色用强调色
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✳", style = ShiyeType.cardTitle, color = Color.White)
                }
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(text = "拾页", style = ShiyeType.brand, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = subtitle,
                        style = ShiyeType.brandSub,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

/** 顶部栏右侧的方形图标按钮，40x40、圆角 14，与原型 .icon-btn 一致 */
@Composable
fun IconActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * 顶部栏右侧的**文字按钮**。
 *
 * 40dp 的方形图标按钮放不下"批量删除""补写回忆"这种四个字的动作 ——
 * 硬塞进去要么字号小到看不清，要么被截断成"批量…"。
 * 动作名超过两个字时用它，一眼就知道点下去会发生什么。
 */
@Composable
fun TopBarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    enabled: Boolean = true
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isDanger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ShiyeType.caption, color = color)
    }
}

/**
 * 底部四个 Tab。
 *
 * 图标沿用原型字符：▤ 日记 / ◫ 记账 / ✳ 灵感 / ⚙ 设置。
 * 用字符而不是图标资源，好处是深浅色都能自动跟随文字色，不用准备两套 drawable。
 */
@Composable
fun ShiyeBottomBar(
    currentTab: Navigator.Tab,
    onSelectTab: (Navigator.Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Navigator.Tab.entries.forEach { tab ->
                TabItem(
                    icon = tab.icon(),
                    label = tab.label(),
                    selected = tab == currentTab,
                    onClick = { onSelectTab(tab) }
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = icon, style = ShiyeType.cardTitle, color = color)
        Text(text = label, style = ShiyeType.caption, color = color)
    }
}

// Tab 的图标与文案集中在这里，改文案/换图标只动这一处
private fun Navigator.Tab.icon(): String = when (this) {
    Navigator.Tab.DIARY -> "▤"
    Navigator.Tab.LEDGER -> "◫"
    Navigator.Tab.INSPIRATION -> "✳"
    Navigator.Tab.SETTINGS -> "⚙"
}

private fun Navigator.Tab.label(): String = when (this) {
    Navigator.Tab.DIARY -> "日记"
    Navigator.Tab.LEDGER -> "记账"
    Navigator.Tab.INSPIRATION -> "灵感"
    Navigator.Tab.SETTINGS -> "设置"
}
