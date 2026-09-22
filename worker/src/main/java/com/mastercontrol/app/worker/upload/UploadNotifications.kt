package com.mastercontrol.app.worker.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.mastercontrol.app.core.common.format.Formatters
import com.mastercontrol.app.worker.R
import com.mastercontrol.app.domain.model.UploadTask
import com.mastercontrol.app.domain.model.UploadTaskState

/**
 * Progress notification for the long-running upload queue worker.
 *
 * Large transfers legitimately exceed WorkManager's background execution window,
 * so while a task is actually in flight the worker promotes itself to a
 * foreground service. The notification renders the *same* numbers the upload
 * screen shows (bytesUploaded / totalBytes from real TDLib `updateFile` events);
 * nothing here is estimated or fabricated.
 *
 * The worker lives in a library module and therefore resolves the tap target
 * through the package launch intent instead of referencing the app's Activity.
 */
class UploadNotifications(private val context: Context) {

    private val manager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    /** Idempotent: creates the low-importance "Uploads" channel once. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = manager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.mc_channel_uploads_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.mc_channel_uploads_description)
                setShowBadge(false)
            },
        )
    }

    fun foregroundInfo(task: UploadTask, title: String?): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, notification(task, title), foregroundServiceType())

    /**
     * Refreshes the visible progress while a transfer runs. Called from the
     * worker on real transfer events only, and throttled by the worker.
     */
    fun updateProgress(task: UploadTask, title: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager?.notify(NOTIFICATION_ID, notification(task, title))
    }

    private fun notification(task: UploadTask, title: String?): Notification {
        val transferred = task.totalBytes?.takeIf { it > 0 }
        val determinate = transferred != null && task.state == UploadTaskState.UPLOADING
        val percent = if (determinate) (task.progress * 100).toInt().coerceIn(0, 100) else 0
        val subtitle = when (task.state) {
            UploadTaskState.PREPARING -> "Preparing ${task.videoId}"
            UploadTaskState.UPLOADING ->
                if (determinate) {
                    "${Formatters.percent(task.progress)} · ${Formatters.bytes(task.bytesUploaded)} of ${Formatters.bytes(transferred)}"
                } else {
                    "Uploading · ${Formatters.bytes(task.bytesUploaded)}"
                }
            UploadTaskState.VERIFYING -> "Verifying Telegram message for ${task.videoId}"
            UploadTaskState.RETRYING -> "Waiting to retry ${task.videoId}"
            else -> task.videoId
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: task.videoId)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(100, percent, !determinate)
            .apply { contentIntent()?.let { setContentIntent(it) } }
            .build()
    }

    private fun contentIntent(): PendingIntent? {
        val intent: Intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * Android 14 requires an explicit foreground-service type. Uploads move the
     * operator's own media to Telegram, which is data synchronization.
     */
    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    companion object {
        const val CHANNEL_ID = "master_control_uploads"
        const val NOTIFICATION_ID = 47_110
    }
}
