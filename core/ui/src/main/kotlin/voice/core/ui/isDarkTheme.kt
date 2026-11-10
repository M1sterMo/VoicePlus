package voice.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import voice.core.data.ThemeMode

@Composable
fun isDarkTheme(): Boolean {
  return when (themeSetting(ThemeMode.SYSTEM) { themeModeStore }) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
}
