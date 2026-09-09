package com.mastercontrol.app.worker.di

import com.mastercontrol.app.domain.port.UploadWorkerScheduler
import com.mastercontrol.app.worker.scheduler.UploadWorkerSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    abstract fun bindUploadWorkerScheduler(impl: UploadWorkerSchedulerImpl): UploadWorkerScheduler
}
