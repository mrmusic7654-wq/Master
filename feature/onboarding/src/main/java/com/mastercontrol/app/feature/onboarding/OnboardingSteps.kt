package com.mastercontrol.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mastercontrol.app.core.ui.component.CopyValueRow
import com.mastercontrol.app.core.ui.component.DataRow
import com.mastercontrol.app.core.ui.component.EmptyState
import com.mastercontrol.app.core.ui.component.McAction
import com.mastercontrol.app.core.ui.component.McConfirmDialog
import com.mastercontrol.app.core.ui.component.McDimens
import com.mastercontrol.app.core.ui.component.McSecretTextField
import com.mastercontrol.app.core.ui.component.McTextField
import com.mastercontrol.app.core.ui.component.McTone
import com.mastercontrol.app.core.ui.component.NoticeBar
import com.mastercontrol.app.core.ui.component.PermissionItem
import com.mastercontrol.app.core.ui.component.PermissionList
import com.mastercontrol.app.core.ui.component.SectionCard
import com.mastercontrol.app.core.ui.component.SectionHint
import com.mastercontrol.app.core.ui.component.StatusChip
import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelKind
import com.mastercontrol.app.domain.model.ChannelPermissions
import com.mastercontrol.app.domain.model.CodeDeliveryType
import com.mastercontrol.app.domain.model.StorageChannel

