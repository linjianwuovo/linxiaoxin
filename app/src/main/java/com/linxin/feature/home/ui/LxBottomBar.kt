package com.linxin.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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

data class LxTab(@StringRes val labelRes: Int, val icon: ImageVector)

val LxHomeTabs = listOf(
    LxTab(R.string.tab_home, Icons.Outlined.Home),
    LxTab(R.string.tab_schedule, Icons.Outlined.CalendarMonth),
    LxTab(R.string.tab_news, Icons.Outlined.Campaign),
    LxTab(R.string.tab_profile, Icons.Outlined.Person),
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
    /** 滑块按住时整体放大到多少倍（高、宽、圆角同一个系数），抬手回到 1.0 */
    const val PressScale = 1.18f
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

    // ── 玻璃滑块 ──
    // 选中项底下那块会左右滑的胶囊。
    //
    // 这个指示器以前废过一次，原因写在栏体那段注释里：把栏体那套折射参数原样搬到一块 46dp 高的
    // 小胶囊上，折射带最宽 28dp、位移最大 24dp，两个加起来比胶囊本身还高，整块被折平，
    // 采样一失败就剩"一格死灰"。所以这次按胶囊自己的尺寸重算，不吃设置页那三根折射滑杆，
    // 只跟模糊强度联动；折射带锁在胶囊高度的一半以内，位移给到 7dp。
    var barWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val cellPx: Float = if (tabs.isEmpty()) 0f else barWidthPx.toFloat() / tabs.size
    // 酷安那块的效果不是"占格子 68% 的等宽胶囊"，而是按选中项自己的图标+文字收边，
    // 所以每格内容宽度要实测；没测到之前先按 0.68 格宽兜底，第一帧就有东西可画。
    val contentWidthPx = remember { mutableStateMapOf<Int, Int>() }
    val pillPadPx = with(density) { 14.dp.toPx() }
    fun halfWidthOf(index: Int): Float {
        val content = contentWidthPx[index]?.toFloat() ?: (cellPx * 0.68f)
        return (content + pillPadPx * 2f) / 2f
    }
    // 手指按在栏上哪一格，滑块就实时跟到哪（dragX 是手指的 x，NaN = 没人按着）；
    // 抬手就弹回选中那格的中心。按着不动也算，所以点哪滑块就先滑到哪。
    var dragX by remember { mutableFloatStateOf(Float.NaN) }
    // 一次按压里滑块的大小要钉死在"按下去那一刻那格的内容宽"上。不钉的话：拖过半格
    // 就换页，selectedIndex 一变，宽度立刻换成另一格内容宽，滑块会在手里一跳一跳地变大小
    // （用户反馈的"滑动大小不一致"就是这个）。抬手才交给选中项自己的宽度。
    var pressIndex by remember { mutableIntStateOf(-1) }
    // pointerInput 的 lambda 会跨重组一直活着，直接读 selectedIndex 会读到旧值，
    // 所以走 rememberUpdatedState，手势中途换页也不用重启手势。
    val currentSelected by rememberUpdatedState(selectedIndex)
    val onSelectedNow by rememberUpdatedState(onSelected)
    val dragging = !dragX.isNaN()
    // 按住才鼓，抬手弹回 —— 鼓是"按比例放大"：高、宽、圆角一起乘同一个系数，
    // 形状不变形，宽标签涨的绝对量比窄标签多，这才是酷安那个比例感。
    // grow 单独一根带过冲的弹簧，和下面两条边的 snap 互不干扰：跟手归跟手，尺寸归尺寸。
    val grow by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.40f, stiffness = 520f),
        label = "lxPillGrow",
    )
    val pillScale = 1f + (LxGlass.PressScale - 1f) * grow
    val pillHeightDp = (46f * pillScale).dp
    val pillShape = RoundedCornerShape((16f * pillScale).dp)
    val safeIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val restCenter = (safeIndex + 0.5f) * cellPx
    val sizeIndex = if (dragging && pressIndex >= 0) pressIndex else safeIndex
    val targetHalf = halfWidthOf(sizeIndex) * pillScale
    val center = if (dragging) dragX else restCenter
    val maxBar = barWidthPx.toFloat()
    // 左右两条边各自一根弹簧：左边硬、右边软，赶路的时候两条边一先一后，
    // 胶囊自然被拉长，到位再收回来 —— 这就是酷安那个"橡皮"味道的来源。
    val pillLeft by animateFloatAsState(
        targetValue = (center - targetHalf).coerceIn(0f, maxBar),
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.86f, stiffness = 1500f),
        label = "lxPillLeft",
    )
    val pillRight by animateFloatAsState(
        targetValue = (center + targetHalf).coerceIn(0f, maxBar),
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.52f, stiffness = 480f),
        label = "lxPillRight",
    )
    val pillWidthDp = with(density) { (pillRight - pillLeft).coerceAtLeast(1f).toDp() }
    // 玻璃一直在，按得越实它越"有货"：面色更亮、描边更亮、折射更强、模糊更通透。
    // press 单独夹到 0..1；尺寸那条 grow 保留弹簧过冲，所以抬手能看到一下回弹的闪光。
    // 面色压薄一档：0.30 的白膜把折射折出来的颜色全盖平了，压到 0.24 才看得见里面在弯。
    val press = grow.coerceIn(0f, 1f)
    val pillTint = Color.White.copy(
        alpha = (if (isDark) 0.12f else 0.24f) + 0.10f * press,
    )
    val pillBorder = Color.White.copy(
        alpha = (if (isDark) 0.24f else 0.45f) + 0.25f * press,
    )
    val pillBorderWidth = (1f + 0.5f * press).dp

    Box(modifier = barBody.padding(horizontal = 4.dp)) {
        // 滑块画在 tab 底下，自己不吃点击
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                // 这里不能用 Modifier.offset { IntOffset(...) }：那个 lambda 只在重新测量的时候取值，
                // 滑块的尺寸又不随选中项变，Compose 就不重测，读到的永远是上一帧的位置 ——
                // 真机上表现成"滑块慢一格"（点公告它停在消息那格）。
                // graphicsLayer 的 translationX 在绘制阶段每帧都读，跟着动画走，不会滞后。
                .graphicsLayer { translationX = pillLeft }
                .size(width = pillWidthDp, height = pillHeightDp)
                .clip(pillShape)
                .then(
                    when (material) {
                        BarMaterial.LIQUID -> Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                // 折射要看得见：位移从 7dp 提到 12dp（按住 18dp），色散也抬起来。
                                // 之前"看不到折射"一半原因是他设置里模糊度拉到 0，栏体本身几乎不糊，
                                // 那块又只有 7dp 位移 + 0.5 色散，折出来的东西和没折一样。
                                // 折射带宽度按"胶囊高度的 42%→48%"给，永远不超过它自己的一半。
                                blur((blurDp * (0.75f - 0.22f * press)).dp.toPx())
                                lensWithDispersion(
                                    refractionHeight = (pillHeightDp * (0.42f + 0.06f * press)).toPx(),
                                    refractionAmount = (12f + 6f * press).dp.toPx(),
                                    dispersion = (dispersion.coerceAtLeast(0.85f) + 0.15f * press).coerceAtMost(1f),
                                    depthEffect = true,
                                )
                            },
                            highlight = {
                                Highlight(
                                    width = LxGlass.HighlightWidth * (1.6f + 1.2f * press),
                                    style = HighlightStyle.Default(
                                        angle = LxGlass.HighlightAngle,
                                        falloff = LxGlass.HighlightFalloff,
                                    ),
                                )
                            },
                            onDrawSurface = { drawRect(pillTint) },
                        )
                        BarMaterial.FROSTED -> Modifier
                            .hazeEffect(state = hazeState) {
                                blurRadius = (frostBlurDp * (0.6f - 0.18f * press)).dp
                                noiseFactor = 0.10f
                                backgroundColor = parchment.copy(alpha = 0.34f + 0.16f * press)
                            }
                            .background(pillTint)
                        BarMaterial.SOLID -> Modifier.background(
                            selectedColor.copy(
                                alpha = (if (isDark) 0.22f else 0.12f) + 0.10f * press,
                            ),
                        )
                    },
                )
                .border(pillBorderWidth, pillBorder, pillShape),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { barWidthPx = it.width }
                // Initial pass：父层先看手指，但一个事件都不消费，所以每格自己的 clickable 照旧生效
                // （点按换页归它管），这里只管让滑块跟手；拖的时候页面不动，抬手才切。
                .pointerInput(cellPx, tabs.size) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        var over = -1
                        if (cellPx > 0f) {
                            dragX = down.position.x
                            over = (down.position.x / cellPx).toInt().coerceIn(0, tabs.size - 1)
                            pressIndex = over
                        }
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull()
                            if (change == null || !change.pressed || change.changedToUpIgnoreConsumed()) {
                                // 抬手才换页：整段拖动里页面纹丝不动，松手那下才切到手指最后停的那格。
                                // 纯点一下也会走到这里，和子节点 clickable 落在同一格，重复设一次无害。
                                if (cellPx > 0f && over >= 0) {
                                    val released = (change?.position?.x ?: (over + 0.5f) * cellPx)
                                    val to = (released / cellPx).toInt().coerceIn(0, tabs.size - 1)
                                    if (to != currentSelected) onSelectedNow(to)
                                }
                                dragX = Float.NaN
                                pressIndex = -1
                                break
                            }
                            if (cellPx > 0f) {
                                // 不做 positionChange 判断：dragX 是 mutableFloatStateOf，
                                // 值没变本来就不会重组，每个事件都写一次最省事
                                dragX = change.position.x
                                over = (change.position.x / cellPx).toInt().coerceIn(0, tabs.size - 1)
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
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
                        ) { onSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 这一层只干一件事：把"图标+文字"实际占多宽量出来给滑块用。
                    // 外层那格是等宽的 weight(1f)，量它等于没量。
                    Column(
                        modifier = Modifier
                            .wrapContentWidth()
                            .onSizeChanged { contentWidthPx[index] = it.width },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = if (active) selectedColor else LxInkFaint,
                            modifier = Modifier.size(22.dp),
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
            }
        }
    }
}
