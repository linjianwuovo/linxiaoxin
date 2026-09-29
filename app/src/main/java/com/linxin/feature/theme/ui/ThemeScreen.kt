package com.linxin.feature.theme.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxCardBorder
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSand
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.settings.BarMaterial
import com.linxin.core.settings.ThemeMode
import kotlin.math.roundToInt

@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var showColorPicker by remember { mutableStateOf(false) }
    val monetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = "主题设置", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp),
        ) {
            SectionLabel("主题模式")
            LxCard {
                SegmentedChoice(
                    options = listOf("跟随系统", "浅色", "深色"),
                    selectedIndex = settings.mode.ordinal,
                    modifier = Modifier.padding(14.dp),
                    onSelect = { viewModel.setMode(ThemeMode.values()[it]) },
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionLabel("颜色")
            LxCard {
                if (monetSupported) {
                    SettingRow(
                        icon = Icons.Filled.Image,
                        title = "启用 Monet 颜色",
                        subtitle = "从壁纸自动取色作为主题色",
                        trailing = {
                            Switch(
                                checked = settings.monet,
                                onCheckedChange = { viewModel.setMonet(it) },
                                colors = SwitchDefaults.switchColors(
                                    uncheckedTrackColor = LxSandDeep,
                                ),
                            )
                        },
                    )
                    RowDivider()
                }
                SettingRow(
                    icon = Icons.Filled.Palette,
                    title = "自定义主题色",
                    subtitle = if (monetSupported && settings.monet)
                        "已启用 Monet，手动配色暂不生效"
                    else
                        "取色器任选，影响主色调与氛围光",
                    onClick = { showColorPicker = true },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(LxTerra)
                                .border(1.dp, LxCardBorder, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = LxInkMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionLabel("底栏材质")
            LxCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.BlurOn,
                            contentDescription = null,
                            tint = LxTerra,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = barMaterialDesc(settings.barMaterial),
                            fontSize = 12.sp,
                            color = LxInkMuted,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SegmentedChoice(
                        options = listOf("液态玻璃", "毛玻璃", "纯色"),
                        selectedIndex = settings.barMaterial.ordinal,
                        onSelect = { viewModel.setBarMaterial(BarMaterial.values()[it]) },
                    )
                    if (settings.barMaterial != BarMaterial.SOLID) {
                        GlassSliderRow("模糊度", settings.glassBlur, viewModel::setGlassBlur)
                        if (settings.barMaterial == BarMaterial.LIQUID) {
                            GlassSliderRow("折射度", settings.glassRefraction, viewModel::setGlassRefraction)
                            GlassSliderRow("扭曲度", settings.glassDistortion, viewModel::setGlassDistortion)
                            GlassSliderRow("色散", settings.glassDispersion, viewModel::setGlassDispersion)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "切换后立即生效，并会记住你的选择。",
                fontSize = 12.sp,
                color = LxInkMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initial = settings.accent,
            onDismiss = { showColorPicker = false },
            onConfirm = { argb ->
                viewModel.setAccent(argb)
                if (monetSupported && settings.monet) viewModel.setMonet(false)
                showColorPicker = false
            },
        )
    }
}

private fun barMaterialDesc(material: BarMaterial): String = when (material) {
    BarMaterial.LIQUID -> "背景实时模糊流动，通透感强"
    BarMaterial.FROSTED -> "更重的磨砂质感，朦胧柔和"
    BarMaterial.SOLID -> "不透明实心底栏，最省电"
}

/**
 * 液态玻璃调参滑杆。value 只在本地临时持有，松手才写库——滑杆期间不触发 DataStore，
 * 避免每帧重写导致首页玻璃重建卡顿。
 */
@Composable
private fun GlassSliderRow(
    label: String,
    initial: Float,
    onCommit: (Float) -> Unit,
) {
    var value by remember(initial) { mutableFloatStateOf(initial) }
    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 13.sp, color = LxInk)
        Text(text = "${(value * 100).roundToInt()}%", fontSize = 13.sp, color = LxInkMuted)
    }
    Spacer(Modifier.height(6.dp))
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onCommit(value) },
        valueRange = 0f..1f,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = LxInkMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** 圆角分段选择器：选中项填充卡片色 + 细边框，仿 HyperOS / SukiSU 顶部分段控件。 */
@Composable
private fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LxSand)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MiuixTheme.colorScheme.surface else Color.Transparent)
                    .then(
                        if (selected) Modifier.border(1.dp, LxCardBorder, RoundedCornerShape(12.dp))
                        else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) LxTerra else LxInkMuted,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LxInkMuted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = LxInk,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 12.sp, color = LxInkMuted)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f)),
    )
}
