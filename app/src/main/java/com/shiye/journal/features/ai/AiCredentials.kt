package com.shiye.journal.features.ai

/**
 * 凭据（API Key）的清洗。**纯 Kotlin，可单测。**
 *
 * 为什么要单独做这件事：API Key 几乎都是从网页后台复制来的，
 * 而网页那一行文本经常带着首尾换行、制表符或不间断空格。
 * 用户肉眼看不见，但 HTTP 头的值里一旦出现控制字符，
 * OkHttp 会直接抛 IllegalArgumentException —— 请求根本发不出去。
 *
 * 与其让用户学着小心翼翼地复制，不如在入口处替他洗干净。
 */
object AiCredentials {

    /**
     * 洗成一个能安全放进 HTTP 头的字符串。
     *
     * 去掉首尾空白，再剔除所有控制字符（含换行 \n、回车 \r、制表符 \t）。
     * 中间的正常空格保留：万一某个 provider 的 Key 里就是有分隔符也别因此我们改坏。
     *
     * @return 清洗后的字符串；全是脏字符时返回空串
     */
    fun sanitize(raw: String): String = buildString {
        raw.trim().forEach { c ->
            // C0 控制字符（含 \n \r \t）与 DEL 一律丢掉
            val isControl = c.code <= 0x1F || c.code == 0x7F
            if (!isControl) append(c)
        }
    }

    /**
     * 这条凭据里有没有我们不得不替他处理掉的脏字符。
     *
     * 判据是"洗完还跟原来不一样"，而不是"洗完跟 trim 后不一样" ——
     * trim 本身就会吃掉首尾换行，用后者比对检测不出最末尾那个换行，
     * 而那恰恰是从网页复制时最常见的情况。
     */
    fun hasInvisibleJunk(raw: String): Boolean = sanitize(raw) != raw
}
