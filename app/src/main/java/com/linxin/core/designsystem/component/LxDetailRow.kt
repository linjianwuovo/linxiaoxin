package com.linxin.core.designsystem.component

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.HorizontalDivider

@Composable
fun LxDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 72.dp,
    showDivider: Boolean = true,
) {
    if (value.isBlank()) return

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.width(labelWidth),
            )
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f),
            )
        }
    }
}
