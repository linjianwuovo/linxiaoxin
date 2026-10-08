package com.linxin.core.messages

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息提醒的开关与"已经提醒过什么"。
 *
 * seen 只留最近 [MAX_SEEN] 个 key：够挡住重复提醒，又不会让 DataStore 无限膨胀。
 */
data class MessageSettings(
    val enabled: Boolean = false,
    val news: Boolean = true,
    val checkin: Boolean = true,
    val returnToSchool: Boolean = true,
    val lastRunAt: Long = 0L,
)

private val Context.messagesDataStore: DataStore<Preferences> by preferencesDataStore(name = "linxin_messages")

@Singleton
class MessagePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.messagesDataStore

    val settings: Flow<MessageSettings> = store.data.map { p ->
        MessageSettings(
            enabled = p[KEY_ENABLED] ?: false,
            news = p[KEY_NEWS] ?: true,
            checkin = p[KEY_CHECKIN] ?: true,
            returnToSchool = p[KEY_RETURN] ?: true,
            lastRunAt = p[KEY_LAST_RUN] ?: 0L,
        )
    }

    suspend fun current(): MessageSettings = settings.first()

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[KEY_ENABLED] = value }
    }

    /** tag 取 [MessageTag] 的值，对应各自的开关 */
    suspend fun setSource(tag: String, value: Boolean) {
        val key = when (tag) {
            MessageTag.NEWS -> KEY_NEWS
            MessageTag.CHECKIN -> KEY_CHECKIN
            else -> KEY_RETURN
        }
        store.edit { it[key] = value }
    }

    suspend fun seenKeys(tag: String): Set<String> {
        val raw = store.data.first()[seenKey(tag)] ?: return emptySet()
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    /** 只记"已经提醒过"的，没提醒的（比如发送失败）下次还要再试 */
    suspend fun markSeen(tag: String, keys: Set<String>) {
        if (keys.isEmpty()) return
        val merged = (seenKeys(tag) + keys).toList().takeLast(MAX_SEEN).joinToString(",")
        store.edit { it[seenKey(tag)] = merged }
    }

    /**
     * 已读只能本地记：抓包确认门户没有"标记已读"接口，`readFlag` 是服务端按推送时点给的。
     */
    suspend fun readIds(): Set<String> {
        val raw = store.data.first()[KEY_READ_IDS] ?: return emptySet()
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    suspend fun markRead(id: String) {
        if (id.isBlank()) return
        val merged = (readIds() + id).toList().takeLast(MAX_SEEN).joinToString(",")
        store.edit { it[KEY_READ_IDS] = merged }
    }

    suspend fun markRun() {
        store.edit { it[KEY_LAST_RUN] = System.currentTimeMillis() }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_NEWS = booleanPreferencesKey("src_news")
        val KEY_CHECKIN = booleanPreferencesKey("src_checkin")
        val KEY_RETURN = booleanPreferencesKey("src_return")
        val KEY_LAST_RUN = longPreferencesKey("last_run_at")
        val KEY_READ_IDS = stringPreferencesKey("read_ids")

        val SEEN_NEWS = stringPreferencesKey("seen_news")
        val SEEN_CHECKIN = stringPreferencesKey("seen_checkin")
        val SEEN_RETURN = stringPreferencesKey("seen_return")

        const val MAX_SEEN = 200

        fun seenKey(tag: String) = when (tag) {
            MessageTag.NEWS -> SEEN_NEWS
            MessageTag.CHECKIN -> SEEN_CHECKIN
            else -> SEEN_RETURN
        }
    }
}

object MessageTag {
    const val NEWS = "news"
    const val CHECKIN = "checkin"
    const val RETURN = "return"
}

/** 一条待发送的提醒。[key] 用来去重，[route] 是点进去的导航目的地。 */
data class MessageAlert(
    val tag: String,
    val key: String,
    val title: String,
    val body: String,
    val route: String,
)
