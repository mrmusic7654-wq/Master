package com.mastercontrol.app.domain.usecase

import com.mastercontrol.app.domain.model.ActivityLogEntry
import com.mastercontrol.app.domain.model.ActivityType
import com.mastercontrol.app.domain.model.MappingStatus
import com.mastercontrol.app.domain.model.ReconciliationReport

import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.TelegramMediaRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Reconciliation engine.
 *
 * Detects (and only reports — never auto-deletes):
 *  - database says a mapping exists, Telegram message missing  -> REMOTE_DELETED
 *  - Telegram message exists but file/metadata differs         -> STALE
 *  - Telegram media present in the channel without any local mapping -> "Unmanaged
 *    Telegram Media Found" entries for the administrator to decide.
 */
class ReconcileUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val channelRepository: TelegramChannelRepository,
    private val telegramMediaRepository: TelegramMediaRepository,
    private val activityRepository: ActivityRepository,
) {
    suspend operator fun invoke(scanUnmanagedWindow: Int = 200): ReconciliationReport {
        var report = ReconciliationReport()
        val runAt = Instant.now()

        // 1) Verify every locally recorded mapping.
        val mappings = videoRepository.getAllMappings()
        for (mapping in mappings) {
            if (mapping.mappingStatus == MappingStatus.NONE) continue
            report = report.copy(mappingsChecked = report.mappingsChecked + 1)
            val verification = telegramMediaRepository.verifyMessage(mapping.channelId, mapping.messageId)
            when {
                !verification.found -> {
                    videoRepository.setMappingStatus(mapping.videoId, MappingStatus.REMOTE_DELETED)
                    activityRepository.add(
                        ActivityLogEntry(
                            type = ActivityType.MAPPING_STALE,
                            message = "${mapping.videoId}: Telegram message ${mapping.messageId} is missing",
                            relatedVideoId = mapping.videoId,
                            channelId = mapping.channelId,
                            createdAt = runAt,
                        ),
                    )
                    report = report.copy(mappingsRemoteMissing = report.mappingsRemoteMissing + 1)
                }
                !verification.matchesVideo -> {
                    videoRepository.setMappingStatus(mapping.videoId, MappingStatus.STALE)
                    activityRepository.add(
                        ActivityLogEntry(
                            type = ActivityType.MAPPING_STALE,
                            message = "${mapping.videoId}: Telegram media changed (size/file mismatch)",
                            relatedVideoId = mapping.videoId,
                            channelId = mapping.channelId,
                            createdAt = runAt,
                        ),
                    )
                    report = report.copy(mappingsStale = report.mappingsStale + 1)
                }
                else -> {
                    videoRepository.setMappingStatus(mapping.videoId, MappingStatus.ACTIVE)
                    report = report.copy(mappingsOk = report.mappingsOk + 1)
                }
            }
        }

        // 2) Optional scan of the most recent channel media for unmanaged items.
        //    Default channel is scanned only, and nothing is ever auto-deleted.
        val defaultChannel = channelRepository.getChannelByDefault()
        if (defaultChannel != null && scanUnmanagedWindow > 0) {
            val knownMessageIds = mappings
                .filter { it.channelId == defaultChannel.id }
                .map { it.messageId }
                .toSet()
            val recent = telegramMediaRepository.fetchRecentMessages(
                channelId = defaultChannel.id,
                fromMessageId = 0L,
                offset = 0,
                limit = scanUnmanagedWindow,
            )
            val unmanaged = recent.filter { it.isVideo && it.messageId !in knownMessageIds }
            if (unmanaged.isNotEmpty()) {
                report = report.copy(
                    unmanagedFound = unmanaged.size,
                    unmanagedItems = unmanaged,
                )
                activityRepository.add(
                    ActivityLogEntry(
                        type = ActivityType.UNMANAGED_TELEGRAM_MEDIA_FOUND,
                        message = "${unmanaged.size} unmanaged Telegram video(s) found in ${defaultChannel.displayName}",
                        details = unmanaged.take(5).joinToString { "msg ${it.messageId}" },
                        channelId = defaultChannel.id,
                        createdAt = runAt,
                    ),
                )
            }
        }

        activityRepository.add(
            ActivityLogEntry(
                type = ActivityType.RECONCILIATION_RAN,
                message = "Reconciliation: ${report.mappingsChecked} mappings checked, " +
                    "${report.unmanagedFound} unmanaged item(s) found",
                channelId = defaultChannel?.id,
                createdAt = runAt,
            ),
        )
        return report
    }
}
