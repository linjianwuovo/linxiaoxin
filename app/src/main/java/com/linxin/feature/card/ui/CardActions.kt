package com.linxin.feature.card.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import com.linxin.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxDialog
import com.linxin.core.designsystem.component.LxDialogConfirmTone
import com.linxin.core.designsystem.component.LxOutlinedButton
import com.linxin.core.designsystem.component.LxTextField
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class CardAction { NONE, LOST, MODIFY, UNBIND }

/**
 * 校园卡三个写操作入口：挂失 / 改查询密码 / 解绑。
 *
 * 每一个都先弹确认 + 密码框，密码只在内存里活到这次提交，不落盘、不进日志。
 * 协议出处：它 H5 的 axios 拦截器（RSA 加密哪些字段）+ home 页解绑调用（body 只有 password）
 * + i18n 文案（挂失/改密各只有一个或两个密码框）。
 */
@Composable
fun CardActionsSection(viewModel: CardViewModel) {
    var action by remember { mutableStateOf(CardAction.NONE) }
    val busy by viewModel.writeBusy.collectAsStateWithLifecycle()
    val outcome by viewModel.writeOutcome.collectAsStateWithLifecycle()

    LxCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.card_actions),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            LxOutlinedButton(
                text = stringResource(R.string.card_action_lost),
                enabled = !busy,
                onClick = { action = CardAction.LOST },
            )
            LxOutlinedButton(
                text = stringResource(R.string.card_action_modify),
                enabled = !busy,
                onClick = { action = CardAction.MODIFY },
            )
            LxOutlinedButton(
                text = stringResource(R.string.card_action_unbind),
                enabled = !busy,
                onClick = { action = CardAction.UNBIND },
            )
            Text(
                text = stringResource(R.string.card_actions_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = LxInkMuted,
            )
        }
    }

    when (action) {
        CardAction.LOST -> PasswordDialog(
            title = stringResource(R.string.card_action_lost),
            warning = stringResource(R.string.card_lost_warn),
            busy = busy,
            onDismiss = { action = CardAction.NONE },
            onSubmit = { pwd -> viewModel.lostCard(pwd) },
        )
        CardAction.UNBIND -> PasswordDialog(
            title = stringResource(R.string.card_action_unbind),
            warning = stringResource(R.string.card_unbind_warn),
            busy = busy,
            onDismiss = { action = CardAction.NONE },
            onSubmit = { pwd -> viewModel.unbind(pwd) },
        )
        CardAction.MODIFY -> ModifyPasswordDialog(
            busy = busy,
            onDismiss = { action = CardAction.NONE },
            onSubmit = { old, new -> viewModel.modifyPassword(old, new) },
        )
        CardAction.NONE -> Unit
    }

    outcome?.let { result ->
        LxDialog(
            title = stringResource(if (result.ok) R.string.card_write_ok else R.string.card_write_fail),
            message = result.text,
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = { viewModel.clearWriteOutcome(); if (result.ok) action = CardAction.NONE },
            onDismissRequest = { viewModel.clearWriteOutcome() },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    warning: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lenError = stringResource(R.string.card_pwd_len)
    LxDialog(
        title = title,
        message = warning,
        confirmText = stringResource(R.string.action_confirm),
        confirmTone = LxDialogConfirmTone.Destructive,
        onConfirm = {
            if (!pwd.matches(Regex("^\\d{6}$"))) {
                error = lenError
            } else {
                error = null
                onSubmit(pwd)
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LxTextField(
                value = pwd,
                onValueChange = { pwd = it },
                label = stringResource(R.string.card_pwd),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = it, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
            }
            if (busy) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.card_write_busy),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
            }
        }
    }
}

@Composable
private fun ModifyPasswordDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lenError = stringResource(R.string.card_pwd_len)
    val mismatchError = stringResource(R.string.card_pwd_mismatch)
    val sameError = stringResource(R.string.card_pwd_same)
    LxDialog(
        title = stringResource(R.string.card_action_modify),
        message = stringResource(R.string.card_modify_warn),
        confirmText = stringResource(R.string.action_confirm),
        confirmTone = LxDialogConfirmTone.Destructive,
        onConfirm = {
            when {
                !old.matches(Regex("^\\d{6}$")) || !new.matches(Regex("^\\d{6}$")) ->
                    error = lenError
                new != confirm -> error = mismatchError
                old == new -> error = sameError
                else -> {
                    error = null
                    onSubmit(old, new)
                }
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PwdField(stringResource(R.string.card_pwd_old)) { old = it }
            Spacer(modifier = Modifier.height(8.dp))
            PwdField(stringResource(R.string.card_pwd_new)) { new = it }
            Spacer(modifier = Modifier.height(8.dp))
            PwdField(stringResource(R.string.card_pwd_confirm)) { confirm = it }
            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = it, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
            }
            if (busy) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.card_write_busy),
                    style = MiuixTheme.textStyles.footnote2,
                    color = LxInkMuted,
                )
            }
        }
    }
}

@Composable
private fun PwdField(label: String, onChange: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    LxTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        label = label,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
