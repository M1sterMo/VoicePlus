package voice.features.chapterEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.common.RetainedViewModel
import voice.core.common.resolveChapterName
import voice.core.data.BookId
import voice.core.data.byMarkKey
import voice.core.data.markForPosition
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.playback.PlayerController
import voice.navigation.Navigator

@AssistedInject
public class ChapterEditorViewModel(
  private val bookRepository: BookRepository,
  private val overrideRepo: ChapterNameOverrideRepo,
  private val playerController: PlayerController,
  private val navigator: Navigator,
  dispatcherProvider: DispatcherProvider,
  @Assisted private val bookId: BookId,
) : RetainedViewModel(MainScope(dispatcherProvider)) {
  private val localOffset = MutableStateFlow<Int?>(null)
  private val editMutex = Mutex()
  private var initialOffset: Int? = null
  private var chaptersBeforeCurrent = emptyList<ChapterItemState>()
  private var chaptersToFreeze: List<ChapterItemState>? = null

  private val _editingChapter = mutableStateOf<ChapterItemState?>(null)
  internal val editingChapter: State<ChapterItemState?> get() = _editingChapter

  private val _showResetConfirm = mutableStateOf(false)

  @Composable
  public fun viewState(): ChapterEditorViewState? {
    val book = bookRepository.flow(bookId)
      .filterNotNull()
      .collectAsState(null).value ?: return null

    val liveMarkKey by remember(bookId, book.chapters) {
      playerController.livePlaybackStateFlow(bookId)
        .map { liveState ->
          liveState?.let { state ->
            book.chapters
              .firstOrNull { it.id == state.chapterId }
              ?.markForPosition(state.positionMs)
              ?.let { mark -> Pair(state.chapterId.value, mark.startMs) }
          }
        }
        .distinctUntilChanged()
    }.collectAsState(null)

    val overrideList by overrideRepo.overridesForBook(bookId)
      .collectAsState(emptyList())

    // Seed the editable offset from the persisted value once the book first loads; thereafter
    // localOffset is the source of truth and every edit is persisted back to the book.
    remember(book.content.id) {
      if (localOffset.value == null) {
        initialOffset = book.content.chapterNameOffset
        localOffset.value = book.content.chapterNameOffset
      }
      book.content.id
    }
    val offset = localOffset.collectAsState().value ?: book.content.chapterNameOffset

    val overrideMap = overrideList.byMarkKey()

    val persistedCurrentMark = book.currentChapter.markForPosition(book.content.positionInChapter)
    val currentMarkKey = liveMarkKey
      ?: Pair(book.currentChapter.id.value, persistedCurrentMark.startMs)

    var globalIndex = 0
    var currentChapterIndex = 0
    val items = book.chapters.flatMap { chapter ->
      chapter.chapterMarks.map { mark ->
        val key = Pair(chapter.id.value, mark.startMs)
        val override = overrideMap[key]
        val isCurrent = key == currentMarkKey
        val item = ChapterItemState(
          chapterId = chapter.id,
          markStartMs = mark.startMs,
          displayNumber = globalIndex + 1,
          displayName = resolveChapterName(mark.name ?: "", offset, override),
          hasOverride = override != null,
          isCurrent = isCurrent,
        )
        if (isCurrent) currentChapterIndex = globalIndex
        globalIndex++
        item
      }
    }
    chaptersBeforeCurrent = items.take(currentChapterIndex)

    return ChapterEditorViewState(
      offset = offset,
      chapters = items,
      currentChapterIndex = currentChapterIndex,
      editingChapter = _editingChapter.value,
      showResetConfirm = _showResetConfirm.value,
    )
  }

  public fun onOffsetIncrement() {
    val current = localOffset.value ?: 0
    setOffset(if (current == Int.MAX_VALUE) current else current + 1)
  }

  public fun onOffsetDecrement() {
    val current = localOffset.value ?: 0
    setOffset(if (current == Int.MIN_VALUE) current else current - 1)
  }

  public fun onOffsetSet(value: Int) {
    setOffset(value)
  }

  public fun onEditChapterClick(item: ChapterItemState) {
    _editingChapter.value = item
  }

  public fun onEditDismiss() {
    _editingChapter.value = null
  }

  public fun onEditConfirm(
    item: ChapterItemState,
    newName: String,
  ) {
    scope.launch {
      editMutex.withLock {
        overrideRepo.set(item.chapterId, item.markStartMs, bookId, newName.trim())
      }
      _editingChapter.value = null
    }
  }

  public fun onDeleteOverride(item: ChapterItemState) {
    scope.launch {
      editMutex.withLock {
        overrideRepo.delete(item.chapterId, item.markStartMs)
      }
    }
  }

  public fun onResetAllClick() {
    _showResetConfirm.value = true
  }

  public fun onResetAllConfirm() {
    _showResetConfirm.value = false
    localOffset.value = 0
    scope.launch {
      editMutex.withLock {
        bookRepository.updateBook(bookId) { it.copy(chapterNameOffset = 0) }
        overrideRepo.deleteAll(bookId)
        initialOffset = 0
        chaptersToFreeze = null
      }
    }
  }

  public fun onResetAllDismiss() {
    _showResetConfirm.value = false
  }

  public fun onBack() {
    // Each offset change is already persisted, so just navigate back.
    navigator.goBack()
  }

  private fun setOffset(offset: Int) {
    if (localOffset.value == offset) return
    // Once a book has needed a correction, keep the names before the currently playing mark.
    // Audiobooks can contain extra or missing embedded marks, so one global offset cannot describe
    // the whole timeline. Existing manual names remain authoritative.
    if (chaptersToFreeze == null &&
      (initialOffset?.let { it != 0 } == true || chaptersBeforeCurrent.any { it.hasOverride })
    ) {
      chaptersToFreeze = chaptersBeforeCurrent.filterNot { it.hasOverride }
    }
    localOffset.value = offset
    scope.launch {
      editMutex.withLock {
        val existingOverrides = overrideRepo.overridesForBook(bookId).first().byMarkKey()
        chaptersToFreeze.orEmpty().forEach { chapter ->
          val key = Pair(chapter.chapterId.value, chapter.markStartMs)
          if (key !in existingOverrides) {
            overrideRepo.set(
              chapterId = chapter.chapterId,
              markStartMs = chapter.markStartMs,
              bookId = bookId,
              name = chapter.displayName,
            )
          }
        }
        // Freeze once per editor session so repeated +/- taps do not rewrite prior ranges.
        if (chaptersToFreeze != null) chaptersToFreeze = emptyList()
        bookRepository.updateBook(bookId) { it.copy(chapterNameOffset = offset) }
      }
    }
  }

  @AssistedFactory
  public interface Factory {
    public fun create(bookId: BookId): ChapterEditorViewModel
  }
}
