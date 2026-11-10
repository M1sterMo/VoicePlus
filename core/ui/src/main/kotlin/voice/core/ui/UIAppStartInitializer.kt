package voice.core.ui

import android.app.Application
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
      applyNightMode(themeModeStore.data.first())
    }
    themeModeStore.data
      .distinctUntilChanged()
      .onEach(::applyNightMode)
      .launchIn(scope)
  }

  private fun applyNightMode(themeMode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(
      when (themeMode) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
      },
    )
  }
}
