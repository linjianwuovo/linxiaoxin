package com.linxin.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.shapes.Capsule
import com.linxin.R
import com.linxin.core.designsystem.liquid.lensWithDispersion
import com.linxin.core.designsystem.theme.LxInkFaint
import com.linxin.core.designsystem.theme.LxParchment
import com.linxin.core.settings.BarMaterial
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一个底栏项。`pageIndex` 是它对应的 HorizontalPager 页号 —— 底栏现在只有四个 tab
 * （中间那颗是「全部服务」按钮，不占页），所以 tab 下标和页号不再是一回事，
 * 公告那一页还在 pager 里，只是从中间那颗进去。
 */
data class LxTab(@StringRes val labelRes: Int, val icon: ImageVector, val pageIndex: Int)

val LxHomeTabs = listOf(
    LxTab(R.string.tab_home, Icons.Outlined.Home, 0),
    LxTab(R.string.tab_schedule, Icons.Outlined.CalendarMonth, 1),
    LxTab(R.string.tab_messages, Icons.Outlined.Forum, 2),
    LxTab(R.string.tab_profile, Icons.Outlined.Person, 4),
)

/**
 * 液态玻璃参数规范。基准 = Kyant0 Backdrop 官方示例（vibrancy + blur 8dp + lens 24/24），
 * 每个量的含义都按库里的定义用，不自创系数：
 * - `refractionHeight`：折射带宽度，只有边缘往里这么宽的区域真的弯曲，超过栏体一半高度就没意义了。
 * - `refractionAmount`：弯曲位移量。真机实测超过 24dp 会把高饱和内容折成实心灰，所以硬上限锁死 24dp。
 * - `chromaticAberration`：色散，边缘出彩虹边，是"苹果味"折射的主要来源。
 * - `depthEffect`：按形状深度调制折射（中心弱、边缘强），避免整块被均匀推糊。
 * 设置页四根滑杆各自映射到一个区间：模糊度 → 模糊半径（液态 2dp..12dp；毛玻璃走 Haze，
 * 6dp..30dp，中间值正好是原先写死的 18dp；两者共用同一个 0..1 值），
 * 折射度 → 折射带宽度（14dp..28dp），扭曲度 → 位移量（0..24dp），色散 → AGSL 的
 * chromaticAberration（0..1，1 = 官方写死值）。扭曲度的右端就是 24dp 上限，往左只会更小。
 * 后三根只对液态有效；毛玻璃只吃模糊度。
 */
private object LxGlass {
    const val BlurMaxDp = 12f
    const val BlurMinDp = 2f
    const val FrostBlurMaxDp = 30f
    const val FrostBlurMinDp = 6f
    const val LensHeightMinDp = 14f
    const val LensHeightMaxDp = 28f
    const val LensAmountMaxDp = 24f
    const val SurfaceAlphaLight = 0.34f
    const val SurfaceAlphaDark = 0.16f
    val HighlightWidth = 0.75.dp
    const val HighlightAngle = 60f
    const val HighlightFalloff = 1.2f
}

/**
 * 首页底栏。LIQUID 材质 = Kyant0 Backdrop 官方用法（用户提供的液态玻璃 demo 的栏体写法）：
 * vibrancy + blur + lens 折射 + 0.40f 面色。
 * 滑块/水滴指示器已整体废弃——真机上的灰块只来自那层叠加在栏内的小玻璃（它的采样区
 * 太窄，折射/饱和处理一旦失败就只剩一块平灰），选中态只用图标+文字的颜色区分。
 */
