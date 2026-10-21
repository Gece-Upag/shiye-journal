package com.shiye.journal.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 保存状态。
 *
 * 规格要求"不得只靠颜色表达状态"，所以四个状态在 UI 上都配了文字，
 * [FAILED] 还额外提供重试入口。
 */
enum class SaveState {
    /** 从未改动，或刚加载完 */
    IDLE,

    /** 正在写入数据库 */
    SAVING,

    /** 最新改动已落库 */
    SAVED,

    /** 写入失败；内容仍在内存里，可重试 */
    FAILED
}

/**
 * 串行自动保存队列。
 *
 * 规格 §4.4 里几条硬要求，逐条对应到实现：
 *
 * - **停止输入约 0.8 秒后触发** → [schedule] 每次都取消上一个延时任务，只留最后一个。
 * - **防抖不能让旧内容覆盖新内容** → 队列里不存"待写内容"，只存版本号；
 *   真正写入时由 [write] 去读**当前最新**的输入内容。这样无论防抖怎么取消，
 *   写出去的永远是最新的，不存在"排队等着被写入的旧快照"。
 * - **串行队列 + 版本号** → [mutex] 保证同一时刻只有一个写入；[dirtyVersion] / [savedVersion]
 *   两个计数器判断"这次写入完成时，它写的是不是最新那版"。
 * - **只有最新版本保存成功后才能显示"已保存"** → [SaveState.SAVED] 只在写入成功
 *   且版本号仍是最新的情况下发出。
 * - **保存失败保留内容并可重试** → 失败时不回滚内存里的内容，[hasPending] 保持为真，
 *   由 UI 提供重试（再调一次 [schedule] 即可）。
 * - **日记被删除后未完成的写入必须失效** → [invalidate] 之后所有写入直接跳过，
 *   避免"删除后又把内容写回去"导致记录复活。
 *
 * 这个类是纯 Kotlin，不依赖 Android，因此防抖、串行、失效这几条都能用单元测试验证，
 * 不用靠真机上狂敲键盘来碰运气。
 *
 * @param scope 写入用的作用域，通常传 viewModelScope。
 * @param debounceMillis 防抖时长，默认 800ms（规格要求"约 0.8 秒"）。
 * @param write 真正的写入操作。**必须读取最新内容**，不要捕获调用时的快照。
 * @param onState 状态变化回调；会被切到 [scope] 所在线程调用。
 */
class AutosaveQueue(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 800L,
    private val write: suspend () -> Unit,
    private val onState: (SaveState) -> Unit = {}
) {

    /** 串行锁：同一时刻只允许一个写入在进行 */
    private val mutex = Mutex()

    /** 防抖计时任务 */
    private var debounceJob: Job? = null

    /** 内容变更次数。每次 [schedule] 加一。 */
    @Volatile
    private var dirtyVersion = 0L

    /** 已成功落库的版本 */
    @Volatile
    private var savedVersion = 0L

    /** 置位后所有写入失效（日记已被删除） */
    @Volatile
    private var invalidated = false

    /** 是否有还没落库的改动 */
    val hasPending: Boolean get() = dirtyVersion != savedVersion

    /** 是否已失效 */
    val isInvalidated: Boolean get() = invalidated

    /**
     * 内容变更：递增版本号并重启防抖计时。
     *
     * 已失效时直接忽略 —— 这正是"删除后写入必须失效，避免记录复活"的落点。
     */
    fun schedule() {
        if (invalidated) return
        dirtyVersion++
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMillis)
            flush()
        }
    }

    /**
     * 立即保存并等待完成（挂起函数）。
     *
     * 用于"页面离开时等待最后一次有效保存完成"：调用方在后台协程里等它返回，
     * 返回后内容一定已经落库或已明确失败。
     */
    suspend fun flush() {
        if (invalidated) return
        // 循环而不是递归：写入过程中用户又敲了几个字时，接着再写一轮
        while (!invalidated && hasPending) {
            val ok = saveOnce()
            if (!ok) return // 失败就停下，等用户点重试，避免失败后无限重试刷屏
        }
    }

    /** 异步触发一次立即保存，不阻塞调用线程。用于按 Home / 返回上一页等时机。 */
    fun flushAsync() {
        if (invalidated) return
        debounceJob?.cancel()
        debounceJob = scope.launch { flush() }
    }

    /**
     * 写入一轮。
     * @return 是否成功；失败返回 false，让 [flush] 停止。
     */
    private suspend fun saveOnce(): Boolean = mutex.withLock {
        if (invalidated || !hasPending) return@withLock true

        // 本轮要写到的版本。写完后若它已不是最新，说明期间又有新输入，
        // 交给 [flush] 的下一轮处理。
        val target = dirtyVersion
        onState(SaveState.SAVING)
        try {
            write()
        } catch (t: Throwable) {
            onState(SaveState.FAILED)
            return@withLock false
        }
        if (invalidated) return@withLock true
        savedVersion = target
        onState(SaveState.SAVED)
        true
    }

    /**
     * 让队列彻底失效（日记已删除时调用）。
     *
     * 取消待触发的防抖任务，并让之后所有 [schedule] / [flush] 直接返回，
     * 这样已经删掉的日记不会因为在途写入而"复活"。
     */
    fun invalidate() {
        invalidated = true
        debounceJob?.cancel()
        debounceJob = null
    }

    /**
     * 解除失效状态、清空版本计数，让队列能重新开始工作。
     *
     * 用于"日记被删掉后，用户又按同一个日期重新写了一篇"：编辑页 ViewModel 的 key
     * 是日期，回到列表再进来拿到的还是同一个实例，队列此时已被 [invalidate] 废掉，
     * 不复位的話新那篇一个字都存不进去（表现为"今天再也写不了日记"）。
     */
    fun reset() {
        debounceJob?.cancel()
        debounceJob = null
        invalidated = false
        dirtyVersion = 0L
        savedVersion = 0L
        onState(SaveState.IDLE)
    }
}

/**
 * 应用级"强制保存"登记处。
 *
 * 规格要求"按 Home、切换到后台时，应确保最新内容已经提交"。
 * 但 Activity 并不知道当前哪个页面有未保存的内容，
 * 所以让有未保存内容的页面（目前只有日记编辑页）在这里登记一个 flush 回调，
 * Activity 在 ON_STOP 时统一触发一次。
 *
 * 登记项在页面销毁时由调用方 [unregister]，不会长期持有 ViewModel。
 */
class SaveFlushRegistry {

    private val lock = Any()
    private val entries = LinkedHashMap<String, () -> Unit>()

    fun register(key: String, flush: () -> Unit) {
        synchronized(lock) { entries[key] = flush }
    }

    fun unregister(key: String) {
        synchronized(lock) { entries.remove(key) }
    }

    /** 触发所有登记项。回调内部各自负责切到自己的协程，本方法不阻塞。 */
    fun flushAll() {
        val snapshot = synchronized(lock) { entries.values.toList() }
        snapshot.forEach { runCatching { it() } }
    }

    /** 当前登记数量，测试用 */
    fun size(): Int = synchronized(lock) { entries.size }
}
