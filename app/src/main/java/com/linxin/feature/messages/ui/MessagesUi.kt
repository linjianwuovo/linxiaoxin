package com.linxin.feature.messages.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.theme.LxInk
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.messages.MessageScheduler
import com.linxin.core.messages.MessageSettings
import com.linxin.core.messages.MessageTag
import com.linxin.core.messages.MessagePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val prefs: MessagePrefs,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<MessageSettings> = prefs.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MessageSettings(),
    )

    /** 关掉总开关时顺手取消周期任务，别让调度器空转 */
    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            prefs.setEnabled(value)
            MessageScheduler.sync(appContext, value)
        }
    }

    fun setSource(tag: String, value: Boolean) {
        viewModelScope.launch { prefs.setSource(tag, value) }
    }

    fun checkNow() {
        MessageScheduler.checkNow(appContext)
    }
}

@Composable
fun MessagesCard(viewModel: MessagesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // 授权对话框回来后没有数据变化可触发重组，用一个自增计数强制再读一次系统权限状态
    var permissionTick by remember { mutableIntStateOf(0) }
    val notificationsAllowed = remember(permissionTick) { notificationsEnabled(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    LxCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.msg_settings_title),
                subtitle = stringResource(R.string.msg_settings_desc),
                checked = settings.enabled,
                onCheckedChange = { on ->
                    viewModel.setEnabled(on)
                    if (on && !notificationsEnabled(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            if (settings.enabled) {
                if (!notificationsAllowed) {
                    Text(
                        text = stringResource(R.string.msg_perm_off),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LxTerra,
                        modifier = Modifier.padding(start = 18.dp, bottom = 10.dp),
                    )
                }
                CheckRow(stringResource(R.string.msg_src_news), settings.news) {
                    viewModel.setSource(MessageTag.NEWS, it)
                }
                CheckRow(stringResource(R.string.msg_src_checkin), settings.checkin) {
                    viewModel.setSource(MessageTag.CHECKIN, it)
                }
                CheckRow(stringResource(R.string.msg_src_return), settings.returnToSchool) {
                    viewModel.setSource(MessageTag.RETURN, it)
                }
                Text(
                    text = stringResource(R.string.msg_check_now),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.SemiBold,
                    color = LxTerra,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.checkNow() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private fun notificationsEnabled(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LxTerra,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium, color = LxInk)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MiuixTheme.textStyles.footnote2, color = LxInkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.switchColors(uncheckedTrackColor = LxSandDeep),
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = LxInkMuted,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.switchColors(uncheckedTrackColor = LxSandDeep),
        )
    }
}
