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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import voice.core.data.ThemeMode
import voice.core.data.store.ThemeModeStore
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
class UIAppStartInitializer(
  @ThemeModeStore
  private val themeModeStore: DataStore<ThemeMode>,
  private val scope: CoroutineScope,
) : AppInitializer {

  override fun onAppStart(application: Application) {
    Coil.setImageLoader(
      ImageLoader.Builder(application)
        .addLastModifiedToFileCacheKey(false)
        .build(),
    )
    themeModeStore.data
      .distinctUntilChanged()
      .onEach { themeMode ->
        AppCompatDelegate.setDefaultNightMode(
          when (themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
          },
        )
      }
      .launchIn(scope)
  }
}
