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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.shapes.Capsule
import com.linxin.R
import com.linxin.core.designsystem.drag.LxDampedDragAnimation
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
    /**
     * 水球里那层"被折进来的 tab 文字"有多浓。隐形副本录进 backdrop 时按这个不透明度录，
     * 1.0 = 和屏幕上的字一样实（残影重到能读笔画），0.35 ≈ 只剩一层灰。折射本身不受影响。
     */
    const val GhostAlpha = 0.35f
    /**
     * 按住那颗泡的不透明度。酷安那张实测内部亮度范围 246..252（基本全不透明），
     * 这里留 3% 给边缘那圈折射，免得变成一块纯贴纸。
     */
    const val PressOpacity = 0.97f
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

    // 栏体自己的样式（不含 frame 的外边距）：frame 单独包一层，水球才能做它的兄弟节点，
    // 不被栏体那圈 clip 剪掉，按下去可以鼓出格子和栏的边界
    val barBody = when (material) {
        BarMaterial.SOLID -> barShadow(Modifier)
            .clip(shape)
            .background(LxParchment, shape)
            .height(64.dp)
        BarMaterial.FROSTED -> barShadow(Modifier)
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurRadius = frostBlurDp.dp
                noiseFactor = 0.14f
                backgroundColor = frostTint
            }
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .height(64.dp)
        BarMaterial.LIQUID -> Modifier
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

    // ── 水球滑块 ──
    // 这块 2026-10-10 整体对齐 SukiSU Ultra 的 FloatingBottomBar：一整格宽、56dp 高、CircleShape，
    // 位置 = value * 格宽，静止是一块 10% 平色，按住才长出折射和高光。
    // 它以前废过一次的原因还留着（栏体那套折射参数原样搬到小胶囊上会被折成平灰），
    // 现在水球自己的折射只给 10dp 带 / 14dp 位移，比栏体小得多，安全。
    var barWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val cellPx: Float = if (tabs.isEmpty()) 0f else barWidthPx.toFloat() / tabs.size
    // 滑块的运动整个交给 LxDampedDragAnimation（Miuix 官方示例那份 Apache-2.0 实现，
    // 出处和改动写在 core/designsystem/drag/LxDampedDrag.kt 顶上）：value 是"连续浮点的第几格"
    // 带阻尼弹簧，pressProgress / scaleX / scaleY 各一根弹簧且阻尼不同 —— 按住时横纵不同步地涨，
    // 就是水滴被捏一下；整条栏还跟着手指橡皮条一样最多偏 4dp，抬手弹回。
    // 换页只在抬手那一刻发生（onDragStopped 里），拖动途中页面不动，这是他要的口径。
    val animationScope = rememberCoroutineScope()
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            val total = barWidthPx.toFloat()
            if (total == 0f) 0f else {
                val f = (offsetAnimation.value / total).coerceIn(-1f, 1f)
                rubberBandPx * f.sign * EaseOut.transform(abs(f))
            }
        }
    }
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onSelectedNow by rememberUpdatedState(onSelected)

    fun cellAt(x: Float): Int {
        val total = barWidthPx.toFloat()
        if (total <= 0f || tabs.isEmpty()) return currentIndex
        return (x / (total / tabs.size)).toInt().coerceIn(0, tabs.size - 1)
    }

    val drag = remember(animationScope, tabs.size) {
        LxDampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabs.size - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            // 按住鼓到 90dp：栏才 64dp，所以这颗泡会顶出栏子的上下边界 ——
            // 他给的第二张酷安截图就是这个样子（数码那颗白泡明显凸出栏顶）
            pressedScale = 90f / 56f,
            canDrag = { offset -> offset.x in 0f..barWidthPx.toFloat() },
            onDragStarted = { position -> updateValue(cellAt(position.x).toFloat()) },
            onDragStopped = {
                val target = targetValue.roundToInt().coerceIn(0, tabs.size - 1)
                if (currentIndex != target) {
                    currentIndex = target
                    onSelectedNow(target)
                }
                updateValue(target.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, dragAmount ->
                val cell = barWidthPx.toFloat() / tabs.size
                if (cell > 0f && dragAmount.x != 0f) {
                    updateValue((targetValue + dragAmount.x / cell).coerceIn(0f, (tabs.size - 1).toFloat()))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
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

    // 水球的几何和参数对齐 SukiSU 的 FloatingBottomBar（那份文件是 GPL-3.0，我们只取数值和逻辑，
    // 代码是自己在 Kyant0 Backdrop 上重写的）：一整格宽、56dp 高、CircleShape，
    // 位置 = value * tabWidth，不做居中偏移，也不按内容收边 —— 所以拖动中它永远不会跳大小。
    val press = drag.pressProgress.coerceIn(0f, 1f)
    val pillHeightDp = 56.dp
    val pillWidthDp = with(density) { cellPx.toDp() }
    val pillShape = CircleShape
    // 速度也参与形变：拖起来 scaleX 被压扁、scaleY 被拉长，停手弹簧自己收回 —— 就是"加速变化"
    val squash = drag.velocity / 10f
    val pillScaleX = drag.scaleX / (1f - (squash * 0.75f).coerceIn(-0.2f, 0.2f))
    val pillScaleY = drag.scaleY * (1f - (squash * 0.25f).coerceIn(-0.2f, 0.2f))
    val pillLeft = (
        (drag.value * cellPx + panelOffset)
            .coerceIn(0f, (barWidthPx.toFloat() - cellPx).coerceAtLeast(0f))
        )
    // 静止时它就是一块 10% 的平色（浅色用黑、深色用白），按住把平色淡出、换成折射和高光。
    // 这一层刻意不动：他抱怨的残影只在按住那一态出现，静止那颗 beta31 的样子他没说改。
    val pillRestTint = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.07f)
    // 按住那颗 = 酷安那张实测 #FCFCFC（那颗泡内部亮度范围只有 246..252，等于不透明，
    // 字全在泡上面、泡里面读不出内容）。以前这里是 0.94 的白，剩下 6% 会把页面标题的
    // 笔画透出来，就是他说"那个残影太明显了，调淡一点"要压掉的东西。
    val pillPressTint = if (isDark) Color(0xFF26262A) else Color(0xFFFCFCFC)
    // 水球的采样源 = 页面背景 + 一层看不见的标签副本。少了后半截，玻璃里只会折到页面，
    // 图标和文字不会被"盖"进水滴里（SukiSU 那份就是这么叠的）
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    // 外层只管位置和留白。三个兄弟节点的绘制顺序很关键：
    // 栏体（只当背景）→ 水球 → 标签（画在最上面）。
    // 上一版水球是最后一个节点，按住时那颗近不透明的白泡把图标和文字糊掉了；他给的第二张
    // 酷安截图层次是反过来的 —— 泡鼓出栏顶，绿色的图标和文字仍然清清楚楚压在泡上。
    Box(modifier = frame) {
      Box(modifier = barBody.fillMaxWidth())

      // 水球是栏体的兄弟节点，画在栏体之上、标签之下：不被栏体那圈 clip 剪掉，
      // 所以按下去既能鼓出一格、也能鼓出栏子的上下边界
      Box(
          modifier = Modifier
              .align(Alignment.CenterStart)
              // 这里不能用 Modifier.offset { IntOffset(...) }：那个 lambda 只在重新测量的时候取值，
              // 滑块的尺寸又不随选中项变，Compose 就不重测，读到的永远是上一帧的位置 ——
              // 真机上表现成"滑块慢一格"（点公告它停在消息那格）。
              // graphicsLayer 的 translationX 在绘制阶段每帧都读，跟着动画走，不会滞后。
              .graphicsLayer {
                  translationX = pillLeft
                  scaleX = pillScaleX
                  scaleY = pillScaleY
              }
              .size(width = pillWidthDp, height = pillHeightDp)
              // 按住浮起来：影子跟着按压进度长，这颗泡才像离出栏面。clip = false，
              // 只借它的形状画阴影，别把水球自己剪了
              .shadow(elevation = (2f + 12f * press).dp, shape = pillShape, clip = false)
              .clip(pillShape)
              .then(
                  when (material) {
                      BarMaterial.LIQUID -> Modifier.drawBackdrop(
                          backdrop = combinedBackdrop,
                          shape = { pillShape },
                          effects = {
                              vibrancy()
                              // 液态玻璃，不是放大镜。这颗泡被 graphicsLayer 放大到 90dp，
                              // 采样进来的内容跟着放大 1.6 倍，笔画又没被平色压住，看着就是凸透镜
                              // （他原话"你是放大镜还是液态玻璃"）。所以泡里必须先糊一层：
                              // 静止就吃设置页那根"模糊度"（和栏体同源，0.7 倍），按住再加深 8dp。
                              // 只能透光、不能透字，折射和高光照旧。
                              blur(((blurDp * 0.7f) + 8f * press).dp.toPx())
                              // SukiSU 的水球不带模糊，只有按住才出现的折射：
                              // lens(10dp·p, 14dp·p, depthEffect = true, chromaticAberration = 0.5)。
                              // 我们那份 AGSL 会拿 refractionHeight 做除数，所以留 0.001dp 的地板。
                              lensWithDispersion(
                                  refractionHeight = (10f * press).coerceAtLeast(0.001f).dp.toPx(),
                                  refractionAmount = (14f * press).coerceAtLeast(0.001f).dp.toPx(),
                                  dispersion = 0.5f,
                                  depthEffect = true,
                              )
                          },
                          highlight = {
                              // 它的高光是 alpha = pressProgress，静止等于没有；我们的 Highlight 没有
                              // alpha 通道，就拿宽度当强度，0 宽就是不画。
                              Highlight(
                                  width = LxGlass.HighlightWidth * 2f * press,
                                  style = HighlightStyle.Default(
                                      angle = LxGlass.HighlightAngle,
                                      falloff = LxGlass.HighlightFalloff,
                                  ),
                              )
                          },
                          onDrawSurface = {
                              // 平色是最后一层，压在折射上面。按住那层从 0.94 收到 PressOpacity，
                              // 页面标题的笔画就漏不出来了；边缘的折射和高光仍然照 pressProgress 长。
                              drawRect(pillRestTint, alpha = 1f - press)
                              drawRect(pillPressTint, alpha = press * LxGlass.PressOpacity)
                          },
                      )
                      BarMaterial.FROSTED -> Modifier
                          .hazeEffect(state = hazeState) {
                              blurRadius = (frostBlurDp * 0.6f).dp
                              noiseFactor = 0.10f
                              backgroundColor = parchment.copy(alpha = 0.34f)
                          }
                          .background(pillRestTint)
                      BarMaterial.SOLID -> Modifier.background(
                          selectedColor.copy(alpha = 0.15f),
                      )
                  },
              ),
      )

      // 标签层还是原来那层 4dp 内缩的壳：barWidthPx（一格多宽）和水球起点对齐都靠它，
      // 数值和上一版逐字一致，只是从栏体的子里挪出来成了兄弟，好排在泡上面。
      Box(modifier = Modifier.matchParentSize().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { barWidthPx = it.width }
                // 整条栏跟着手指橡皮条一样偏，最多 4dp，抬手弹回
                .graphicsLayer { translationX = panelOffset }
                // 阻尼动画自己带 pointerInput（Initial pass，不消费事件），
                // 所以每格自己的 clickable 照旧管点按，它只管拖动和水球按压反馈
                .then(drag.modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                LxTabCell(
                    tab = tab,
                    active = index == selectedIndex,
                    scale = if (index == currentIndex) 1f + 0.2f * press else 1f,
                    selectedColor = selectedColor,
                    clickable = true,
                    onClick = { onSelected(index) },
                )
            }
        }

        // 一层看不见的标签副本，专门录进 tabsBackdrop 给水球当采样源。
        // 没有它，水球能折射到的只有页面背景，文字不会被"盖"进玻璃里 —— 他要的就是这个。
        // 注意 alpha 的写法：外层那个 alpha(0f) 只负责让这一排在屏幕上不可见，
        // 里面这层 graphicsLayer 才是"录进 backdrop 的字有多浓"。水球里那层字的残影太明显
        // （2026-10-10 他的原话），所以采样进去的这一层只给 GhostAlpha，折射本身不动。
        Row(
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .clearAndSetSemantics { }
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { alpha = LxGlass.GhostAlpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                LxTabCell(
                    tab = tab,
                    active = index == selectedIndex,
                    scale = if (index == currentIndex) 1f + 0.2f * press else 1f,
                    selectedColor = selectedColor,
                    clickable = false,
                    onClick = {},
                )
            }
        }
      }
    }
}

/**
 * 底栏一格：图标 + 文字。抽出来是因为水球要采样一份一模一样的隐形副本
 * （见 [LxBottomBar] 里那层 `alpha(0f)` 的 Row），不能再抄一遍代码。
 */
@Composable
private fun RowScope.LxTabCell(
    tab: LxTab,
    active: Boolean,
    scale: Float,
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
                scaleX = scale
                scaleY = scale
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
