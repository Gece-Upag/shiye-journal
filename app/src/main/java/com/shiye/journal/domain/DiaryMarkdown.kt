package com.shiye.journal.domain

import com.shiye.journal.data.model.DiaryEntry

/**
 * 单篇日记 → Markdown。
 *
 * 格式严格按规格 §7.2 的示例：YAML Front Matter 三个键 + 每个字段一个二级标题。
 *
 * 几个容易踩的坑，都在这里处理掉了：
 * - **YAML 特殊字符**：模板名里出现 `: `、`#`、`"`、`\` 会让 front matter 解析失败，
 *   所以走 [yamlScalar] 做引号转义。
 * - **空字段保留标题**：规格明确要求，空字段也要留下 `## 标签`，不能整段省略。
 * - **换行**：字段内容里的 `\r\n` 统一成 `\n`，避免 Windows/旧数据混进来导致空行错乱。
 * - **编码**：由调用方按 UTF-8 写出，这里只负责生成字符串。
 *
 * 纯函数，可单测；批量导出（阶段 5）复用同一个 [render]，保证单篇与批量格式一致。
 */
object DiaryMarkdown {

    /** 导出文件名：YYYY-MM-DD.md */
    fun fileName(entry: DiaryEntry): String = "${entry.date}.md"

    /**
     * 批量导出的文件名。同一天有多篇时从第 2 篇起加序号（`2026-10-16-2.md`）。
     *
     * 为什么不直接覆盖：文件名由日期决定，同一天的多篇日记互相覆盖会**静默丢日记**，
     * 这和"导出包含所有现存日记"的要求冲突。序号按导出顺序（日期升序）分配，
     * 规则可预测：同一批导出永远得到同样的文件名。
     */
    fun fileNameFor(entry: DiaryEntry, sameDateIndex: Int): String =
        if (sameDateIndex <= 0) "${entry.date}.md" else "${entry.date}-${sameDateIndex + 1}.md"

    /**
     * 生成整篇 Markdown。
     *
     * @param entry 日记；字段顺序由调用方保证（Repository 读出来时已按 sort_order 排好）
     */
    fun render(entry: DiaryEntry): String = buildString {
        append("---\n")
        append("date: ").append(yamlScalar(entry.date)).append('\n')
        append("template: ").append(yamlScalar(entry.templateName)).append('\n')
        append("template_version: ").append(entry.templateVersion).append('\n')
        append("---\n")

        entry.fields.forEach { field ->
            append('\n')
            append("## ").append(field.label).append("\n\n")
            append(normalizeNewlines(field.content))
            append('\n')
        }
    }

    /**
     * YAML 标量转义。
     *
     * 规则保守一些：只要含有 YAML 里有意义的字符、或首尾有空格，就整体加双引号，
     * 内部的 `"` 与 `\` 再转义。宁可多一层引号，也不要让用户自定义的模板名破坏解析。
     */
    fun yamlScalar(value: String): String {
        if (value.isEmpty()) return "\"\""
        val looksLikeOtherType = value in YAML_KEYWORDS ||
            value.startsWith("- ") ||
            value.toLongOrNull() != null
        val needsQuote = looksLikeOtherType ||
            value.any { it in "\"\\#&*!|>%@`\n\r" } ||
            value.contains(':') ||
            value.startsWith(' ') ||
            value.endsWith(' ')
        if (!needsQuote) return value
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        return "\"$escaped\""
    }

    /** 统一换行为 \n，并去掉结尾多余空行（正文结尾的空行没有意义） */
    fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').trimEnd()

    /** 不转义时会被 YAML 解析成布尔/空值的词 */
    private val YAML_KEYWORDS = setOf("true", "false", "null", "~", "yes", "no", "on", "off")
}
