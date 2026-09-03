package com.amteen.paisa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amteen.paisa.di.LocalAppContainer
import com.amteen.paisa.domain.model.ThemeMode
import com.amteen.paisa.ui.navigation.PaisaNavHost
import com.amteen.paisa.ui.theme.PaisaTheme
import com.amteen.paisa.ui.theme.ThemeSetting

/**
 * The app's single Activity. Everything else is Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as PaisaApp).container

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalAppContainer provides container) {
                PaisaTheme(themeSetting = settings.themeMode.toThemeSetting()) {
                    PaisaNavHost()
                }
            }
        }
    }
}

private fun ThemeMode.toThemeSetting(): ThemeSetting = when (this) {
    ThemeMode.SYSTEM -> ThemeSetting.SYSTEM
    ThemeMode.LIGHT -> ThemeSetting.LIGHT
    ThemeMode.DARK -> ThemeSetting.DARK
}
