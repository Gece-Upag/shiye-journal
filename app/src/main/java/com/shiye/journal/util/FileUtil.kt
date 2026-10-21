package com.shiye.journal.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 Storage Access Framework 写文本文件。
 *
 * 用 SAF 的 Uri 直接写，而不是申请全盘文件权限 —— 规格 §7.2 明确要求
 * "不申请不必要的全盘文件访问权限"。
 *
 * 统一按 UTF-8 写出：Markdown 里几乎必然有中文，平台默认编码在部分设备上不是 UTF-8。
 *
 * @return true 表示写成功；false 表示失败，调用方据此提示用户而不是假装成功。
 */
suspend fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }
