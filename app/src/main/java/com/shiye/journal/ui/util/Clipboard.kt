package com.shiye.journal.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 复制纯文本到剪贴板。
 *
 * 用 ClipData 的纯文本分支。**不自己弹 Toast**：Android 13+ 系统会统一给一个
 * "已复制"的浮层，再弹一次就是重复打扰。
 *
 * @param label 剪贴项的说明（系统浮层上会显示）
 * @return 是否成功（剪贴板服务取不到时返回 false，调用方可据此提示）
 */
fun copyToClipboard(context: Context, text: String, label: String = "文本"): Boolean {
    if (text.isBlank()) return false
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    return true
}
