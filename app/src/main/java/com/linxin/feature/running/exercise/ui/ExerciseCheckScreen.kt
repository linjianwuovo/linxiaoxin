package com.linxin.feature.running.exercise.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linxin.R
import com.linxin.core.designsystem.component.LxButton
import com.linxin.core.designsystem.component.LxTopBar

@Composable
fun ExerciseCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseCheckViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.background,
        topBar = { LxTopBar(title = stringResource(R.string.title_sport_checkin), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.phase) {
                CheckPhase.POLLING -> {
                    if (uiState.qrContent.isNotBlank()) {
                        val image = remember(uiState.qrContent) { qrImageBitmap(uiState.qrContent) }
                        Image(
                            bitmap = image,
                            contentDescription = stringResource(R.string.ui_044),
                            modifier = Modifier.size(240.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.ui_045),
                        modifier = Modifier.padding(top = 24.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                CheckPhase.SUCCESS -> Text(
                    text = stringResource(R.string.ui_046),
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
                CheckPhase.TIMEOUT -> {
                    Text(
                        text = stringResource(R.string.ui_047),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    LxButton(
                        text = stringResource(R.string.action_retry),
                        onClick = viewModel::retry,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}
