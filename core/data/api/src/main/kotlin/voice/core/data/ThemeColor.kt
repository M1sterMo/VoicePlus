package voice.core.data

import kotlinx.serialization.Serializable

/** The app's colour scheme. [DYNAMIC] follows the wallpaper on Android 12+ and is the Material baseline below. */
@Serializable
public enum class ThemeColor {
  DYNAMIC,
  TEAL,
  GREEN,
  ORANGE,
  ROSE,
  BLUE,
}
