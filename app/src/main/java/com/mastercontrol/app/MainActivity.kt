package com.mastercontrol.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.mastercontrol.app.ui.MasterControlRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host.
 *
 * Extends [FragmentActivity] because the app lock can use `BiometricPrompt`,
 * which requires a fragment activity. Everything else is Compose.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MasterControlRoot() }
    }
}
