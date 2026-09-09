package com.mastercontrol.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.masterControlDataStore: DataStore<Preferences> by preferencesDataStore(name = "master_control_settings")

/** Owns the process-wide preferences DataStore. */
fun dataStoreOf(context: Context): DataStore<Preferences> = context.masterControlDataStore
