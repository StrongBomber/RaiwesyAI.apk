package com.raiwesy.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.raiwesy.ai.core.util.AppearanceMode
import com.raiwesy.ai.ui.ChatScreen
import com.raiwesy.ai.ui.ChatViewModel
import com.raiwesy.ai.ui.theme.RaiwesyTheme
import kotlinx.coroutines.launch

/**
 * Single-activity app.
 *
 *  - edge-to-edge Material 3 experience,
 *  - splash screen (core-splashscreen),
 *  - Compose content rooted in [ChatScreen],
 *  - theme mode (System / Light / Dark) applied to both the Android
 *    framework (AppCompatDelegate) and the Compose theme.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val container = (application as RaiwesyApp).container
                return ChatViewModel(container) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        // Keep the Android framework (status bar, splash) in sync with the
        // selected appearance mode.
        lifecycleScope.launch {
            viewModel.state.collect { chatState ->
                applyAppearance(chatState.themeMode)
            }
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (state.themeMode) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }

            RaiwesyTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun applyAppearance(mode: AppearanceMode) {
        val target = when (mode) {
            AppearanceMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppearanceMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppearanceMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target)
        }
    }
}