@Composable
fun LxBottomBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    material: BarMaterial,
    backdrop: Backdrop,
    hazeState: HazeState,
    glassBlur: Float,
    glassRefraction: Float,
    glassDistortion: Float,
    glassDispersion: Float,
    tabs: List<LxTab> = LxHomeTabs,
    onCenterClick: (() -> Unit)? = null,
    centerOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val capsule = Capsule()
    val scheme = MiuixTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    // 玻璃面色：叠在折射上面才读得出"磨砂玻璃"，但太厚就把颜色盖死，所以浅色只给 0.34
    val surfaceAlpha = if (isDark) LxGlass.SurfaceAlphaDark else LxGlass.SurfaceAlphaLight
    val glassTint = Color.White.copy(alpha = surfaceAlpha)
    val blur = glassBlur.coerceIn(0f, 1f)
    val refraction = glassRefraction.coerceIn(0f, 1f)
    val distortion = glassDistortion.coerceIn(0f, 1f)
    val dispersion = glassDispersion.coerceIn(0f, 1f)
    val blurDp = LxGlass.BlurMinDp + (LxGlass.BlurMaxDp - LxGlass.BlurMinDp) * blur
    val frostBlurDp =
        LxGlass.FrostBlurMinDp + (LxGlass.FrostBlurMaxDp - LxGlass.FrostBlurMinDp) * blur
    val lensHeightDp =
        LxGlass.LensHeightMinDp + (LxGlass.LensHeightMaxDp - LxGlass.LensHeightMinDp) * refraction
    val lensAmountDp = LxGlass.LensAmountMaxDp * distortion
    val parchment = LxParchment
    val frostTint = parchment.copy(alpha = 0.58f)

    val frame = modifier
        .navigationBarsPadding()
        .padding(horizontal = 16.dp)
        .padding(bottom = 12.dp)
        .fillMaxWidth()

    val shape = RoundedCornerShape(percent = 50)
    val barShadow = { m: Modifier ->
        m.shadow(
            elevation = 18.dp,
            shape = shape,
            spotColor = Color.Black.copy(alpha = 0.20f),
            ambientColor = Color.Black.copy(alpha = 0.08f),
        )
    }

    val barBody = when (material) {
        BarMaterial.SOLID -> barShadow(frame)
            .clip(shape)
            .background(LxParchment, shape)
            .height(64.dp)
        BarMaterial.FROSTED -> barShadow(frame)
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurRadius = frostBlurDp.dp
                noiseFactor = 0.14f
                backgroundColor = frostTint
            }
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .height(64.dp)
        BarMaterial.LIQUID -> frame
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsule },
                effects = {
                    vibrancy()
                    blur(blurDp.dp.toPx())
                    lensWithDispersion(
                        refractionHeight = lensHeightDp.dp.toPx(),
                        refractionAmount = lensAmountDp.dp.toPx(),
                        dispersion = dispersion,
                        depthEffect = true,
                    )
                },
                highlight = {
                    Highlight(
                        width = LxGlass.HighlightWidth,
                        style = HighlightStyle.Default(
                            angle = LxGlass.HighlightAngle,
                            falloff = LxGlass.HighlightFalloff,
                        ),
                    )
                },
                onDrawSurface = { drawRect(glassTint) },
            )
    }

    val selectedColor = scheme.primary
    // 选中图标下面垫的那块圆角方形，是照酷安底栏加的；只用主色低透明度实色，
    // 不叠第二层玻璃采样 —— 之前那种"栏内再开一块小玻璃"在部分机型上会折成一块死灰。
    val blockTint = selectedColor.copy(alpha = if (isDark) 0.26f else 0.14f)
    val blockShape = RoundedCornerShape(13.dp)
    val centerSize = 52.dp
    val splitAt = (tabs.size + 1) / 2

    @Composable
    fun RowScope.tabItem(index: Int) {
        val tab = tabs[index]
        val active = index == selectedIndex
        val label = stringResource(tab.labelRes)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    // 玻璃栏里绝不能有触摸水波纹：indication 是一层约 10% 黑的灰色圆角矩形，
                    // 叠在折射上就变成"点哪一格哪一格发灰"，且部分机型按下后不会自动清掉。
                    interactionSource = remember(label) { MutableInteractionSource() },
                    indication = null,
                ) { onSelected(index) }
                .alpha(if (active) 1f else 0.70f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(blockShape)
                    .background(if (active) blockTint else Color.Transparent),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = label,
                    tint = if (active) selectedColor else LxInkFaint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) selectedColor else LxInkFaint,
            )
        }
    }

    Row(
        modifier = barBody.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左半 + 中间那颗实心圆 + 右半：中间那颗不翻页，它开「全部服务」菜单
        tabs.take(splitAt).forEachIndexed { i, _ -> tabItem(i) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            if (onCenterClick != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(centerSize)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .clickable(
                            interactionSource = remember("center") { MutableInteractionSource() },
                            indication = null,
                        ) { onCenterClick() },
                ) {
                    Icon(
                        imageVector = if (centerOpen) Icons.Outlined.Close else Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.tab_services),
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                // 开合时那颗加号转 45 度变成叉，比换图标顺眼，也不额外要动画资源
                                rotationZ = if (centerOpen) 45f else 0f
                            },
                    )
                }
            }
        }
        tabs.drop(splitAt).forEachIndexed { i, _ -> tabItem(i + splitAt) }
    }
}
