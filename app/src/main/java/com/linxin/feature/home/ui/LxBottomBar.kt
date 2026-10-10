/*
 * 首页液态玻璃底栏。
 *
 * 结构、绘制顺序和全部数值移植自 Kyant0/AndroidLiquidGlass 官方示例组件
 *   app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 *   app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTab.kt
 * 版本 tag 2.0.0（commit bebb11a91b），Copyright 2025 Kyant0，Apache-2.0，
 * 与我们依赖的 io.github.kyant0:backdrop:2.0.0 同源同版本。
 * 拖拽引擎 LxDampedDragAnimation 是 Miuix 官方示例的 Apache-2.0 副本，
 * 出处见 core/designsystem/drag/LxDampedDrag.kt 顶部。
 *
 * 为什么不抄 SukiSU Ultra 那份 FloatingBottomBar：它是 GPL-3.0，抄进来这个 MIT 仓
 * 就得整仓换协议。而它文件第一行自己写着 "Adapted from compose-miuix-ui example
 * (IosLiquidGlassNavigationBar) — Apache 2.0"，所以我们直接取它的 Apache-2.0 上游，
 * 拿到的是同一套做法，法律上干净。
 *
 * 我们相对上游改了这几处，其余照抄：
 * - 标签内容和配色换成我们自己的（Miuix Icon + Text、五个 tab、走 stringResource）；
 * - 栏体那层玻璃吃设置页的四根滑杆（模糊度/折射度/扭曲度/色散），上游是写死的
 *   vibrancy + blur(8dp) + lens(24dp, 24dp)；
 * - 多了 SOLID / FROSTED 两种非玻璃材质分支；
 * - 换页时机：上游是"球先弹到位、再换页"，我们保持"抬手就换页"（他验收过的手感）；
 * - 上游那份 alpha=0 的隐形标签副本我们删掉了，水球只采页面 backdrop（原因见 [LxBottomBar]
 *   的注释：会在球里再画一份标签，和外面那份错开成重影）。
 */
package com.linxin.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.linxin.R
import com.linxin.core.designsystem.drag.LxDampedDragAnimation
import com.linxin.core.designsystem.theme.LxInkFaint
import com.linxin.core.designsystem.theme.LxParchment
import com.linxin.core.settings.BarMaterial
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class LxTab(@StringRes val labelRes: Int, val icon: ImageVector)

val LxHomeTabs = listOf(
    LxTab(R.string.tab_home, Icons.Outlined.Home),
    LxTab(R.string.tab_schedule, Icons.Outlined.CalendarMonth),
    LxTab(R.string.tab_news, Icons.Outlined.Campaign),
    LxTab(R.string.tab_profile, Icons.Outlined.Person),
)

/**
 * 设置页四根滑杆 → 玻璃参数的映射区间。基准仍是上游那组写死值
 * （vibrancy + blur 8dp + lens 24dp/24dp），滑杆中点大致落在那儿：
 * 模糊度 → 模糊半径（液态 2dp..12dp；毛玻璃走 Haze 6dp..30dp，两者共用同一个 0..1 值），
 * 折射度 → 折射带宽度（14dp..28dp），扭曲度 → 位移量（0..24dp），
 * 色散 → lens 的 chromaticAberration 开关（>0 就开，上游那个参数就是布尔）。
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

    /** 栏体按压缩放的量：上游是 lerp(1f, 1f + 16dp / width, pressProgress) */
    val BarPressGrow = 16.dp

    /** 水球：56dp 高、一格宽、Capsule 形；按住鼓到 78dp（上游 pressedScale = 78f/56f） */
    const val PillHeightDp = 56f
    const val PillPressedScale = 78f / 56f

    /** 水球的折射带 / 位移量（上游 10dp·p、14dp·p，色散开） */
    const val PillRefractionHeightDp = 10f
    const val PillRefractionAmountDp = 14f

    /** 水球内阴影（上游 InnerShadow(radius = 8dp·p, alpha = p)） */
    const val PillInnerShadowDp = 8f
}

