package voice.features.upNext

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.UpNextStore

@ContributesTo(AppScope::class)
interface UpNextGraph {
  val upNextViewModel: UpNextViewModel
}

internal data class UpNextViewState(
  val nowPlaying: Book?,
  val queue: List<Book>,
)

@Inject
class UpNextViewModel(
  @UpNextStore
  private val upNextStore: DataStore<List<BookId>>,
  @CurrentBookStore
  currentBookStore: DataStore<BookId?>,
  bookRepository: BookRepository,
) : RetainedViewModel() {

  internal val state: Flow<UpNextViewState> = combine(
    currentBookStore.data,
    upNextStore.data,
    bookRepository.flow(),
  ) { current, queue, books ->
    val byId = books.associateBy { it.id }
    UpNextViewState(nowPlaying = current?.let(byId::get), queue = queue.mapNotNull(byId::get))
  }

  /** Puts [id] where [target] is now, as dragging it onto that book does. */
  internal fun move(
    id: BookId,
    target: BookId,
  ) {
    scope.launch { upNextStore.updateData { it.moved(id, target) } }
  }

  internal fun remove(id: BookId) {
    scope.launch { upNextStore.updateData { it - id } }
  }
}

internal fun List<BookId>.moved(
  id: BookId,
  target: BookId,
): List<BookId> {
  if (id !in this || target !in this || id == target) return this
  val rest = this - id
  val at = rest.indexOf(target) + if (indexOf(id) < indexOf(target)) 1 else 0
  return rest.toMutableList().apply { add(at, id) }
}
