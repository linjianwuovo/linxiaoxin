package com.linxin.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linxin.R
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkFaint
import com.linxin.navigation.Routes
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底栏中间那颗「全部服务」按钮打开的菜单。
 *
 * 为什么要有它：底栏改成酷安那种「四个 tab + 中间一颗」之后，公告从底栏掉下来了，
 * 报修 / 请假 / 校园卡这些也一直没有一级入口（要先去「我的 → 更多功能」翻一层）。
 * 中间这颗就是那层的替代：点一下直接到。
 *
 * 公告走 `onAnnouncement`（它还是首页 pager 里的一页，不是独立路由），
 * 其余都是导航到各自的二级页路由。
 */
private data class LxServiceEntry(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val route: String?,
)

@Composable
fun LxServiceMenu(
    onAnnouncement: () -> Unit,
    onRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        LxServiceEntry(R.string.tab_news, Icons.Outlined.Campaign, null),
        LxServiceEntry(R.string.title_repair, Icons.Outlined.Handyman, Routes.REPAIR_LIST),
        LxServiceEntry(R.string.title_leave, Icons.Outlined.EventNote, Routes.LEAVE_LIST),
        LxServiceEntry(R.string.title_card, Icons.Outlined.CreditCard, Routes.CAMPUS_CARD),
        LxServiceEntry(R.string.title_dorm_checkin, Icons.Outlined.HowToReg, Routes.CHECKIN_LIST),
        LxServiceEntry(R.string.title_ai_class, Icons.Outlined.School, Routes.AICLASS_HOME),
        LxServiceEntry(R.string.title_more_functions, Icons.Outlined.MoreHoriz, Routes.MORE_FEATURES),
    )
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = shape)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    ) {
        entries.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { entry ->
                    val label = stringResource(entry.labelRes)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                // 跟底栏一样不吃水波纹，点一下发灰一块不好看
                                interactionSource = remember(entry.route) { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val route = entry.route
                                if (route == null) onAnnouncement() else onRoute(route)
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = label,
                                tint = LxInkFaint,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = LxInk,
                        )
                    }
                }
                // 最后一行不满四个时补空位，不然那几项会被拉宽、跟上面错位
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