/**
 * 首页底栏。三个兄弟节点，写下来的顺序就是层级：
 * ① 栏体玻璃底板（只有底板）→ ② 水球 → ③ 那一排标签（画在最上面，永远清晰）。
 *
 * 上游是两层（栏体 Row 里玻璃和标签在一起，球画在最后），我们不能照搬，原因写在第 ① 层
 * 那段注释里：球会把一块不透明的页面画在标签上面，字会被盖没。
 *
 * 三条是踩过才知道的：
 * 1. 缩放只能写在 [drawBackdrop] 的 `layerBlock` 里，不能自己套 `graphicsLayer { scaleX }`。
 *    库在采样背景时会对 layerBlock 做一次逆仿射（LayerBackdrop 的 inverseTransform），
 *    于是玻璃变大而背景比例不变；写在外层就没有那次逆运算，球里全是放大的字 —— 放大镜。
 * 2. 水球必须是栏体的兄弟节点，否则被栏体那层 clip 剪住，鼓不出栏外。
 * 3. 上游那份 alpha=0 的隐形标签副本不能要：它让球里再出现一份标签，和上面那份错开几十
 *    像素成重影（逆仿射的锚点在这层的左上角，不是中心）。
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
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val tabsCount = tabs.size
    val scheme = MiuixTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val surfaceAlpha = if (isDark) LxGlass.SurfaceAlphaDark else LxGlass.SurfaceAlphaLight
    // 上游是 浅色 #FAFAFA·0.4 / 深色 #121212·0.4，我们用羊皮纸色对等替换
    val containerColor = LxParchment.copy(alpha = surfaceAlpha)
    val frostTint = LxParchment.copy(alpha = 0.58f)

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
    val selectedColor = scheme.primary
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    // 采样源就是页面那一层（外层 HomeScaffold 录的 backdrop）。
    // 上游还合了一份"隐形标签副本"进来，我们不能要，原因写在水球那段注释里。

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val barWidthPx = constraints.maxWidth.toFloat()
        // 一格多宽：整条栏减掉左右各 4dp 的内缩再按格数分。水球宽度也是这个。
        val tabWidth = with(density) { barWidthPx - 8f.dp.toPx() } / tabsCount

        // 整条栏跟着手指橡皮条一样偏，最多 4dp，抬手弹回（上游同款）
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, barWidthPx) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / barWidthPx).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val animationScope = rememberCoroutineScope()
        // 注意这里不能写 remember(selectedIndex)：那样外部换页时 currentIndex 会被直接重置成
        // 新值，下面那个 `currentIndex != selectedIndex` 就永远不成立，animateToValue 不会被调用，
        // 球会留在原地（真机上点消息、页面翻了球还在首页）。初值取一次就够了。
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        val onSelectedNow by rememberUpdatedState(onSelected)
        val padPx = with(density) { 4f.dp.toPx() }

        fun indexAt(x: Float): Int {
            if (tabWidth <= 0f) return currentIndex
            // 必须是 floor，不能是 roundToInt：一格的范围是 [padPx + i·tabWidth, +tabWidth)，
            // round 会把格心右侧的每一个点都算到下一格去 —— 首页格中心 (196-12)/264 = 0.697，
            // round 完就是第 1 格，表现成"按住首页水球往右飘"（他 2026-10-10 深夜报的）。
            return ((x - padPx) / tabWidth).toInt().fastCoerceIn(0, tabsCount - 1)
        }

        // 阻尼拖拽引擎（Miuix 官方示例的 Apache-2.0 副本）：value 是"第几格"的连续浮点，
        // pressProgress / scaleX / scaleY 各一根弹簧且阻尼不同（1.0 / 0.6 / 0.7），
        // 按住时横纵不同步地涨，就是水滴被捏一下。拖动途中页面不动，换页只在抬手那一刻。
        val drag = remember(animationScope, tabsCount, density, isLtr, tabWidth) {
            LxDampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = LxGlass.PillPressedScale,
                canDrag = { offset -> offset.x in 0f..barWidthPx },
                onDragStarted = { position -> updateValue(indexAt(position.x).toFloat()) },
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (currentIndex != target) {
                        currentIndex = target
                        onSelectedNow(target)
                    }
                    updateValue(target.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDragCancelled = {
                    updateValue(currentIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    if (tabWidth > 0f && dragAmount.x != 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            )
        }

        // 从外面换页（点按、返回、快捷入口）时把滑块弹到那一格；自己拖出来的已经在那了
        LaunchedEffect(selectedIndex) {
            if (currentIndex != selectedIndex) {
                currentIndex = selectedIndex
                drag.animateToValue(selectedIndex.toFloat())
            }
        }

        // pressProgress 每帧都在变，包成 lambda 传下去，别引起整排格 recompose
        val press = { drag.pressProgress }

        // ── ① 栏体玻璃底板（只有底板，标签不在这里，见下面第 ③ 层）──
        // 为什么把玻璃和标签拆成两层：水球采的是页面那层 backdrop，它会连着一块**不透明的
        // 页面像素**画出来。球画在标签上面的时候（上游就是这个顺序）会把"消息"那格整个盖没
        // —— 米11 上实测到的。上游不会丢字是因为它另外录了一份隐形标签副本再画回玻璃里，
        // 而那份副本和上面清晰的标签错开就是重影。拆成 底板 → 球 → 标签 三层，两个毛病都没有。
        //
        // drawBackdrop 排在 height/fillMaxWidth 之前：玻璃层是 64dp，内容区被最后那个
        // padding(4dp) 收成 56dp，按压缩放时才有 4dp 余量、不会被自己剪到。
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .then(
                    when (material) {
                        BarMaterial.LIQUID -> Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(blurDp.dp.toPx())
                                lens(lensHeightDp.dp.toPx(), lensAmountDp.dp.toPx())
                            },
                            layerBlock = {
                                val scale = lerp(
                                    1f,
                                    1f + LxGlass.BarPressGrow.toPx() / size.width,
                                    press(),
                                )
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        BarMaterial.FROSTED -> Modifier.lxFrost(hazeState, frostBlurDp, frostTint)
                        BarMaterial.SOLID -> Modifier.background(LxParchment, Capsule())
                    },
                )
                .height(64f.dp)
                .fillMaxWidth(),
        )

        // ── ② 水球 ──
        // 位移走 graphicsLayer（绘制阶段每帧读，不会慢一格），形变走 layerBlock（会被逆仿射，
        // 背景不跟着放大）。形状是 Capsule 不是 CircleShape：一格宽、56dp 高、两端半圆，
        // 这才是酷安/SukiSU 那颗；CircleShape 在非正方形上会画成扁椭圆。
        // 阴影/内阴影/高光都是 drawBackdrop 的参数，库自己 clip，不用再加 .clip/.shadow。
        //
        // 采样源只用页面 backdrop，不用上游那份 combined(页面 + 隐形标签副本)：上游只在玻璃里
        // 画一份字，我们按他的要求还要在泡上面压一份清晰的，两层就会重影 —— layerBlock 的逆仿射
        // 锚点在这层的左上角而不是中心，放大 1.39 倍后采到的标签会和上面的错开几十像素。
        Box(
            modifier = Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) drag.value * tabWidth + panelOffset
                        else barWidthPx - (drag.value + 1f) * tabWidth + panelOffset
                }
                .then(
                    when (material) {
                        BarMaterial.LIQUID -> Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                // 上游：球只有 lens，不 blur、不 vibrancy。静止时 p=0
                                // → refractionHeight/Amount <= 0 → lens 自己 return，
                                // 所以 press=0 的观感和改动前一致。
                                val p = press()
                                lens(
                                    LxGlass.PillRefractionHeightDp.dp.toPx() * p,
                                    LxGlass.PillRefractionAmountDp.dp.toPx() * p,
                                    chromaticAberration = dispersion > 0f,
                                )
                            },
                            highlight = { Highlight.Default.copy(alpha = press()) },
                            shadow = { Shadow(alpha = press()) },
                            innerShadow = {
                                InnerShadow(
                                    radius = LxGlass.PillInnerShadowDp.dp * press(),
                                    alpha = press(),
                                )
                            },
                            layerBlock = {
                                scaleX = drag.scaleX
                                scaleY = drag.scaleY
                                val velocity = drag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val p = press()
                                drawRect(
                                    if (!isDark) Color.Black.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - p,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * p))
                            },
                        )
                        BarMaterial.FROSTED -> Modifier
                            .lxFrost(hazeState, frostBlurDp, frostTint)
                            .background(Color.Black.copy(alpha = 0.07f), Capsule())
                        BarMaterial.SOLID -> Modifier.background(
                            selectedColor.copy(alpha = 0.15f),
                            Capsule(),
                        )
                    },
                )
                .height(LxGlass.PillHeightDp.dp)
                .fillMaxWidth(1f / tabsCount),
        )

        // ──  标签层：画在最上面，永远清晰 ──
        // 手势挂在这一层而不是球上。上游是把 pointerInput 挂在 pill 上的，那样只有"按在球上"
        // 才有反馈；他要的是"手按在哪滑块跟着去哪"（原话），所以整条栏收事件。
        // 这个 pointerInput 走 PointerEventPass.Initial 且不消费，所以每格自己的 clickable
        // 照旧管点按。几何和第 ① 层逐字一致（64dp 高 + 4dp 内缩），两层才对得齐。
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .then(drag.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                LxTabCell(
                    tab = tab,
                    active = index == selectedIndex,
                    scale = { if (index == currentIndex) lerp(1f, 1.2f, press()) else 1f },
                    selectedColor = selectedColor,
                    clickable = true,
                    onClick = { onSelected(index) },
                )
            }
        }
    }
}

/** 毛玻璃材质那块底板：Haze 采样 + 羊皮纸面色，圆角和液态一致用 Capsule。 */
private fun Modifier.lxFrost(hazeState: HazeState, frostBlurDp: Float, tint: Color): Modifier =
    this.hazeEffect(state = hazeState) {
        blurRadius = frostBlurDp.dp
        noiseFactor = 0.14f
        backgroundColor = tint
    }

/**
 * 底栏一格：图标 + 文字。[scale] 收 lambda 而不是 Float：pressProgress 每帧都在变，
 * 传裸值会让整排格跟着 recompose。
 */
@Composable
private fun RowScope.LxTabCell(
    tab: LxTab,
    active: Boolean,
    scale: () -> Float,
    selectedColor: Color,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(tab.labelRes)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .then(
                if (clickable) {
                    Modifier.clickable(
                        // 玻璃栏里绝不能有触摸水波纹：indication 是一层约 10% 黑的灰色圆角矩形，
                        // 叠在折射上就变成"点哪一格哪一格发灰"，且部分机型按下后不会自动清掉。
                        interactionSource = remember(label) { MutableInteractionSource() },
                        indication = null,
                    ) { onClick() }
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = label,
            tint = if (active) selectedColor else LxInkFaint,
            modifier = Modifier.width(22.dp).height(22.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) selectedColor else LxInkFaint,
        )
    }
}
