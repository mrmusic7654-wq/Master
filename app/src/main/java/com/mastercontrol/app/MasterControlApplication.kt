package com.mastercontrol.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.Tags
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Two responsibilities only: hand WorkManager the Hilt worker factory (the
 * upload, import and reconciliation workers are `@HiltWorker` classes) and write
 * a single, secret-free startup line to the diagnostics log.
 */
@HiltAndroidApp
class MasterControlApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var logger: Logger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            // Release builds never emit WorkManager debug output; the log is a
            // diagnostics surface, not a place where request data belongs.
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        logger.info(Tags.APP, "Master Control ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) started")
    }
}
