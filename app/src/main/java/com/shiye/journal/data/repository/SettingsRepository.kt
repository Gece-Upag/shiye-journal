package com.shiye.journal.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.shiye.journal.data.model.ThemeMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/**
 * 设置仓库。
 *
 * 用 SharedPreferences 而不是数据库表，理由：
 * - 只有"主题模式""上次备份时间"这类**偏好**，不是业务数据，不该和业务数据一起被备份恢复；
 * - 备份恢复只搬运数据库文件，把偏好留在本地，
 *   这样在另一台机器上恢复数据不会被原来那台机器的主题设置影响。
 *
 * 注意：备份/恢复的 XML 规则已把 sharedpref 排除（见 res/xml/backup_rules.xml）。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 主题模式，默认跟随系统 */
    val themeMode: Flow<ThemeMode> = prefs.observeKey(KEY_THEME_MODE)
        .map { ThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.storageKey)) }

    fun currentThemeMode(): ThemeMode =
        ThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.storageKey))

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.storageKey) }
    }

    /** 上次备份时间，epoch 毫秒；从未备份过为 null */
    val lastBackupAt: Flow<Long?> = prefs.observeKey(KEY_LAST_BACKUP_AT)
        .map {
            val value = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
            if (value == 0L) null else value
        }

    fun currentLastBackupAt(): Long? = prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it != 0L }

    fun setLastBackupAt(millis: Long) {
        prefs.edit { putLong(KEY_LAST_BACKUP_AT, millis) }
    }

    /**
     * 上次停留的灵感页。
     *
     * 存偏好而不是让 ViewModel 自己记着：切了页再关掉 App，下次打开应该还在那一页，
     * 不然每次进来都要重新切一次。
     */
    val lastInspirationPageId: Flow<String> = prefs.observeKey(KEY_LAST_INSPIRATION_PAGE)
        .map { prefs.getString(KEY_LAST_INSPIRATION_PAGE, null) ?: DEFAULT_INSPIRATION_PAGE }

    fun currentLastInspirationPageId(): String =
        prefs.getString(KEY_LAST_INSPIRATION_PAGE, null) ?: DEFAULT_INSPIRATION_PAGE

    fun setLastInspirationPageId(pageId: String) {
        prefs.edit { putString(KEY_LAST_INSPIRATION_PAGE, pageId) }
    }

    companion object {
        private const val PREFS_NAME = "shiye_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
        private const val KEY_LAST_INSPIRATION_PAGE = "last_inspiration_page"

        /** 兜底用的页 id。直接引实体的常量，避免两处写死同一个字符串后漂移 */
        const val DEFAULT_INSPIRATION_PAGE = com.shiye.journal.data.local.entity.InspirationPageEntity.DEFAULT_ID
    }
}

/**
 * 监听某个 key 的变化，变化时发射一次。
 *
 * 不用 OnSharedPreferenceChangeListener 的具体值，而是重新读一次 prefs，
 * 这样即使有多处写入也不会拿到过期数据。
 */
private fun SharedPreferences.observeKey(key: String): Flow<String> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) trySend(changedKey)
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(key)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
