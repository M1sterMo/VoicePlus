package voice.features.bookOverview.overview

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.common.comparator.sortedNaturally
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.ListeningSessionEndReason
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.LibraryOrganisationRepo
import voice.core.data.repo.ListeningSessionRepo
import voice.core.data.repo.internals.dao.RecentBookSearchDao
import voice.core.data.store.BooksPerRowStore
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.GridModeStore
import voice.core.featureflag.ExperimentalPlaybackPersistenceQualifier
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.FolderPickerInSettingsFeatureFlagQualifier
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.scanner.DeviceHasStoragePermissionBug
import voice.core.scanner.MediaScanTrigger
import voice.core.search.BookSearch
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.search.BookSearchViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

@SingleIn(BookOverviewScope::class)
@Inject
class BookOverviewViewModel(
  private val repo: BookRepository,
  private val mediaScanner: MediaScanTrigger,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  @CurrentBookStore
  private val currentBookStoreDataStore: DataStore<BookId?>,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
  @BooksPerRowStore
  private val booksPerRowStore: DataStore<Int>,
  private val navigator: Navigator,
  private val recentBookSearchDao: RecentBookSearchDao,
  private val search: BookSearch,
  private val contentRepo: BookContentRepo,
  private val sessionRepo: ListeningSessionRepo,
  private val deviceHasStoragePermissionBug: DeviceHasStoragePermissionBug,
  @FolderPickerInSettingsFeatureFlagQualifier
  private val folderPickerInSettingsFeatureFlag: FeatureFlag<Boolean>,
  @ExperimentalPlaybackPersistenceQualifier
  private val experimentalPlaybackPersistenceFeatureFlag: FeatureFlag<Boolean>,
  private val organisationRepo: LibraryOrganisationRepo,
) : RetainedViewModel() {
  private var searchActive by mutableStateOf(false)
  private var query by mutableStateOf("")

  fun attach() {
    // Every navigation back to the library re-enters composition and lands here. Skip the full
    // SAF re-walk when a scan completed recently; folder changes and restores scan explicitly.
    mediaScanner.scan(skipIfCompletedWithin = ENTRY_SCAN_COOLDOWN)
  }

  @Composable
  internal fun state(): BookOverviewViewState {
    val playState = remember { playStateManager.flow }
      .collectAsState(initial = PlayStateManager.PlayState.Paused).value
    val hasStoragePermissionBug = remember { deviceHasStoragePermissionBug.hasBug }
      .collectAsState().value
    val books = remember { repo.flow() }
      .collectAsState(initial = null).value
    val finishedAt = remember {
      sessionRepo.allSessions().map { sessions ->
        sessions
          .filter { it.endReason == ListeningSessionEndReason.EndOfBook.id }
          .groupBy({ it.bookId }, { it.endedAt })
          .mapValues { (_, endings) -> endings.max() }
      }
    }.collectAsState(initial = emptyMap()).value
    val currentBookId = remember { currentBookStoreDataStore.data }
      .collectAsState(initial = null).value
    val scannerActive = remember { mediaScanner.scannerActive }
      .collectAsState(initial = false).value
    val gridMode = remember { gridModeStore.data }
      .collectAsState(initial = null).value
    val booksPerRow = remember { booksPerRowStore.data }.collectAsState(initial = null).value?.coerceIn(2, 3)
    val organisation = remember { organisationRepo.flow() }.collectAsState(initial = null).value
    // Show the saved layout on the first populated frame, without a two-to-three-column shuffle.
    if (gridMode == null || books == null || booksPerRow == null || organisation == null) return BookOverviewViewState.Loading
    val noBooks = !scannerActive && books.isEmpty()

    val layoutMode = when (gridMode) {
      GridMode.LIST -> BookOverviewLayoutMode.List
      GridMode.GRID -> BookOverviewLayoutMode.Grid
      GridMode.BOOKS -> BookOverviewLayoutMode.Books
      GridMode.FOLLOW_DEVICE -> BookOverviewLayoutMode.Books
    }

    val bookSearchViewState = bookSearchViewState(layoutMode, booksPerRow)
    val experimentalPlaybackPersistence = experimentalPlaybackPersistenceFeatureFlag.get()
    val livePlaybackState: State<LivePlaybackState?> = if (experimentalPlaybackPersistence && currentBookId != null) {
      remember(currentBookId) {
        playerController.livePlaybackStateFlow(currentBookId)
      }.collectAsState(null)
    } else {
      remember { mutableStateOf(null) }
    }

    return BookOverviewViewState(
      layoutMode = layoutMode,
      booksPerRow = booksPerRow,
      organisation = organisation,
      books = books
        .groupBy {
          it.category
        }
        .mapValues { (category, books) ->
          books
            .sortedWith(category.comparator)
            .associate { book ->
              book.id to key(book.id) {
                book.itemViewState(
                  currentBookId = currentBookId,
                  finishedAt = finishedAt[book.id],
                  livePlaybackState = { livePlaybackState.value },
                )
              }
            }
        }
        .toSortedMap(),
      playButtonState = if (playState == PlayStateManager.PlayState.Playing) {
        BookOverviewViewState.PlayButtonState.Playing
      } else {
        BookOverviewViewState.PlayButtonState.Paused
      }.takeIf { currentBookId != null },
      showAddBookHint = if (hasStoragePermissionBug) {
        false
      } else {
        noBooks
      },
      showSearchIcon = books.isNotEmpty(),
      isLoading = scannerActive,
      searchActive = searchActive,
      searchViewState = bookSearchViewState,
      showStoragePermissionBugCard = hasStoragePermissionBug,
      showFolderPickerIcon = !folderPickerInSettingsFeatureFlag.get(),
    )
  }

  @Composable
  private fun bookSearchViewState(
    layoutMode: BookOverviewLayoutMode,
    booksPerRow: Int,
  ): BookSearchViewState {
    return if (searchActive) {
      val recentBookSearch = remember {
        recentBookSearchDao.recentBookSearches()
      }.collectAsState(initial = emptyList()).value.reversed()
      var searchBooks by remember {
        mutableStateOf(emptyList<BookOverviewItemViewState>())
      }
      LaunchedEffect(query) {
        searchBooks = search.search(query).map { it.toItemViewState() }
      }
      val suggestedAuthors: List<String> by produceState(initialValue = emptyList()) {
        value = contentRepo.all()
          .filter { it.isActive }
          .mapNotNull { it.author }
          .toSet()
          .sortedNaturally()
      }

      val bookSearchViewState = if (query.isNotBlank()) {
        BookSearchViewState.SearchResults(
          query = query,
          books = searchBooks,
          layoutMode = layoutMode,
          booksPerRow = booksPerRow,
        )
      } else {
        BookSearchViewState.EmptySearch(
          recentQueries = recentBookSearch,
          suggestedAuthors = suggestedAuthors,
          query = query,
        )
      }
      bookSearchViewState
    } else {
      BookSearchViewState.EmptySearch(
        recentQueries = emptyList(),
        suggestedAuthors = emptyList(),
        query = query,
      )
    }
  }

  fun onSettingsClick() {
    navigator.goTo(Destination.Settings)
  }

  fun setView(mode: GridMode) {
    scope.launch { gridModeStore.updateData { mode } }
  }
  fun setBooksPerRow(count: Int) {
    scope.launch { booksPerRowStore.updateData { count.coerceIn(2, 3) } }
  }

  fun onBookClick(id: BookId) {
    navigator.goTo(Destination.Playback(id))
  }

  fun onBookFolderClick() {
    navigator.goTo(Destination.FolderPicker)
  }

  fun onSearchActiveChange(active: Boolean) {
    if (active && !searchActive) {
      query = ""
    }
    this.searchActive = active
  }

  fun onSearchQueryChange(query: String) {
    this.query = query
  }

  fun onSearchBookClick(id: BookId) {
    val query = query.trim()
    if (query.isNotBlank()) {
      scope.launch {
        recentBookSearchDao.add(query)
      }
    }
    searchActive = false
    navigator.goTo(Destination.Playback(id))
  }

  fun playPause() {
    playerController.playPause()
  }

  fun onPermissionBugCardClick() {
    if (Build.VERSION.SDK_INT >= 30) {
      navigator.goTo(
        Destination.Activity(
          Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:com.android.externalstorage".toUri()),
        ),
      )
    }
  }
}

@Composable
private fun Book.itemViewState(
  currentBookId: BookId?,
  finishedAt: Instant?,
  livePlaybackState: () -> LivePlaybackState?,
): State<BookOverviewItemViewState> {
  if (id != currentBookId) {
    return rememberUpdatedState(toItemViewState(finishedAt))
  }
  val currentPlaybackState by rememberUpdatedState(livePlaybackState)
  return remember(this, currentBookId, finishedAt) {
    derivedStateOf {
      val livePlayback = currentPlaybackState()
      if (livePlayback != null) {
        overlay(livePlayback)
      } else {
        this
      }.toItemViewState(finishedAt)
    }
  }
}

// How stale the library may get before re-entering the overview triggers another automatic scan.
private val ENTRY_SCAN_COOLDOWN = 5.minutes
