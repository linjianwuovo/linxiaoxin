package com.linxin.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.linxin.R

// Noto Serif SC 仅 Regular：中文衬线笔画密度高，配 Newsreader Medium 才视觉对齐；
// 两边都 Medium 会让中文"黑一块"。需要强调靠字号，不靠字重。
private val NotoSerifScRegular = Font(R.font.notoserifsc_regular, FontWeight.Normal)
private val NotoSerifScAsMedium = Font(R.font.notoserifsc_regular, FontWeight.Medium)

/**
 * Newsreader 小号光学尺寸（14pt）— 正文级标题（14~18sp）。
 * 字形在小字下更粗、字距更宽，利于卡片标题阅读。
 */
val NewsreaderSmall = FontFamily(
    Font(R.font.newsreader_14_regular, FontWeight.Normal),
    Font(R.font.newsreader_14_medium, FontWeight.Medium),
    NotoSerifScRegular,
    NotoSerifScAsMedium,
)

/**
 * Newsreader 大号光学尺寸（24pt）— 大标题（20~30sp）。
 */
val NewsreaderLarge = FontFamily(
    Font(R.font.newsreader_24_regular, FontWeight.Normal),
    Font(R.font.newsreader_24_medium, FontWeight.Medium),
    NotoSerifScRegular,
    NotoSerifScAsMedium,
)

/**
 * Newsreader 超大光学尺寸（36pt）— 显示字号（30sp 以上）。
 */
val NewsreaderDisplay = FontFamily(
    Font(R.font.newsreader_36_regular, FontWeight.Normal),
    Font(R.font.newsreader_36_medium, FontWeight.Medium),
    NotoSerifScRegular,
    NotoSerifScAsMedium,
)

/**
 * Outfit — 正文、按钮、数字。三字重覆盖全部 sans 场景。
 */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

/** 等宽数字：计时器/成绩列对齐用。 */
val LxTabularNums = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
