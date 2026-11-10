package voice.core.ui

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import voice.core.data.ThemeColor
import voice.core.data.ThemeMode
import voice.core.data.store.ThemeColorStore
import voice.core.data.store.ThemeModeStore

@ContributesTo(AppScope::class)
interface SharedGraph {

  @get:ThemeModeStore
  val themeModeStore: DataStore<ThemeMode>

  @get:ThemeColorStore
  val themeColorStore: DataStore<ThemeColor>
}
