package com.traceon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.traceon.core.ui.theme.TraceonTheme
import com.traceon.core.ui.theme.isDark
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ThemeMode
import com.traceon.domain.repository.SettingsRepository
import com.traceon.feature.tracking.UsageSyncScheduler
import com.traceon.navigation.TraceonNav
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val settings: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeFlow = remember { settings.themeMode() }
            val mode by themeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val dark = mode.isDark()
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            TraceonTheme(mode) { TraceonNav() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opportunistic refresh of app-usage sessions, only if the user has switched that source on.
        lifecycleScope.launch {
            if (settings.sourceStates().first()[CollectionMethod.USAGE_STATS] == true) UsageSyncScheduler.syncNow(applicationContext)
        }
    }
}
