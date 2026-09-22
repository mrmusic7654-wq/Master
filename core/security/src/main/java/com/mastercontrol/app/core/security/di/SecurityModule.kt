package com.mastercontrol.app.core.security.di

import com.mastercontrol.app.core.security.AndroidKeyStoreCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Composition root for the Keystore-backed secret store.
 *
 * [AndroidKeyStoreCipher] is created without an `@Inject` constructor on purpose:
 * it touches the platform keystore, and keeping that behind an explicit provider
 * makes the security boundary visible in one place (see SECURITY.md).
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideAndroidKeyStoreCipher(): AndroidKeyStoreCipher = AndroidKeyStoreCipher()
}
