package com.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.core.ServiceLocator
import com.jarvis.ui.nav.JarvisNavHost
import com.jarvis.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs = ServiceLocator.prefs
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle()
            val accent by prefs.accent.collectAsStateWithLifecycle()

            JarvisTheme(themeMode = themeMode, accentHex = accent) {
                JarvisNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Wake word + "am I allowed to listen" bookkeeping.
        ServiceLocator.engine.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Phase 1: listening stops with the UI. The always-on service arrives in
        // Phase 2, which is why the persistent notification is not shown yet.
        ServiceLocator.engine.onBackground()
    }
}
