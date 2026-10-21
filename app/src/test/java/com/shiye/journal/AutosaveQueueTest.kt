package com.shiye.journal

import com.shiye.journal.domain.AutosaveQueue
import com.shiye.journal.domain.SaveFlushRegistry
import com.shiye.journal.domain.SaveState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动保存队列的行为测试。
 *
 * 这些是规格 §4.4 里的硬要求，靠真机狂敲键盘是验证不了的：
 * 防抖、串行、不回退、失效、失败可重试，全部用虚拟时间跑。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutosaveQueueTest {

    @Test
    fun `防抖期间连续输入只保存一次`() = runTest {
        var current = ""
        val written = mutableListOf<String>()

        val queue = AutosaveQueue(
            scope = this,
            debounceMillis = 800,
            write = { written.add(current) }
        )

        current = "今"
        queue.schedule()
        advanceTimeBy(300)
        current = "今天"
        queue.schedule()
        advanceTimeBy(300)
        current = "今天很"
        queue.schedule()
        advanceTimeBy(5000)

        assertEquals("防抖后只应写入一次", 1, written.size)
        assertEquals("写出去的必须是最新内容", "今天很", written.single())
    }

    @Test
    fun `每次输入都会重置防抖计时`() = runTest {
        var current = ""
        val written = mutableListOf<String>()
        val queue = AutosaveQueue(scope = this, debounceMillis = 800, write = { written.add(current) })

        current = "a"
        queue.schedule()
        repeat(5) {
            advanceTimeBy(700) // 每次都不足 800ms，不该触发
            current += "a"
            queue.schedule()
        }
        assertEquals("未到防抖时长前不应写入", 0, written.size)

        // 注意：advanceTimeBy 的边界是"严格小于"，正好推进 800 不会触发排在 800 上的任务，
        // 所以这里多推一点；这不是实现细节，而是虚拟时间调度器的语义
        advanceTimeBy(1_000)
        assertEquals("最后一次输入后等够防抖时长，应该写入一次", 1, written.size)
    }

    @Test
    fun `保存期间产生的新输入会在同一轮之后继续保存`() = runTest {
        var current = "v1"
        val written = mutableListOf<String>()
        // 防抖设得极大，保证只有显式 flush 才会写，便于精确控制时序
        val queue = AutosaveQueue(
            scope = this,
            debounceMillis = 10_000,
            // write 在开始时取一次值：真实的写库操作也是"读当前内容 → 写"，
            // 模拟出"写入耗时 50ms"这段时间差
            write = { val snapshot = current; delay(50); written.add(snapshot) }
        )

        queue.schedule()
        val job = launch { queue.flush() }
        advanceTimeBy(10) // 第一次写入已经开始，正卡在 delay(50)

        current = "v2"
        queue.schedule() // 保存过程中用户又敲了字

        advanceTimeBy(5_000)
        job.join()

        assertEquals("两次版本都要落库，且顺序是先旧后新", listOf("v1", "v2"), written)
        assertFalse("全部写完后不应还有挂起的改动", queue.hasPending)
    }

    @Test
    fun `只有最新版本保存成功才显示已保存`() = runTest {
        var current = "v1"
        val states = mutableListOf<SaveState>()
        val queue = AutosaveQueue(
            scope = this,
            debounceMillis = 10_000,
            write = { delay(50); if (current == "v1") error("第一次写入失败") },
            onState = { states.add(it) }
        )

        queue.schedule()
        val job = launch { queue.flush() }
        advanceTimeBy(5_000)
        job.join()

        assertTrue("失败时应处于 FAILED 且保留待保存状态", queue.hasPending)
        assertEquals("最后一次状态必须是失败", SaveState.FAILED, states.last())

        // 用户点重试：内容修好后应该重新走通
        current = "v2"
        queue.schedule()
        val retryJob = launch { queue.flush() }
        advanceTimeBy(5_000)
        retryJob.join()

        assertEquals("重试成功后应显示已保存", SaveState.SAVED, states.last())
        assertFalse(queue.hasPending)
    }

    @Test
    fun `日记被删除后未完成的写入全部失效`() = runTest {
        var writeCount = 0
        val queue = AutosaveQueue(
            scope = this,
            debounceMillis = 800,
            write = { writeCount++ }
        )

        queue.schedule()
        advanceTimeBy(300) // 防抖还没到，写操作尚未发生
        queue.invalidate() // 此时日记被删除
        advanceTimeBy(5_000)

        assertEquals("失效后不应再写入", 0, writeCount)

        // 之后再提交也应被忽略，避免记录复活
        queue.schedule()
        advanceTimeBy(5_000)
        assertEquals("失效后新的提交也必须被忽略", 0, writeCount)
        assertTrue(queue.isInvalidated)
    }

    @Test
    fun `reset 之后队列恢复工作`() = runTest {
        var writeCount = 0
        var state: SaveState = SaveState.IDLE
        val queue = AutosaveQueue(
            scope = this,
            debounceMillis = 800,
            write = { writeCount++ },
            onState = { state = it }
        )

        queue.schedule()
        queue.invalidate() // 日记被删除
        advanceTimeBy(5_000)
        assertEquals("失效期间不应写入", 0, writeCount)

        queue.reset() // 同一天重新写了一篇
        assertEquals(false, queue.isInvalidated)
        assertEquals(SaveState.IDLE, state)

        queue.schedule()
        advanceTimeBy(900) // 边界是严格小于，正好推进 800 不会触发排在 800ms 上的任务
        assertEquals("复位后必须能正常保存", 1, writeCount)
        assertEquals(SaveState.SAVED, state)
    }

    @Test
    fun `flushAsync 会立即写入而不等防抖`() = runTest {
        var writeCount = 0
        val queue = AutosaveQueue(scope = this, debounceMillis = 10_000, write = { writeCount++ })

        queue.schedule()
        advanceTimeBy(100)
        assertEquals("防抖未到，还没写", 0, writeCount)

        queue.flushAsync()
        advanceTimeBy(1_000)
        assertEquals("flushAsync 应立刻触发写入", 1, writeCount)
    }

    @Test
    fun `SaveFlushRegistry 登记后可统一触发并可以注销`() = runTest {
        val registry = SaveFlushRegistry()
        var flushed = 0

        registry.register("a") { flushed++ }
        registry.register("b") { flushed++ }
        assertEquals(2, registry.size())

        registry.flushAll()
        assertEquals("所有登记项都应被触发", 2, flushed)

        registry.unregister("a")
        registry.flushAll()
        assertEquals("注销后不应再被触发", 3, flushed)
    }
}
