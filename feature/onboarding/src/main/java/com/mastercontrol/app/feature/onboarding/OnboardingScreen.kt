package com.mastercontrol.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.core.ui.component.McAnimatedVisibility
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McTopBar
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.core.ui.component.McTone

/**
 * First-run flow.
 *
 * [onFinished] is invoked once the account is authorized *and* a verified default
 * storage channel exists — never earlier, so the app cannot show a dashboard that
 * is not actually usable.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.stage) {
        if (state.stage == com.mastercontrol.app.domain.usecase.OnboardingStage.Complete) onFinished()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissMessage()
        }
    }

    OnboardingScreen(
        state = state,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onApiIdChange = viewModel::onApiIdChange,
        onApiHashChange = viewModel::onApiHashChange,
        onSaveCredentials = viewModel::onSaveCredentials,
        onEditCredentials = viewModel::onEditCredentials,
        onClearCredentials = viewModel::onClearCredentials,
        onTestConnection = viewModel::onTestConnection,
        onPhoneChange = viewModel::onPhoneChange,
        onSubmitPhone = viewModel::onSubmitPhone,
        onCodeChange = viewModel::onCodeChange,
        onSubmitCode = viewModel::onSubmitCode,
        onResendCode = viewModel::onResendCode,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmitPassword = viewModel::onSubmitPassword,
        onChannelQueryChange = viewModel::onChannelQueryChange,
        onSelectChannel = viewModel::onSelectChannel,
        onReverifyChannel = viewModel::onReverifyChannel,
        onMakeDefault = viewModel::onMakeDefault,
        onRefreshChannels = viewModel::onRefreshChannels,
        onProceedFromWelcome = viewModel::onProceedFromWelcome,
    )
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onApiIdChange: (String) -> Unit = {},
    onApiHashChange: (String) -> Unit = {},
    onSaveCredentials: () -> Unit = {},
    onEditCredentials: () -> Unit = {},
    onClearCredentials: () -> Unit = {},
    onTestConnection: () -> Unit = {},
    onPhoneChange: (String) -> Unit = {},
    onSubmitPhone: () -> Unit = {},
    onCodeChange: (String) -> Unit = {},
    onSubmitCode: () -> Unit = {},
    onResendCode: () -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onSubmitPassword: () -> Unit = {},
    onChannelQueryChange: (String) -> Unit = {},
    onSelectChannel: (com.mastercontrol.app.domain.model.ChannelCandidate, Boolean) -> Unit = { _, _ -> },
    onReverifyChannel: (Long) -> Unit = {},
    onMakeDefault: (Long) -> Unit = {},
    onRefreshChannels: () -> Unit = {},
    onProceedFromWelcome: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            McTopBar(
                title = "Master Control",
                subtitle = "Telegram media storage console",
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = McDimens.SpacingLg),
        ) {
            StepIndicator(current = state.step)
            Spacer(Modifier.height(McDimens.SpacingLg))

            ConnectionStatusBanner(state)

            McAnimatedVisibility(visible = state.infoMessage != null) {
                state.infoMessage?.let { message ->
                    NoticeBar(
                        message = message,
                        tone = McTone.SUCCESS,
                        icon = Icons.Filled.CheckCircle,
                        modifier = Modifier.padding(bottom = McDimens.SpacingMd),
                    )
                }
            }

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep(onProceed = onProceedFromWelcome)
                OnboardingStep.CREDENTIALS -> CredentialsStep(
                    state = state,
                    onApiIdChange = onApiIdChange,
                    onApiHashChange = onApiHashChange,
                    onSave = onSaveCredentials,
                    onTestConnection = onTestConnection,
                    onClear = onClearCredentials,
                )
                OnboardingStep.AUTHENTICATION -> AuthenticationStep(
                    state = state,
                    onPhoneChange = onPhoneChange,
                    onSubmitPhone = onSubmitPhone,
                    onCodeChange = onCodeChange,
                    onSubmitCode = onSubmitCode,
                    onResendCode = onResendCode,
                    onPasswordChange = onPasswordChange,
                    onSubmitPassword = onSubmitPassword,
                    onEditCredentials = onEditCredentials,
                )
                OnboardingStep.CHANNEL -> ChannelStep(
                    state = state,
                    onQueryChange = onChannelQueryChange,
                    onSelectChannel = onSelectChannel,
                    onReverify = onReverifyChannel,
                    onMakeDefault = onMakeDefault,
                    onRefresh = onRefreshChannels,
                )
                OnboardingStep.COMPLETE -> ReadyStep(state = state)
            }
            Spacer(Modifier.height(McDimens.SpacingXxl))
        }
    }
}

/**
 * Non-interactive progress indicator for the first-run flow. The steps are state,
 * not navigation: tapping one cannot skip a prerequisite, so they are not buttons.
 */
