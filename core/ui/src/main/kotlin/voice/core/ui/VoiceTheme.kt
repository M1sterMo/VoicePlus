package voice.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.datastore.core.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import voice.core.common.rootGraphAs
import voice.core.data.ThemeColor

@Composable
fun VoiceTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = voiceColorScheme(
      color = themeSetting(ThemeColor.DYNAMIC) { themeColorStore },
      dark = isDarkTheme(),
    ),
  ) {
    content()
  }
}

/** Previews have no graph, so they render the defaults. */
@Composable
internal fun <T> themeSetting(
  default: T,
  store: SharedGraph.() -> DataStore<T>,
): T {
  if (LocalInspectionMode.current) return default
  val flow = remember { rootGraphAs<SharedGraph>().store().data }
  // UIAppStartInitializer has loaded these stores, so this returns from memory without a default-coloured frame.
  val initial = remember { runBlocking { flow.first() } }
  return flow.collectAsState(initial = initial, context = Dispatchers.Unconfined).value
}
