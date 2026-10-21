package com.shiye.journal

import com.shiye.journal.navigation.Navigator
import com.shiye.journal.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航状态机测试。
 *
 * 覆盖的是自写导航最容易出错、也最影响体验的三点：
 * 1. 每个 Tab 一条独立返回栈，切走再切回来子页面还在；
 * 2. 整个导航状态能序列化再还原 —— 旋转屏幕与进程重建靠的就是它；
 * 3. **连点防重**：同一个入口被点两下只能压一个页面（修过一个真 bug，见下面对应测试）。
 *
 * 关于 3：早先这里没有防重，手指连点「记一笔」会压进两个编辑页，
 * 用户得按两次返回才回得去；修的时候还要注意不能直接比较整个 Route ——
 * 编辑页每次新建都带新的 editorKey，整体比较永远不相等，等于把保护关掉了。
 */
class NavigatorTest {

    @Test
    fun `初始停在日记 Tab 且不可回退`() {
        val nav = Navigator()
        assertEquals(Navigator.Tab.DIARY, nav.currentTab)
        assertEquals(Route.DiaryHome, nav.currentRoute)
        assertFalse(nav.canGoBack)
        // 栈底就该交给系统返回键，不能拦
        assertFalse(nav.pop())
    }

    @Test
    fun `压栈与回退`() {
        val nav = Navigator()
        nav.push(Route.DiarySearch)
        assertTrue(nav.canGoBack)
        assertEquals(Route.DiarySearch, nav.currentRoute)
        assertTrue(nav.pop())
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `切换 Tab 不影响各自的返回栈`() {
        val nav = Navigator()
        nav.push(Route.DiarySearch)

        // 切到记账，此时记账栈里只有根页面
        nav.selectTab(Navigator.Tab.LEDGER)
        assertEquals(Route.LedgerHome, nav.currentRoute)
        assertFalse(nav.canGoBack)

        // 切回日记，之前压入的页面还在
        nav.selectTab(Navigator.Tab.DIARY)
        assertTrue("切回日记后应仍可回退", nav.canGoBack)
    }

    @Test
    fun `popToTabRoot 一次退到根页面`() {
        val nav = Navigator()
        nav.push(Route.DiarySearch)
        nav.push(Route.TemplateList)
        nav.push(Route.DiaryEditor("2026-10-15"))
        assertEquals(4, nav.stacks.getValue(Navigator.Tab.DIARY).size)
        nav.popToTabRoot()
        assertFalse(nav.canGoBack)
        assertEquals(Route.DiaryHome, nav.currentRoute)
    }

    @Test
    fun `replace 不增加栈深度`() {
        val nav = Navigator()
        nav.push(Route.DiarySearch)
        nav.replace(Route.TemplateList)
        assertEquals(2, nav.stacks.getValue(Navigator.Tab.DIARY).size)
        assertEquals(Route.TemplateList, nav.currentRoute)
    }

    // ---------------- 连点防重 ----------------

    @Test
    fun `连点同一个入口只压一个页面`() {
        val nav = Navigator()
        nav.push(Route.RecordEditor(recordId = null, kind = "expense"))
        // 第二次点击：editorKey 是新的，但 dedupeKey 一样，应被忽略
        nav.push(Route.RecordEditor(recordId = null, kind = "expense"))

        assertEquals(2, nav.stacks.getValue(Navigator.Tab.DIARY).size)
        assertTrue(nav.pop())
        assertFalse("只压了一个页面，退回一次就该到底", nav.canGoBack)
    }

    @Test
    fun `退回后再进同一个入口仍然可以`() {
        val nav = Navigator()
        nav.push(Route.RecordEditor(recordId = null, kind = "expense"))
        nav.pop()
        // 栈顶已经不是编辑页了，这次点击必须生效 —— 否则用户就只能记一笔账
        assertTrue(nav.push(Route.RecordEditor(recordId = null, kind = "expense")))
        assertEquals(Route.RecordEditor::class, nav.currentRoute::class)
    }

    @Test
    fun `编辑不同的记录不受防重影响`() {
        val nav = Navigator()
        nav.push(Route.RecordEditor(recordId = "t-1", kind = "expense"))
        nav.push(Route.RecordEditor(recordId = "t-2", kind = "expense"))
        assertEquals(3, nav.stacks.getValue(Navigator.Tab.DIARY).size)
    }

    // ---------------- 编辑页的一次性标识 ----------------

    @Test
    fun `每次新建编辑页的 editorKey 都不同`() {
        // 这条守的是一个真 bug：新建时 recordId 恒为 null，
        // 如果 ViewModel 的 key 用 recordId，第二次新建会拿到上一次那个 ViewModel，
        // 里面还留着"已保存"标记 → 页面一出现就自动返回，看上去像"一天只能记一笔"。
        assertNotEquals(Route.RecordEditor().editorKey, Route.RecordEditor().editorKey)
        assertNotEquals(Route.TemplateEditor().editorKey, Route.TemplateEditor().editorKey)
    }

    @Test
    fun `序列化往返保留 editorKey`() {
        // 旋转屏幕后 ViewModel 靠 editorKey 找回同一个实例，这个值丢了就等于正在编的内容没了
        val nav = Navigator()
        val editor = Route.RecordEditor(recordId = null, kind = "income")
        nav.push(editor)

        val restored = Navigator.deserialize(nav.serialize())
        val route = restored.stacks.getValue(Navigator.Tab.DIARY).last() as Route.RecordEditor
        assertEquals(editor.editorKey, route.editorKey)
        assertEquals("income", route.kind)
    }

    // ---------------- 序列化 ----------------

    @Test
    fun `序列化后能完整还原 Tab 与所有返回栈`() {
        val nav = Navigator()
        nav.push(Route.DiarySearch)
        nav.selectTab(Navigator.Tab.LEDGER)
        nav.push(Route.LedgerStats)
        nav.selectTab(Navigator.Tab.SETTINGS)

        val restored = Navigator.deserialize(nav.serialize())

        assertEquals(Navigator.Tab.SETTINGS, restored.currentTab)
        assertEquals(2, restored.stacks.getValue(Navigator.Tab.DIARY).size)
        assertEquals(2, restored.stacks.getValue(Navigator.Tab.LEDGER).size)
        assertEquals(1, restored.stacks.getValue(Navigator.Tab.INSPIRATION).size)
        assertTrue(restored.stacks.getValue(Navigator.Tab.INSPIRATION).first() is Route.InspirationHome)
    }

    @Test
    fun `损坏的存档退回全新状态而不是崩溃`() {
        val restored = Navigator.deserialize("这不是一段合法的 JSON")
        assertEquals(Navigator.Tab.DIARY, restored.currentTab)
        assertEquals(Route.DiaryHome, restored.currentRoute)

        val empty = Navigator.deserialize(null)
        assertEquals(Navigator.Tab.DIARY, empty.currentTab)
    }
}