@Composable
private fun StepIndicator(current: OnboardingStep, modifier: Modifier = Modifier) {
    val steps = listOf(
        OnboardingStep.CREDENTIALS to "1. Credentials",
        OnboardingStep.AUTHENTICATION to "2. Sign in",
        OnboardingStep.CHANNEL to "3. Channel",
        OnboardingStep.COMPLETE to "4. Ready",
    )
    val currentIndex = steps.indexOfFirst { it.first == current }
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = McDimens.SpacingMd),
        horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm),
    ) {
        steps.forEachIndexed { index, (_, label) ->
            val tone = when {
                index < currentIndex || current == OnboardingStep.COMPLETE -> McTone.SUCCESS
                index == currentIndex -> McTone.INFO
                else -> McTone.NEUTRAL
            }
            StatusChip(
                text = label,
                tone = tone,
                icon = if (index < currentIndex) Icons.Filled.CheckCircle else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConnectionStatusBanner(state: OnboardingUiState, modifier: Modifier = Modifier) {
    val (label, tone) = connectionLabel(state)
    StatusChip(text = label, tone = tone, modifier = modifier.padding(bottom = McDimens.SpacingMd))
}

private fun connectionLabel(state: OnboardingUiState): Pair<String, McTone> {
    val connection = when (state.connectionState) {
        com.mastercontrol.app.domain.model.ConnectionState.READY -> "Telegram connected"
        com.mastercontrol.app.domain.model.ConnectionState.CONNECTING -> "Connecting to Telegram…"
        com.mastercontrol.app.domain.model.ConnectionState.UPDATING -> "Synchronizing with Telegram…"
        com.mastercontrol.app.domain.model.ConnectionState.WAITING_FOR_NETWORK -> "Waiting for network"
        com.mastercontrol.app.domain.model.ConnectionState.DISCONNECTED -> "Disconnected from Telegram"
        com.mastercontrol.app.domain.model.ConnectionState.UNKNOWN -> "Telegram engine not started"
    }
    val tone = when (state.connectionState) {
        com.mastercontrol.app.domain.model.ConnectionState.READY -> McTone.SUCCESS
        com.mastercontrol.app.domain.model.ConnectionState.CONNECTING,
        com.mastercontrol.app.domain.model.ConnectionState.UPDATING,
        -> McTone.INFO
        com.mastercontrol.app.domain.model.ConnectionState.WAITING_FOR_NETWORK,
        com.mastercontrol.app.domain.model.ConnectionState.DISCONNECTED,
        -> McTone.WARNING
        com.mastercontrol.app.domain.model.ConnectionState.UNKNOWN -> McTone.NEUTRAL
    }
    return connection to tone
}

@Composable
private fun WelcomeStep(onProceed: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(McDimens.SpacingLg).size(McDimens.IconSizeEmptyState),
            )
        }
        Spacer(Modifier.height(McDimens.SpacingLg))
        Text(
            text = "Media asset management,\nwith Telegram as the storage layer",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(McDimens.SpacingMd))
        Text(
            text = "Import video, catalogue it under a permanent Video ID, upload it to a " +
                "channel you control, and keep the mapping verified — so a separate player " +
                "app can stream it later without ever touching this database.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(McDimens.SpacingXl))
        BulletRow(Icons.Filled.CloudUpload, "You provide your own Telegram API credentials at runtime — nothing is baked into the build.")
        BulletRow(Icons.Filled.Security, "Credentials and the TDLib database key are stored in the Android Keystore.")
        BulletRow(Icons.Filled.Key, "Every upload is verified against the real Telegram message before it is marked complete.")
        Spacer(Modifier.height(McDimens.SpacingXl))
        Button(onClick = onProceed, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
        Spacer(Modifier.height(McDimens.SpacingSm))
        SectionHint(
            "Master Control is an independent application built on the open-source TDLib " +
                "Telegram client library. It is not affiliated with, endorsed by, or part of " +
                "Telegram, and it does not use Telegram branding.",
        )
    }
}

@Composable
private fun BulletRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = McDimens.SpacingXs)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(McDimens.SpacingMd))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
