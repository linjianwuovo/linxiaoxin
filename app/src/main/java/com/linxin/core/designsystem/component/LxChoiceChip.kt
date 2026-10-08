package com.linxin.core.designsystem.component

import com.linxin.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.linxin.core.designsystem.theme.LxCream
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.designsystem.theme.LxTerraSoft

@Composable
fun LxChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    val borderColor = when {
        !enabled -> LxSandDeep.copy(alpha = 0.5f)
        selected -> LxTerra
        else -> LxSandDeep
    }
    val containerColor = when {
        !enabled -> LxCream.copy(alpha = 0.65f)
        selected -> if (isPressed) LxTerraSoft.copy(alpha = 0.85f) else LxTerraSoft
        isPressed -> LxCream.copy(alpha = 0.8f)
        else -> LxCream
    }
    val textColor = when {
        !enabled -> LxInkMuted.copy(alpha = 0.6f)
        selected -> LxTerra
        else -> LxInkMuted
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = textColor,
        )
    }
}
