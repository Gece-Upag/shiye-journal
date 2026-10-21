package com.shiye.journal.data.repository

import androidx.room.withTransaction
import com.shiye.journal.data.local.AppDatabase
import com.shiye.journal.data.local.entity.InspirationPageEntity
import com.shiye.journal.data.model.Inspiration
import com.shiye.journal.data.model.InspirationPage
import com.shiye.journal.data.model.toDomain
import com.shiye.journal.data.model.toEntity
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 灵感仓库。
 *
 * 两件事：**记下来** 和 **翻到**。加了多灵感页之后多一件：**分开放** ——
 * 每一页的内容彼此隔离，删页连带删掉页里的全部内容（见 [deletePage]）。
 *
 * 刻意不做分类、标签、置顶：灵感的价值是想到就立刻记下，
 * 任何让人停下来思考"这条该放哪"的字段都是负担。
 */
class InspirationRepository(private val database: AppDatabase) {

    private val dao = database.inspirationDao()
    private val pageDao = database.inspirationPageDao()

    // ---------------- 灵感页 ----------------

    /** 全部页，按排序位 */
    fun observePages(): Flow<List<InspirationPage>> =
        pageDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getPage(id: String): InspirationPage? = pageDao.getById(id)?.toDomain()

    /** 新建一页。排序位排在最后一页后面，免得新页插到中间 */
    suspend fun createPage(name: String): InspirationPage {
        val page = InspirationPage(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            sortOrder = pageDao.countAll().toLong(),
            createdAt = DateTimeUtil.nowMillis()
        )
        pageDao.upsert(page.toEntity())
        return page
    }

    suspend fun renamePage(id: String, name: String) {
        pageDao.rename(id, name.trim())
    }

    /**
     * 删除一页，**页里的内容一起删**。
     *
     * 两步必须在同一个事务里：只删页不删灵感，会留下一批永远也看不到的孤儿数据
     * （备份体积照涨，恢复回来照样看不到）。
     */
    suspend fun deletePage(id: String): Int = database.withTransaction {
        val removed = dao.deleteByPage(id)
        pageDao.deleteById(id)
        removed
    }

    /** 首次启动建一个默认页，保证"至少有一页"这个前提永远成立 */
    suspend fun ensureSeeded() {
        if (pageDao.countAll() == 0) {
            pageDao.upsert(
                InspirationPageEntity(
                    id = InspirationPageEntity.DEFAULT_ID,
                    name = InspirationPageEntity.DEFAULT_NAME,
                    sortOrder = 0,
                    createdAt = DateTimeUtil.nowMillis()
                )
            )
        }
    }

    // ---------------- 灵感 ----------------

    /** 全部灵感，新的在最上面 */
    fun observeAll(): Flow<List<Inspiration>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 某一页的灵感，新的在最上面 */
    fun observeByPage(pageId: String): Flow<List<Inspiration>> =
        dao.observeByPage(pageId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Inspiration? = dao.getById(id)?.toDomain()

    /** 新增一条灵感到指定页，返回带 id 的完整对象，UI 可直接用它做后续操作 */
    suspend fun add(content: String, pageId: String): Inspiration {
        val now = DateTimeUtil.nowMillis()
        val entity = Inspiration(
            id = java.util.UUID.randomUUID().toString(),
            content = content.trim(),
            createdAt = now,
            updatedAt = now,
            pageId = pageId
        ).toEntity()
        dao.upsert(entity)
        return entity.toDomain()
    }

    /**
     * 原地编辑内容。
     * [updatedAt] 跟着改，但**不改** [createdAt] ——
     * 灵感列表按创建时间排序，改了创建时间会让这条突然跳到列表顶部。
     */
    suspend fun update(id: String, content: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(existing.copy(content = content.trim(), updatedAt = DateTimeUtil.nowMillis()))
    }

    /** 永久删除，没有回收站 */
    suspend fun delete(id: String) = dao.deleteById(id)

    /** 批量永久删除，返回删掉的条数 */
    suspend fun deleteAll(ids: Collection<String>): Int =
        if (ids.isEmpty()) 0 else dao.deleteByIds(ids.toList())

    /**
     * [pageId] 为空表示跨页搜索全部。
     *
     * 返回 Flow：库里一变就重新发射，"刚记下的一条立刻能被搜到"。
     */
    fun observeSearch(
        keyword: String,
        fromMillis: Long,
        toMillis: Long,
        sort: String,
        pageId: String = ""
    ): Flow<List<Inspiration>> = dao
        .observeSearch(keyword.trim(), fromMillis, toMillis, sort, pageId)
        .map { list -> list.map { it.toDomain() } }

    suspend fun countAll(): Int = dao.countAll()
}
