package com.mastercontrol.app.lock

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped unlock state.
 *
 * Deliberately *not* persisted: a configuration change (rotation, split screen)
 * keeps the session unlocked, while process death or a fresh launch requires the
 * operator to unlock again. Persisting an "unlocked" flag would defeat the lock.
 */
@Singleton
class AppLockSession @Inject constructor() {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}
