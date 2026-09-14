package com.mastercontrol.app.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mastercontrol.app.R
import com.mastercontrol.app.core.ui.component.LoadingState
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McSecretTextField
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.domain.repository.AppLockMode

/**
 * Gate in front of the whole UI.
 *
 * While the app lock is enabled and this process has not been unlocked, nothing
 * but the lock screen is composed — no catalog data, no Telegram state.
 */
@Composable
fun AppLockGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val viewModel: AppLockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    LaunchedEffect(activity, state) {
        val locked = state as? LockState.Locked ?: return@LaunchedEffect
        val host = activity ?: return@LaunchedEffect
        viewModel.onBiometricAvailability(host, locked.mode)
        if (locked.mode != AppLockMode.PIN && !locked.promptVisible && !locked.verifying) {
            viewModel.onShowBiometricPrompt(host)
        }
    }

    when (val current = state) {
        LockState.Checking -> LoadingState(
            message = "Checking the app lock…",
            modifier = modifier.fillMaxSize(),
        )

        LockState.Unlocked -> content()

        is LockState.Locked -> LockScreen(
            state = current,
            modifier = modifier,
            activity = activity,
            onPinChange = viewModel::onPinChange,
            onSubmitPin = viewModel::onSubmitPin,
            onShowBiometricPrompt = { host -> viewModel.onShowBiometricPrompt(host) },
        )
    }
}

@Composable
private fun LockScreen(
    state: LockState.Locked,
    modifier: Modifier = Modifier,
    activity: FragmentActivity? = null,
    onPinChange: (String) -> Unit = {},
    onSubmitPin: () -> Unit = {},
    onShowBiometricPrompt: (FragmentActivity) -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(McDimens.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (state.mode == AppLockMode.PIN) Icons.Filled.Lock else Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(McDimens.IconSizeEmptyState),
            )
            Spacer(Modifier.height(McDimens.SpacingLg))
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(McDimens.SpacingXs))
            Text(
                text = if (state.mode == AppLockMode.PIN) {
                    stringResource(R.string.lock_subtitle_pin)
                } else {
                    stringResource(R.string.lock_subtitle_biometric)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(McDimens.SpacingXl))

            if (state.mode == AppLockMode.PIN) {
                McSecretTextField(
                    value = state.pin,
                    onValueChange = onPinChange,
                    label = "PIN",
                    keyboardType = KeyboardType.NumberPassword,
                    errorText = state.error,
                    enabled = !state.verifying,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(McDimens.SpacingMd))
                Button(
                    onClick = onSubmitPin,
                    enabled = !state.verifying && state.pin.length >= MIN_PIN_LENGTH,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Unlock") }
            } else {
                if (state.error != null) {
                    NoticeBar(message = state.error, tone = McTone.WARNING, icon = Icons.Filled.Lock)
                    Spacer(Modifier.height(McDimens.SpacingMd))
                }
                if (state.biometricAvailable && activity != null) {
                    Button(
                        onClick = { onShowBiometricPrompt(activity) },
                        enabled = !state.promptVisible,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(McDimens.SpacingXs))
                        Text(if (state.promptVisible) "Waiting for your device" else "Unlock with device security")
                    }
                } else {
                    NoticeBar(
                        message = "This device cannot unlock Master Control with ${
                            if (state.mode == AppLockMode.BIOMETRIC) "biometrics" else "the device screen lock"
                        }. Turn off the app lock in Settings, or set a PIN, to regain access.",
                        tone = McTone.DANGER,
                        icon = Icons.Filled.Lock,
                    )
                }
            }

            if (state.failedAttempts > 0) {
                Spacer(Modifier.height(McDimens.SpacingMd))
                SectionHint("${state.failedAttempts} unsuccessful attempt(s) in this session.")
            }
        }
    }
}

/** Walks the context chain to the hosting [FragmentActivity], if there is one. */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private const val MIN_PIN_LENGTH = 4
