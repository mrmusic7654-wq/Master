package com.mastercontrol.app.telegram.di

import com.mastercontrol.app.domain.port.TelegramUploadExecutor
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.domain.repository.TelegramChannelRepository
import com.mastercontrol.app.domain.repository.TelegramCredentialsRepository
import com.mastercontrol.app.domain.repository.TelegramMediaRepository
import com.mastercontrol.app.telegram.repository.TelegramAccountRepositoryImpl
import com.mastercontrol.app.telegram.repository.TelegramChannelRepositoryImpl
import com.mastercontrol.app.telegram.repository.TelegramCredentialsRepositoryImpl
import com.mastercontrol.app.telegram.repository.TelegramMediaRepositoryImpl
import com.mastercontrol.app.telegram.upload.TelegramUploadExecutorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramBindingsModule {

    @Binds
    abstract fun bindTelegramCredentialsRepository(
        impl: TelegramCredentialsRepositoryImpl,
    ): TelegramCredentialsRepository

    @Binds
    abstract fun bindTelegramAccountRepository(impl: TelegramAccountRepositoryImpl): TelegramAccountRepository

    @Binds
    abstract fun bindTelegramChannelRepository(impl: TelegramChannelRepositoryImpl): TelegramChannelRepository

    @Binds
    abstract fun bindTelegramMediaRepository(impl: TelegramMediaRepositoryImpl): TelegramMediaRepository

    @Binds
    abstract fun bindTelegramUploadExecutor(impl: TelegramUploadExecutorImpl): TelegramUploadExecutor
}
