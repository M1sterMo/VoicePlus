package voice.features.bookOverview.deleteBook

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.RetainedViewModel
import voice.core.data.BookId
import voice.core.data.repo.BookContentRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.ExcludedBooksStore
import voice.core.logging.api.Logger
import voice.core.playback.PlayerController
import voice.core.scanner.MediaScanTrigger
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.core.strings.R as StringsR

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(
  scope = BookOverviewScope::class,
  binding = binding<BottomSheetItemViewModel>(),
)
@Inject
class DeleteBookViewModel(
  private val application: Application,
  private val mediaScanTrigger: MediaScanTrigger,
  private val contentRepo: BookContentRepo,
  @ExcludedBooksStore
  private val excludedBooksStore: DataStore<Set<String>>,
  private val playerController: PlayerController,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  private val dispatcherProvider: DispatcherProvider,
) : RetainedViewModel(),
  BottomSheetItemViewModel {

  private val _state = mutableStateOf<DeleteBookViewState?>(null)
  internal val state: State<DeleteBookViewState?> get() = _state

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    return listOf(BottomSheetItem.DeleteBook)
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item != BottomSheetItem.DeleteBook) return

    _state.value = DeleteBookViewState(
      id = bookId,
      alsoDeleteFiles = false,
      fileToDelete = bookId.toUri().pathSegments
        .let { segments ->
          val result = segments.lastOrNull()?.removePrefix("primary:")
          if (result.isNullOrEmpty()) {
            Logger.w("Could not determine path for $segments")
            segments.joinToString(separator = "/")
          } else {
            result
          }
        },
    )
  }

  internal fun onDismiss() {
    _state.value = null
  }

  internal fun onToggleDeleteFiles(checked: Boolean) {
    _state.value = _state.value?.copy(alsoDeleteFiles = checked)
  }

  internal fun onConfirmRemove() {
    val state = _state.value ?: return
    _state.value = null
    scope.launch {
      // Add to exclusion list so the scanner won't re-activate this book
      excludedBooksStore.updateData { it + state.id.value }

      // Mark inactive immediately so it disappears from library now
      contentRepo.get(state.id)?.let { content ->
        contentRepo.put(content.copy(isActive = false))
      }

      // A removed book must not keep playing, and must not come back on the next media-button press
      // or Android Auto browse — both resolve the current book without checking whether it's hidden.
      playerController.pauseIfCurrentBookIs(state.id)
      currentBookStoreId.updateData { current -> current.takeUnless { it == state.id } }

      if (state.alsoDeleteFiles) {
        deleteFiles(state.id)
      }

      mediaScanTrigger.scan(restartIfScanning = true)
    }
  }

  /**
   * DocumentFile.delete() reports failure by returning false — a revoked SAF grant or a provider
   * without delete support otherwise leaves the user believing gigabytes were freed while the files
   * are still on disk, with the book now hidden so nothing ever surfaces them again.
   */
  private suspend fun deleteFiles(id: BookId) {
    // A slow documents provider or a large folder blocks long enough for an ANR on the main thread.
    val deleted = withContext(dispatcherProvider.io) {
      runCatching { DocumentFile.fromSingleUri(application, id.toUri())?.delete() }
        .onFailure { Logger.w(it, "Could not delete files for $id") }
        .getOrNull() == true
    }
    if (!deleted) {
      Logger.w("Deleting the files for $id failed; the book was removed from the library only")
      Toast.makeText(application, StringsR.string.remove_book_delete_failed, Toast.LENGTH_LONG).show()
    }
  }
}

data class DeleteBookViewState(
  val id: BookId,
  val alsoDeleteFiles: Boolean,
  val fileToDelete: String,
)
