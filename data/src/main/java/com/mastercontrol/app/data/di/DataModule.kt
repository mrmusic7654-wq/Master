package com.mastercontrol.app.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mastercontrol.app.core.database.database.MasterControlDatabase
import com.mastercontrol.app.core.datastore.dataStoreOf
import com.mastercontrol.app.data.documents.SafDocumentStore
import com.mastercontrol.app.data.media.AndroidMediaToolkit
import com.mastercontrol.app.data.repository.ActivityRepositoryImpl
import com.mastercontrol.app.data.repository.AppLockRepositoryImpl
import com.mastercontrol.app.data.repository.CategoryRepositoryImpl
import com.mastercontrol.app.data.repository.ChannelPersistenceImpl
import com.mastercontrol.app.data.repository.FolderRepositoryImpl
import com.mastercontrol.app.data.repository.UploadTaskRepositoryImpl
import com.mastercontrol.app.data.repository.VideoRepositoryImpl
import com.mastercontrol.app.data.id.RoomVideoIdAllocator
import com.mastercontrol.app.domain.id.VideoIdAllocator
import com.mastercontrol.app.domain.port.DocumentStore
import com.mastercontrol.app.domain.port.MediaToolkit
import com.mastercontrol.app.domain.repository.ActivityRepository
import com.mastercontrol.app.domain.repository.AppLockRepository
import com.mastercontrol.app.domain.repository.CategoryRepository
import com.mastercontrol.app.domain.repository.ChannelPersistence
import com.mastercontrol.app.domain.repository.FolderRepository
import com.mastercontrol.app.domain.repository.SettingsRepository
import com.mastercontrol.app.domain.repository.UploadTaskRepository
import com.mastercontrol.app.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object DataStoreProviderModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        dataStoreOf(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MasterControlDatabase =
        MasterControlDatabase.build(context)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    abstract fun bindUploadTaskRepository(impl: UploadTaskRepositoryImpl): UploadTaskRepository

    @Binds
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    abstract fun bindChannelPersistence(impl: ChannelPersistenceImpl): ChannelPersistence

    @Binds
    abstract fun bindAppLockRepository(impl: AppLockRepositoryImpl): AppLockRepository

    @Binds
    abstract fun bindMediaToolkit(impl: AndroidMediaToolkit): MediaToolkit

    @Binds
    abstract fun bindDocumentStore(impl: SafDocumentStore): DocumentStore

    @Binds
    abstract fun bindVideoIdAllocator(impl: RoomVideoIdAllocator): VideoIdAllocator

    @Binds
    abstract fun bindSettingsRepository(impl: com.mastercontrol.app.core.datastore.SettingsRepositoryImpl): SettingsRepository
}
