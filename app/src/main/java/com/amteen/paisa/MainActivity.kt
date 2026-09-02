package com.amteen.paisa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            // TODO(Phase 2): read the persisted ThemeMode from SettingsRepository.
            PaisaTheme(themeSetting = ThemeSetting.SYSTEM) {
                PaisaNavHost()
            }
        }
    }
}
