package com.linxin.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linxin.core.luckin.LuckinMcp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.luckinDataStore: DataStore<Preferences> by preferencesDataStore(name = "linxin_luckin")

/**
 * 瑞幸这摊本地状态：个人 token、上次选的门店、找店用的坐标。
 *
 * token 就是他的瑞幸账号会话凭证，只存应用私有目录，不进仓、不进日志、不进崩溃上报，
 * 界面上也只回显掩码，旁边永远配一颗「清除会话」。
 */
data class LuckinState(
    val token: String = "",
    val deptId: Long = 0L,
    val deptName: String = "",
    val lng: String = DEFAULT_LNG,
    val lat: String = DEFAULT_LAT,
) {
    val configured: Boolean get() = token.isNotBlank()
    val maskedToken: String
        get() = if (token.length <= 8) if (token.isEmpty()) "" else "****"
        else "${token.take(4)}…${token.takeLast(4)}"

    fun point(): Pair<Double, Double>? {
        val a = lng.trim().toDoubleOrNull()
        val b = lat.trim().toDoubleOrNull()
        return if (a != null && b != null) a to b else null
    }

    companion object {
        /** 不给默认坐标：之前写死一个合肥的点，结果人在别处，门店全是错的。定位按钮现取 */
        const val DEFAULT_LNG = ""
        const val DEFAULT_LAT = ""
    }
}

class LuckinPrefs(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("luckin_mcp_token")
        private val KEY_DEPT_ID = longPreferencesKey("luckin_dept_id")
        private val KEY_DEPT_NAME = stringPreferencesKey("luckin_dept_name")
        private val KEY_LNG = stringPreferencesKey("luckin_lng")
        private val KEY_LAT = stringPreferencesKey("luckin_lat")
    }

    val state: Flow<LuckinState> = context.luckinDataStore.data.map { p ->
        LuckinState(
            token = p[KEY_TOKEN].orEmpty(),
            deptId = p[KEY_DEPT_ID] ?: 0L,
            deptName = p[KEY_DEPT_NAME].orEmpty(),
            lng = p[KEY_LNG] ?: LuckinState.DEFAULT_LNG,
            lat = p[KEY_LAT] ?: LuckinState.DEFAULT_LAT,
        )
    }

    suspend fun current(): LuckinState = state.first()

    suspend fun setToken(token: String) {
        context.luckinDataStore.edit { it[KEY_TOKEN] = token.trim() }
    }

    /** 清掉 token，等于把瑞幸会话从这台机器上摘掉 */
    suspend fun clearToken() {
        context.luckinDataStore.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_DEPT_ID)
            it.remove(KEY_DEPT_NAME)
        }
    }

    suspend fun pickShop(shop: LuckinMcp.Shop) {
        context.luckinDataStore.edit {
            it[KEY_DEPT_ID] = shop.deptId
            it[KEY_DEPT_NAME] = shop.deptName
        }
    }

    suspend fun setPoint(lng: String, lat: String) {
        context.luckinDataStore.edit {
            it[KEY_LNG] = lng.trim()
            it[KEY_LAT] = lat.trim()
        }
    }
}
