package com.linxin.core.designsystem.liquid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.max

/**
 * 一颗"完整的水滴"：锚点 [anchorX] 和头 [headX] 两个圆，中间用两条三次贝塞尔接出脖子，
 * 距离越远脖子越细，近到一定程度就并成一个圆。
 *
 * 为什么要自己画：SukiSU 那颗其实只是一格宽的胶囊加横纵挤压，拖出去不会留尾巴，
 * 所以它永远超不出那一格；他要的是"完整的水滴"，形状就得自己拼。几何用社区那份
 * morph 圆角的标准做法：上下两条 cubic，控制点都放在两圆心的中线（midX）上、
 * 纵向各取自己那一端的半径，这样两头之间天然是内凹的脖子，而不是两个圆硬拼。
 *
 * 注意这个 Shape 只给轮廓，填色和折射还是外面那层 drawBackdrop / clip 的事。
 */
internal class LxDropletShape(
    private val anchorX: Float,
    private val headX: Float,
    /** 传 0 表示自动取节点高度的一半 */
    private val centerY: Float,
    private val headRadius: Float,
    /** 锚点那头收缩到多少：1 = 和头一样大（刚起步），越小尾巴越细（快追上） */
    private val anchorScale: Float,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cy = if (centerY > 0f) centerY else size.height / 2f
        val rHead = headRadius.coerceAtLeast(0.5f)
        val rAnchor = (rHead * anchorScale).coerceAtLeast(0.5f)
        val big = max(rHead, rAnchor)
        val dx = headX - anchorX

        // 距离还不到一颗半径：就是一颗圆。硬画贝塞尔会让控制点交叉，画出蝴蝶结
        if (abs(dx) < big * 0.9f) {
            path.addOval(
                Rect(
                    left = minOf(anchorX, headX) - big,
                    top = cy - big,
                    right = maxOf(anchorX, headX) + big,
                    bottom = cy + big,
                ),
            )
            return Outline.Generic(path)
        }

        val midX = (anchorX + headX) / 2f
        path.moveTo(anchorX, cy - rAnchor)
        path.cubicTo(midX, cy - rAnchor, midX, cy - rHead, headX, cy - rHead)
        path.arcTo(Rect(headX - rHead, cy - rHead, headX + rHead, cy + rHead), -90f, 180f, false)
        path.cubicTo(midX, cy + rHead, midX, cy + rAnchor, anchorX, cy + rAnchor)
        path.arcTo(Rect(anchorX - rAnchor, cy - rAnchor, anchorX + rAnchor, cy + rAnchor), 90f, 180f, false)
        path.close()
        return Outline.Generic(path)
    }
}
