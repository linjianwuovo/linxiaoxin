package com.linxin.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.linxin.R
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * MiSans VF —— 全站唯一字体（西文、中文、数字都出自它，衬线大标题一并换掉）。
 *
 * 为什么是一个 ttf 配五份 XML：MiSans VF 是单轴字体，fvar 实测 `wght min 150 /
 * default 330 / max 700`，一份文件覆盖全站用到的全部字重（Light 300 / Normal 400 /
 * Medium 500 / SemiBold 600 / Bold 700，没有一个超过轴上限）。Compose 1.12 的
 * TextStyle 只有 fontFeatureSettings、没有 fontVariationSettings，轴值只能写在
 * `res/font/misans_wN.xml` 里，靠 `android:fontVariationSettings` 锁死，
 * 所以每种字重一个资源 id。
 *
 * `fontVariationSettings` 这个属性在 res/font 里要求较新的平台；低版本上会退化成
 * 轴默认值 330（全站一个粗细，不崩）。目标机 API 36 不受影响。
 *
 * 合规：MiSans 许可协议第 3 条禁止单独分发字体副本，所以 `misans_vf.ttf` 已写进
 * .gitignore 不进公开仓；协议第 1 条要求在软件里注明，见关于页与 MISANS-NOTICE.txt。
 */
val MiSans = FontFamily(
    Font(R.font.misans_w300, FontWeight.Light),
    Font(R.font.misans_w400, FontWeight.Normal),
    Font(R.font.misans_w500, FontWeight.Medium),
    Font(R.font.misans_w600, FontWeight.SemiBold),
    Font(R.font.misans_w700, FontWeight.Bold),
)

private val miuixDefaults = defaultTextStyles()

/**
 * Miuix 的 14 个 textStyles 全量换成 MiSans，字号/行高一律沿用官方值。
 * TextStyles 的字段是 `internal set`，改不了单个，只能整体 copy 重建。
 */
val LxTextStyles = miuixDefaults.copy(
    main = miuixDefaults.main.copy(fontFamily = MiSans),
    paragraph = miuixDefaults.paragraph.copy(fontFamily = MiSans),
    body1 = miuixDefaults.body1.copy(fontFamily = MiSans),
    body2 = miuixDefaults.body2.copy(fontFamily = MiSans),
    button = miuixDefaults.button.copy(fontFamily = MiSans),
    footnote1 = miuixDefaults.footnote1.copy(fontFamily = MiSans),
    footnote2 = miuixDefaults.footnote2.copy(fontFamily = MiSans),
    headline1 = miuixDefaults.headline1.copy(fontFamily = MiSans),
    headline2 = miuixDefaults.headline2.copy(fontFamily = MiSans),
    subtitle = miuixDefaults.subtitle.copy(fontFamily = MiSans),
    title1 = miuixDefaults.title1.copy(fontFamily = MiSans),
    title2 = miuixDefaults.title2.copy(fontFamily = MiSans),
    title3 = miuixDefaults.title3.copy(fontFamily = MiSans),
    title4 = miuixDefaults.title4.copy(fontFamily = MiSans),
)

/** 等宽数字：计时器/成绩列对齐用。 */
val LxTabularNums = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
