package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ChannelCandidate
import com.mastercontrol.app.domain.model.ChannelPermissions
import com.mastercontrol.app.domain.model.ChannelVerification
import com.mastercontrol.app.domain.model.LibraryStatistics
import com.mastercontrol.app.domain.model.StorageChannel
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.CategoryRepository
import com.mastercontrol.app.domain.repository.FolderRepository
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** Computes dashboard values from real rows only. */
class GetLibraryStatisticsUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val categoryRepository: CategoryRepository,
    private val folderRepository: FolderRepository,
    private val channelRepository: TelegramChannelRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(): LibraryStatistics {
        val stats = videoRepository.computeStatistics()
        val recent = activityRepository.getRecentSnapshot(10)
        return stats.copy(
            totalCategories = categoryRepository.countCategories(),
            totalFolders = folderRepository.countFolders(),
            totalChannels = channelRepository.observeChannels().first().size,
            recentActivity = recent,
        )
    }
}

/** Channel discovery. */
class SearchChannelsUseCase @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
) {
    suspend operator fun invoke(query: String): List<ChannelCandidate> =
        channelRepository.searchChannels(query.trim())
}

/** Adds a discovered channel as a storage location (local bookkeeping only). */
class AddStorageChannelUseCase @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(candidate: ChannelCandidate, makeDefault: Boolean = false): StorageChannel {
        val stored = channelRepository.addChannel(candidate)
        if (makeDefault) channelRepository.setDefaultChannel(stored.id)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.CHANNEL_ADDED,
                message = "Storage channel '${stored.displayName}' added",
                channelId = stored.id,
                createdAt = Instant.now(),
            ),
        )
        return stored
    }
}

/** Removes a channel from Master Control. Telegram is untouched. */
class RemoveStorageChannelUseCase @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(channelId: Long) {
        channelRepository.removeChannel(channelId)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.CHANNEL_REMOVED,
                message = "Storage channel removed",
                channelId = channelId,
                createdAt = Instant.now(),
            ),
        )
    }
}

class SetDefaultChannelUseCase @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(channelId: Long) {
        channelRepository.setDefaultChannel(channelId)
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.CHANNEL_DEFAULT_CHANGED,
                message = "Default storage channel changed",
                channelId = channelId,
                createdAt = Instant.now(),
            ),
        )
    }
}

class RenameChannelUseCase @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
) {
    suspend operator fun invoke(channelId: Long, localLabel: String?) =
        channelRepository.renameChannel(channelId, localLabel?.takeIf { it.isNotBlank() })
}

/** Re-verifies live permissions and refreshes the stored row. */
class VerifyChannelUseCase @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(channelId: Long): ChannelVerification {
        val result = channelRepository.verifyChannel(channelId)
        if (result.permissions != result.channel.permissions) {
            channelRepository.refreshPermissions(channelId, result.permissions)
        }
        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.CHANNEL_VERIFIED,
                message = "Channel '${result.channel.displayName}' verified",
                details = result.permissions.describe(),
                channelId = channelId,
                createdAt = Instant.now(),
            ),
        )
        return result
    }
}

/** Login/lock gates for the first-run experience. */
sealed class OnboardingStage {
    object NeedsCredentials : OnboardingStage()
    data class Authentication(val state: AuthorizationState) : OnboardingStage()
    object ChooseChannel : OnboardingStage()
    object Complete : OnboardingStage()
}

class GetOnboardingStageUseCase @Inject constructor(
    private val credentialsRepository: TelegramCredentialsRepository,
    private val accountRepository: TelegramAccountRepository,
    private val channelRepository: TelegramChannelRepository,
) {
    fun stage(): Flow<OnboardingStage> = combine(
        credentialsRepository.observeConfigured(),
        accountRepository.observeAuthorizationState(),
        channelRepository.observeDefaultChannel(),
    ) { configured, auth, defaultChannel ->
        when {
            !configured -> OnboardingStage.NeedsCredentials
            auth !is AuthorizationState.Ready -> OnboardingStage.Authentication(auth)
            defaultChannel == null -> OnboardingStage.ChooseChannel
            else -> OnboardingStage.Complete
        }
    }.distinctUntilChanged()
}

/** Permission snapshot used by the channel details UI. */
data class ChannelPermissionSnapshot(
    val permissions: ChannelPermissions,
    val missingForUpload: List<String>,
)

class GetChannelPermissionSnapshotUseCase @Inject constructor(
    private val channelRepository: TelegramChannelRepository,
) {
    suspend operator fun invoke(channelId: Long): ChannelPermissionSnapshot {
        val perms = channelRepository.verifyPermissions(channelId)
        return ChannelPermissionSnapshot(perms, perms.missingForUpload)
    }
}
