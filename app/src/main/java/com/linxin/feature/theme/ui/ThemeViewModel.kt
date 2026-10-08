package com.linxin.feature.theme.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.core.settings.AppLanguage
import com.linxin.core.settings.BarMaterial
import com.linxin.core.settings.ThemeMode
import com.linxin.core.settings.ThemePrefs
import com.linxin.core.settings.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePrefs: ThemePrefs,
) : ViewModel() {

    val settings: StateFlow<ThemeSettings> = themePrefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettings())

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { themePrefs.setMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { themePrefs.setLanguage(language) }
    }

    fun setBarMaterial(material: BarMaterial) {
        viewModelScope.launch { themePrefs.setBarMaterial(material) }
    }

    fun setAccent(accentArgb: Int) {
        viewModelScope.launch { themePrefs.setAccent(accentArgb) }
    }

    fun setMonet(enabled: Boolean) {
        viewModelScope.launch { themePrefs.setMonet(enabled) }
    }

    fun setGlassBlur(blur: Float) {
        viewModelScope.launch { themePrefs.setGlassBlur(blur) }
    }

    fun setGlassRefraction(refraction: Float) {
        viewModelScope.launch { themePrefs.setGlassRefraction(refraction) }
    }

    fun setGlassDispersion(dispersion: Float) {
        viewModelScope.launch { themePrefs.setGlassDispersion(dispersion) }
    }

    fun setGlassDistortion(distortion: Float) {
        viewModelScope.launch { themePrefs.setGlassDistortion(distortion) }
    }
}
