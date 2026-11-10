package voice.features.upNext

import android.app.Application
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.UpNextStore
import voice.core.initializer.AppInitializer

/**
 * A queued book leaves the queue once it becomes the current book, whichever way it got there:
 * the queue advancing, the listener opening it, Android Auto or a widget.
 */
@ContributesIntoSet(AppScope::class)
class LeaveUpNextWhenCurrent(
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  @UpNextStore
  private val upNextStore: DataStore<List<BookId>>,
  private val scope: CoroutineScope,
) : AppInitializer {

  override fun onAppStart(application: Application) {
    scope.launch {
      currentBookStore.data.filterNotNull().collect { current ->
        upNextStore.updateData { it - current }
      }
    }
  }
}