/** Step 2: runtime application credentials (BYOK). Nothing is compiled in. */
@Composable
internal fun CredentialsStep(
    state: OnboardingUiState,
    onApiIdChange: (String) -> Unit,
    onApiHashChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingLg)) {
        SectionCard(
            title = "Telegram application credentials",
            subtitle = "Provided at runtime, stored in the Android Keystore",
            leadingIcon = Icons.Filled.Key,
        ) {
            SectionHint(
                "Create your own application at my.telegram.org → API development tools. " +
                    "api_id and api_hash identify *this application* to Telegram; they are not " +
                    "your account login and they grant no access by themselves. The account " +
                    "session is established in the next step.",
            )
            Spacer(Modifier.height(McDimens.SpacingLg))

            if (state.credentialsConfigured && state.maskedApiHash.isNotEmpty()) {
                DataRow(label = "Stored", value = state.maskedApiHash, monospaced = true)
                Spacer(Modifier.height(McDimens.SpacingSm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(McDimens.SpacingSm))
            }

            McTextField(
                value = state.apiIdInput,
                onValueChange = onApiIdChange,
                label = "API ID",
                supportingText = "Numeric id from my.telegram.org",
                errorText = state.apiIdError,
                keyboardType = KeyboardType.Number,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.Key,
            )
            Spacer(Modifier.height(McDimens.SpacingMd))
            McSecretTextField(
                value = state.apiHashInput,
                onValueChange = onApiHashChange,
                label = "API hash",
                supportingText = "Hexadecimal string (16–64 characters)",
                errorText = state.apiHashError,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                revealDescription = "Show API hash",
                hideDescription = "Hide API hash",
            )
            Spacer(Modifier.height(McDimens.SpacingLg))

            Button(
                onClick = onSave,
                enabled = state.canSaveCredentials && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(McDimens.SpacingSm))
                }
                Text("Save securely and start Telegram")
            }
            Spacer(Modifier.height(McDimens.SpacingSm))
            Row(horizontalArrangement = Arrangement.spacedBy(McDimens.SpacingSm)) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = state.credentialsConfigured && !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Test connection") }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = state.credentialsConfigured && !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(McDimens.SpacingXs))
                    Text("Clear")
                }
            }
        }

        SectionCard(title = "How credentials are handled", leadingIcon = Icons.Filled.Security) {
            BulletLine("Encrypted with an AES-256-GCM key held in the Android Keystore.")
            BulletLine("Never logged, never exported, never included in backups or diagnostics.")
            BulletLine("Replacing them logs the current Telegram session out first.")
            BulletLine("Deleting them removes all locally stored Telegram secrets.")
        }
    }

    if (confirmClear) {
        McConfirmDialog(
            title = "Clear Telegram credentials?",
            message = "The stored API hash is deleted and the Telegram session on this device is " +
                "closed. Your catalog and Telegram media are not affected.",
            confirmLabel = "Clear credentials",
            dismissLabel = "Keep",
            destructive = true,
            acknowledgementLabel = "I understand the session will be closed",
            icon = Icons.Filled.Warning,
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Step 3: Telegram account authorization, driven entirely by TDLib state. */
@Composable
internal fun AuthenticationStep(
    state: OnboardingUiState,
    onPhoneChange: (String) -> Unit,
    onSubmitPhone: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onResendCode: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onEditCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingLg)) {
        when (val auth = state.authorizationState) {
            AuthorizationState.NeedsConfiguration -> EmptyState(
                icon = Icons.Filled.Key,
                title = "Application credentials required",
                message = "Enter your Telegram api_id and api_hash before signing in.",
                actions = listOf(McAction("Enter credentials", onEditCredentials, emphasized = true)),
            )

            AuthorizationState.WaitPhoneNumber -> SectionCard(
                title = "Sign in to Telegram",
                subtitle = "The account that owns your storage channel",
                leadingIcon = Icons.Filled.Phone,
            ) {
                SectionHint(
                    "Use the account that is (or will be) an administrator of the storage channel. " +
                        "Master Control can only access what this account can access.",
                )
                Spacer(Modifier.height(McDimens.SpacingMd))
                McTextField(
                    value = state.phoneInput,
                    onValueChange = onPhoneChange,
                    label = "Phone number",
                    placeholder = "+15551234567",
                    supportingText = "International format, including the country code",
                    errorText = state.phoneError,
                    keyboardType = KeyboardType.Phone,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(McDimens.SpacingLg))
                Button(
                    onClick = onSubmitPhone,
                    enabled = state.phoneInput.isNotBlank() && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send verification code") }
                Spacer(Modifier.height(McDimens.SpacingSm))
                SectionHint("The number is sent to Telegram only; it is never logged or uploaded elsewhere.")
            }

            is AuthorizationState.WaitCode -> SectionCard(
                title = "Verification code",
                subtitle = auth.codeInfo?.let { deliveryDescription(it.type) } ?: "Enter the code Telegram sent you",
                leadingIcon = Icons.Filled.Lock,
            ) {
                McTextField(
                    value = state.codeInput,
                    onValueChange = onCodeChange,
                    label = "Code",
                    supportingText = if (state.codeTimeoutSeconds > 0) {
                        "Another code can be requested in ${state.codeTimeoutSeconds}s"
                    } else {
                        "Digits only; the code is never stored or logged"
                    },
                    errorText = state.codeError,
                    keyboardType = KeyboardType.NumberPassword,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(McDimens.SpacingLg))
                Button(
                    onClick = onSubmitCode,
                    enabled = state.codeInput.length >= 4 && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Verify") }
                TextButton(
                    onClick = onResendCode,
                    enabled = !state.busy && state.codeTimeoutSeconds == 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Resend code") }
            }

            is AuthorizationState.WaitPassword -> SectionCard(
                title = "Two-step verification",
                subtitle = "This account has a cloud password enabled",
                leadingIcon = Icons.Filled.Lock,
            ) {
                if (auth.passwordHint != null) {
                    NoticeBar(
                        message = "Hint: ${auth.passwordHint}",
                        tone = McTone.INFO,
                        icon = Icons.Filled.Warning,
                        modifier = Modifier.padding(bottom = McDimens.SpacingMd),
                    )
                }
                McSecretTextField(
                    value = state.passwordInput,
                    onValueChange = onPasswordChange,
                    label = "Cloud password",
                    supportingText = "Sent to Telegram only; never logged or stored",
                    errorText = state.passwordError,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(McDimens.SpacingLg))
                Button(
                    onClick = onSubmitPassword,
                    enabled = state.passwordInput.isNotEmpty() && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Unlock account") }
            }

            is AuthorizationState.WaitOtherDeviceConfirmation -> SectionCard(
                title = "Confirm on your other device",
                leadingIcon = Icons.Filled.Verified,
            ) {
                SectionHint(
                    "Telegram asked for confirmation on another device." +
                        (auth.link?.let { " Confirmation link: $it" } ?: ""),
                )
            }

            AuthorizationState.WaitRegistration -> UnsupportedAuthState(
                title = "Account not registered",
                message = "This phone number does not have a Telegram account yet. Master Control " +
                    "requires an existing account and does not create new ones.",
                action = McAction("Use another number", onEditCredentials),
            )

            AuthorizationState.WaitEmailAddress, AuthorizationState.WaitEmailCode -> UnsupportedAuthState(
                title = "Email login is not supported",
                message = "Telegram asked for an email-based login. Master Control supports " +
                    "phone-number authorization only; sign in with an account that uses a phone number.",
                action = McAction("Enter credentials", onEditCredentials),
            )

            AuthorizationState.WaitPremiumPurchase -> UnsupportedAuthState(
                title = "Telegram requires a premium subscription",
                message = "Telegram rejected this sign-in because the account requires Telegram " +
                    "Premium. Master Control does not purchase subscriptions.",
                action = McAction("Enter credentials", onEditCredentials),
            )

            is AuthorizationState.Ready -> SectionCard(
                title = "Authorized",
                subtitle = auth.displayName ?: "Telegram account connected",
                leadingIcon = Icons.Filled.CheckCircle,
            ) {
                DataRow(label = "Account", value = auth.displayName ?: "—")
                DataRow(label = "Username", value = auth.username?.let { "@$it" })
                Spacer(Modifier.height(McDimens.SpacingSm))
                SectionHint("Continue to select the storage channel Master Control will upload to.")
            }

            AuthorizationState.LoggingOut -> BusyCard("Signing out of Telegram…")
            AuthorizationState.Closing -> BusyCard("Closing the Telegram engine…")
            AuthorizationState.Closed -> UnsupportedAuthState(
                title = "Telegram engine closed",
                message = "The TDLib client closed. Restart it from the credentials step.",
                action = McAction("Back to credentials", onEditCredentials),
            )

            is AuthorizationState.Other -> UnsupportedAuthState(
                title = "Unhandled Telegram state",
                message = "TDLib reported '${auth.rawType}'. Master Control does not pretend to " +
                    "support a state it has no handling for.",
                action = McAction("Back to credentials", onEditCredentials),
            )
        }
    }
}

@Composable
private fun BusyCard(message: String) {
    SectionCard(title = message, leadingIcon = Icons.Filled.Refresh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(McDimens.SpacingMd))
            Text("Please wait — this state comes directly from TDLib.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UnsupportedAuthState(title: String, message: String, action: McAction) {
    SectionCard(title = title, leadingIcon = Icons.Filled.Warning) {
        SectionHint(message)
        Spacer(Modifier.height(McDimens.SpacingMd))
        OutlinedButton(onClick = action.onClick) { Text(action.label) }
    }
}

private fun deliveryDescription(type: CodeDeliveryType): String = when (type) {
    CodeDeliveryType.TelegramMessage -> "Sent as a Telegram message to your other devices"
    CodeDeliveryType.Sms -> "Sent by SMS"
    CodeDeliveryType.SmsWord -> "Sent by SMS as a word"
    CodeDeliveryType.SmsPhrase -> "Sent by SMS as a phrase"
    CodeDeliveryType.Call -> "Delivered by phone call"
    CodeDeliveryType.MissedCall -> "Enter the last digits of the missed call number"
    CodeDeliveryType.Fragment -> "Sent through Fragment (anonymous numbers)"
    CodeDeliveryType.EmailAddress -> "Sent by email"
    CodeDeliveryType.Firebase -> "Delivered through Firebase push"
    CodeDeliveryType.Unknown -> "Enter the code Telegram sent you"
}

/** Step 4: choose and verify the storage channel. */
@Composable
internal fun ChannelStep(
    state: OnboardingUiState,
    onQueryChange: (String) -> Unit,
    onSelectChannel: (ChannelCandidate, Boolean) -> Unit,
    onReverify: (Long) -> Unit,
    onMakeDefault: (Long) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingLg)) {
        SectionCard(
            title = "Select storage channel",
            subtitle = "Channels this account can post to",
            leadingIcon = Icons.Filled.Search,
            trailing = {
                TextButton(onClick = onRefresh, enabled = !state.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(McDimens.SpacingXs))
                    Text("Refresh")
                }
            },
        ) {
            McTextField(
                value = state.channelQuery,
                onValueChange = onQueryChange,
                label = "Search by name or @username",
                supportingText = "Searches Telegram with the authenticated account",
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.Search,
            )
            if (state.searchingChannels) {
                Spacer(Modifier.height(McDimens.SpacingMd))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(McDimens.SpacingMd))
                    Text("Searching Telegram…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.channelResults.isNotEmpty()) {
                Spacer(Modifier.height(McDimens.SpacingMd))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(modifier = Modifier.height((state.channelResults.size * 104).coerceAtMost(420).dp)) {
                    items(state.channelResults, key = { it.chatId }) { candidate ->
                        ChannelCandidateRow(
                            candidate = candidate,
                            busy = state.busy,
                            onSelect = { onSelectChannel(candidate, makeDefault = true) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else if (!state.searchingChannels && state.channelQuery.isNotBlank()) {
                Spacer(Modifier.height(McDimens.SpacingMd))
                SectionHint("No channel matched that search for this account.")
            }
        }

        if (state.storedChannels.isNotEmpty()) {
            SectionCard(title = "Configured channels", leadingIcon = Icons.Filled.CloudDone) {
                state.storedChannels.forEach { channel ->
                    StoredChannelRow(
                        channel = channel,
                        busy = state.busy,
                        onVerify = { onReverify(channel.id) },
                        onMakeDefault = { onMakeDefault(channel.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else {
            EmptyState(
                icon = Icons.Filled.CloudDone,
                title = "No storage channel yet",
                message = "Search for the channel Master Control should upload to. Its permissions " +
                    "are verified against Telegram before it is marked ready.",
            )
        }

        state.verification?.let { verification ->
            VerificationCard(verification.channel, verification.permissions, verification.reachable, verification.notes)
        }
    }
}

@Composable
private fun ChannelCandidateRow(
    candidate: ChannelCandidate,
    busy: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = McDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(candidate.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = buildString {
                    append(kindLabel(candidate.kind))
                    append(" · id ${candidate.chatId}")
                    candidate.username?.let { append(" · @$it") }
                    candidate.memberCount?.let { append(" · $it members") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = candidate.permissions.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = if (candidate.permissions.canUploadVideos) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.width(McDimens.SpacingSm))
        Button(onClick = onSelect, enabled = !busy) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(McDimens.SpacingXs))
            Text("Select")
        }
    }
}

@Composable
private fun StoredChannelRow(
    channel: StorageChannel,
    busy: Boolean,
    onVerify: () -> Unit,
    onMakeDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = McDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (channel.isDefault) {
                    Spacer(Modifier.width(McDimens.SpacingSm))
                    StatusChip(text = "Default", tone = McTone.SUCCESS, icon = Icons.Filled.Star)
                }
                if (!channel.enabled) {
                    Spacer(Modifier.width(McDimens.SpacingSm))
                    StatusChip(text = "Disabled", tone = McTone.NEUTRAL)
                }
            }
            Text(
                text = "${kindLabel(channel.kind)} · id ${channel.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = channel.permissions.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = if (channel.permissions.canUploadVideos) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.width(McDimens.SpacingSm))
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onVerify, enabled = !busy) { Text("Verify") }
            if (!channel.isDefault) {
                TextButton(onClick = onMakeDefault, enabled = !busy) { Text("Make default") }
            }
        }
    }
}

@Composable
private fun VerificationCard(
    channel: StorageChannel,
    permissions: ChannelPermissions,
    reachable: Boolean,
    notes: List<String>,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = if (reachable && permissions.canUploadVideos) "Connected" else "Not ready",
        subtitle = "Channel: ${channel.displayName}",
        leadingIcon = if (reachable && permissions.canUploadVideos) Icons.Filled.Verified else Icons.Filled.Warning,
        modifier = modifier,
    ) {
        CopyValueRow(label = "Channel ID", value = channel.id.toString())
        DataRow(label = "Username", value = channel.username?.let { "@$it" })
        DataRow(label = "Type", value = kindLabel(channel.kind))
        DataRow(label = "Role", value = when {
            permissions.isCreator -> "Creator"
            permissions.isAdministrator -> "Administrator"
            else -> "Member"
        })
        Spacer(Modifier.height(McDimens.SpacingMd))
        PermissionList(
            items = listOf(
                PermissionItem("Can post messages", permissions.canPostMessages, required = true),
                PermissionItem("Can edit messages", permissions.canEditMessages, required = true),
                PermissionItem("Can delete messages", permissions.canDeleteMessages, required = true),
                PermissionItem("Can pin messages", permissions.canPinMessages, required = false),
                PermissionItem("Can change channel info", permissions.canChangeInfo, required = false),
                PermissionItem("Can invite users", permissions.canInviteUsers, required = false),
            ),
            notes = if (permissions.canUploadVideos) {
                emptyList()
            } else {
                listOf("Missing for uploads: ${permissions.missingForUpload.joinToString(", ")}. " +
                    "Grant these rights to your account in Telegram, then re-verify.")
            },
        )
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(McDimens.SpacingMd))
            notes.forEach { note ->
                SectionHint(note)
            }
        }
    }
}

@Composable
internal fun ReadyStep(state: OnboardingUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(McDimens.SpacingLg)) {
        SectionCard(
            title = "Master Control is ready",
            subtitle = "Catalog, upload, verify and manage — Telegram is the storage layer",
            leadingIcon = Icons.Filled.CheckCircle,
            modifier = modifier,
        ) {
            DataRow(label = "Account", value = state.account?.displayName ?: (state.authorizationState as? AuthorizationState.Ready)?.displayName)
            DataRow(label = "Username", value = state.account?.username?.let { "@$it" })
            val defaultChannel = state.storedChannels.firstOrNull { it.isDefault }
            DataRow(label = "Storage channel", value = defaultChannel?.displayName)
            if (defaultChannel != null) {
                CopyValueRow(label = "Channel ID", value = defaultChannel.id.toString())
                DataRow(label = "Permissions", value = defaultChannel.permissions.describe())
            }
            Spacer(Modifier.height(McDimens.SpacingSm))
            SectionHint("The dashboard opens automatically. Nothing is uploaded until you queue a video.")
        }
    }
}

internal fun kindLabel(kind: ChannelKind): String = when (kind) {
    ChannelKind.CHANNEL -> "Channel"
    ChannelKind.BROADCAST_GROUP -> "Broadcast group"
    ChannelKind.SUPERGROUP -> "Supergroup"
}

@Composable
private fun BulletLine(text: String) {
    Row(Modifier.padding(vertical = McDimens.SpacingXxs)) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(McDimens.SpacingSm))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
