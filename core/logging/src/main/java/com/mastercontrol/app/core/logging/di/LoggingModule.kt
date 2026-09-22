package com.mastercontrol.app.core.logging.di

import com.mastercontrol.app.core.logging.BuildConfig
import com.mastercontrol.app.core.logging.Logger
import com.mastercontrol.app.core.logging.LoggerConfiguration
import com.mastercontrol.app.core.logging.MasterLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Composition root for logging.
 *
 * The logging policy is derived from the build type: release builds never emit
 * verbose/debug logs, which is the first line of defence against leaking an API
 * hash, a verification code or a phone number into logcat (see SECURITY.md).
 */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Provides
    @Singleton
    fun provideLoggerConfiguration(): LoggerConfiguration = LoggerConfiguration(
        debugLogsEnabled = BuildConfig.DEBUG,
        bufferEnabled = true,
        bufferSize = if (BuildConfig.DEBUG) 2_000 else 500,
    )

    @Provides
    @Singleton
    fun provideLogger(logger: MasterLogger): Logger = logger
}
