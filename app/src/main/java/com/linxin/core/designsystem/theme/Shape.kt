package com.linxin.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// 圆角层级（原型三档）
val RSm = 8.dp    // 小方块图标容器 / 分类小标签
val RMd = 12.dp   // 输入框 / 中型卡片
val RLg = 16.dp   // 主卡片 / 大按钮 / 插画框
val RPill = 20.dp // 胶囊（Badge / 周选择器 chip）

// Miuix 主题不带 shapes，这里提供等价的圆角令牌供各屏引用。
object LxShapes {
    val small = RoundedCornerShape(RMd)
    val medium = RoundedCornerShape(RLg)
    val large = RoundedCornerShape(RLg)
    val pill = RoundedCornerShape(RPill)
}
