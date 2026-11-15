package voice.core.ui

import android.app.Application
import android.app.UiModeManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import coil.Coil
import coil.ImageLoader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import voice.core.data.ThemeColor
import voice.core.data.ThemeMode
import voice.core.data.store.ThemeColorStore
import voice.core.data.store.ThemeModeStore
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
class UIAppStartInitializer(
  @ThemeModeStore
  private val themeModeStore: DataStore<ThemeMode>,
  @ThemeColorStore
  private val themeColorStore: DataStore<ThemeColor>,
  private val scope: CoroutineScope,
) : AppInitializer {

  override fun onAppStart(application: Application) {
    Coil.setImageLoader(
      ImageLoader.Builder(application)
        .addLastModifiedToFileCacheKey(false)
        .build(),
    )
    // Before any activity exists, so none starts in the wrong mode. Loading the colour here too
    // lets VoiceTheme read both from memory for its first frame.
    runBlocking {
      themeColorStore.data.first()
      applyNightMode(application, themeModeStore.data.first())
    }
    themeModeStore.data
      .distinctUntilChanged()
      .onEach { applyNightMode(application, it) }
      .launchIn(scope)
  }

  private fun applyNightMode(
    application: Application,
    themeMode: ThemeMode,
  ) {
    AppCompatDelegate.setDefaultNightMode(
      when (themeMode) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
      },
    )
    // The system draws the launch splash before the app runs; on Android 12+ it can be told the
    // app's own mode, so a dark app on a light phone doesn't open on a white splash.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      application.getSystemService(UiModeManager::class.java).setApplicationNightMode(
        when (themeMode) {
          ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
          ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
          ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        },
      )
    }
  }
}
