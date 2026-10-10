package com.linxin.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class BarMaterial { LIQUID, FROSTED, SOLID }

/** 应用内语言。tag 为 null 表示不覆盖，跟随系统（含系统设置里给本应用单独选的语言）。 */
enum class AppLanguage(val tag: String?) {
    FOLLOW_SYSTEM(null),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en"),
}

/** 默认主题色（品牌蓝），与 Color.kt 的 LxTerraRaw 一致。以 ARGB Int 存储。 */
const val DEFAULT_ACCENT_ARGB: Int = 0xFF2563EB.toInt()

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val barMaterial: BarMaterial = BarMaterial.LIQUID,
    val accent: Int = DEFAULT_ACCENT_ARGB,
    /** 启用 Material You（Monet）：从壁纸取色作为主题色，开启时忽略手动 accent。 */
    val monet: Boolean = false,
    /** 应用内语言，默认跟随系统。 */
    val language: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
    /** 底栏模糊度 0..1：液态玻璃映射模糊半径 2dp→12dp，毛玻璃走 Haze 6dp→30dp（同一根滑杆、同一区间两端）。 */
    val glassBlur: Float = 0.5f,
    /** 液态玻璃折射度 0..1：映射到折射带宽度 14dp→28dp。仅作用于 LIQUID 底栏。 */
    val glassRefraction: Float = 0.5f,
    /** 液态玻璃扭曲度 0..1：映射到折射位移量 0→24dp（24dp 是真机实测上限，不可再高）。仅作用于 LIQUID 底栏。 */
    val glassDistortion: Float = 1f,
    /** 液态玻璃色散 0..1：AGSL 的 chromaticAberration 均匀量，0=无色散，1=官方强度。仅作用于 LIQUID 底栏。 */
    val glassDispersion: Float = 1f,
)

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "linxin_theme")

@Singleton
class ThemePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_MODE = stringPreferencesKey("theme_mode")
        private val KEY_BAR_MATERIAL = stringPreferencesKey("bar_material")
        private val KEY_ACCENT = intPreferencesKey("accent_color")
        private val KEY_MONET = booleanPreferencesKey("monet_enabled")
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_GLASS_BLUR = floatPreferencesKey("glass_blur")
        private val KEY_GLASS_REFRACTION = floatPreferencesKey("glass_refraction")
        private val KEY_GLASS_DISPERSION = floatPreferencesKey("glass_dispersion")
        private val KEY_GLASS_DISTORTION = floatPreferencesKey("glass_distortion")
    }

    val settings: Flow<ThemeSettings> = context.themeDataStore.data.map { prefs ->
        ThemeSettings(
            mode = prefs[KEY_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            barMaterial = prefs[KEY_BAR_MATERIAL]
                ?.let { runCatching { BarMaterial.valueOf(it) }.getOrNull() }
                ?: BarMaterial.LIQUID,
            accent = prefs[KEY_ACCENT] ?: DEFAULT_ACCENT_ARGB,
            monet = prefs[KEY_MONET] ?: false,
            language = prefs[KEY_LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.FOLLOW_SYSTEM,
            glassBlur = prefs[KEY_GLASS_BLUR] ?: 0.5f,
            glassRefraction = prefs[KEY_GLASS_REFRACTION] ?: 0.5f,
            glassDistortion = prefs[KEY_GLASS_DISTORTION] ?: 1f,
            glassDispersion = prefs[KEY_GLASS_DISPERSION] ?: 1f,
        )
    }

    suspend fun setMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[KEY_MODE] = mode.name }
    }

    suspend fun setBarMaterial(material: BarMaterial) {
        context.themeDataStore.edit { it[KEY_BAR_MATERIAL] = material.name }
    }

    suspend fun setAccent(accentArgb: Int) {
        context.themeDataStore.edit { it[KEY_ACCENT] = accentArgb }
    }

    suspend fun setMonet(enabled: Boolean) {
        context.themeDataStore.edit { it[KEY_MONET] = enabled }
    }

    /** 冷启动时同步读一次，避免第一帧按默认值组合、读到真值后整树重刷。 */
    suspend fun currentLanguage(): AppLanguage =
        runCatching {
            context.themeDataStore.data.first()[KEY_LANGUAGE]
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
        }.getOrNull() ?: AppLanguage.FOLLOW_SYSTEM

    suspend fun setLanguage(language: AppLanguage) {
        context.themeDataStore.edit { it[KEY_LANGUAGE] = language.name }
    }

    suspend fun setGlassBlur(blur: Float) {
        context.themeDataStore.edit { it[KEY_GLASS_BLUR] = blur.coerceIn(0f, 1f) }
    }

    suspend fun setGlassRefraction(refraction: Float) {
        context.themeDataStore.edit { it[KEY_GLASS_REFRACTION] = refraction.coerceIn(0f, 1f) }
    }

    suspend fun setGlassDispersion(dispersion: Float) {
        context.themeDataStore.edit { it[KEY_GLASS_DISPERSION] = dispersion.coerceIn(0f, 1f) }
    }

    suspend fun setGlassDistortion(distortion: Float) {
        context.themeDataStore.edit { it[KEY_GLASS_DISTORTION] = distortion.coerceIn(0f, 1f) }
    }
}
