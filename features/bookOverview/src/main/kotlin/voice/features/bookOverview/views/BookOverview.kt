package voice.features.bookOverview.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.data.LibraryOrganisation
import voice.core.ui.PlayButton
import voice.core.ui.VoiceTheme
import voice.features.bookOverview.bottomSheet.BottomSheetContent
import voice.features.bookOverview.deleteBook.DeleteBookDialog
import voice.features.bookOverview.di.BookOverviewGraph
import voice.features.bookOverview.editBook.EditBookDialog
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.overview.BookOverviewViewState
import voice.features.bookOverview.search.BookSearchViewState
import voice.features.bookOverview.series.SeriesDetails
import voice.features.bookOverview.series.SeriesEditorSheet
import voice.features.bookOverview.shelves.LibrarySection
import voice.features.bookOverview.shelves.LibraryShelfDragState
import voice.features.bookOverview.shelves.ShelfControls
import voice.features.bookOverview.shelves.ShelfDialog
import voice.features.bookOverview.shelves.ShelfFilter
import voice.features.bookOverview.shelves.ShelfLibraryBar
import voice.features.bookOverview.shelves.librarySections
import voice.features.bookOverview.shelves.rememberLibraryShelfDragState
import voice.features.bookOverview.views.topbar.BookOverviewTopBar
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.SharedTransitionNavEntryProvider
import java.util.UUID
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface BookOverviewProvider {

  @Provides
  @IntoSet
  fun bookOverviewNavEntryProvider(scrollState: BookOverviewScrollState): NavEntryProvider<*> =
    SharedTransitionNavEntryProvider<Destination.BookOverview> { key, scope ->
      NavEntry(key) {
        BookOverviewScreen(
          scrollState = scrollState,
          sharedTransitionScope = scope,
        )
      }
    }
}

@SingleIn(AppScope::class)
@Inject
class BookOverviewScrollState {
  internal var filter by mutableStateOf(ShelfFilter.ALL)
  private var listIndex = 0
  private var listOffset = 0
  private var gridIndex = 0
  private var gridOffset = 0
  private var listKey: String? = null
  private var gridKey: String? = null
  private var listRestorePending = false
  private var gridRestorePending = false

  fun newListState() = LazyListState(listIndex, listOffset)

  fun newGridState() = LazyGridState(gridIndex, gridOffset)

  fun capture(
    layoutMode: BookOverviewLayoutMode,
    listState: LazyListState,
    gridState: LazyGridState,
  ) {
    when (layoutMode) {
      BookOverviewLayoutMode.List -> {
        captureList(listState)
      }
      BookOverviewLayoutMode.Grid, BookOverviewLayoutMode.Books -> {
        captureGrid(gridState)
      }
    }
  }

  fun captureIfNoRestorePending(
    layoutMode: BookOverviewLayoutMode,
    listState: LazyListState,
    gridState: LazyGridState,
  ) {
    when (layoutMode) {
      BookOverviewLayoutMode.List -> if (!listRestorePending) captureList(listState)
      BookOverviewLayoutMode.Grid, BookOverviewLayoutMode.Books -> if (!gridRestorePending) captureGrid(gridState)
    }
  }

  suspend fun restore(
    layoutMode: BookOverviewLayoutMode,
    listState: LazyListState,
    gridState: LazyGridState,
    itemKeys: List<String> = emptyList(),
  ) {
    fun index(
      key: String?,
      fallback: Int,
    ): Int {
      if (itemKeys.isEmpty()) return fallback
      val exact = itemKeys.indexOf(key)
      if (exact >= 0) return exact
      val section = key?.substringBefore(":book:")?.substringBefore(":series:")
      return itemKeys.indexOf("header:$section").coerceAtLeast(0)
    }
    when (layoutMode) {
      BookOverviewLayoutMode.List -> if (listRestorePending) {
        listState.scrollToItem(index(listKey, listIndex), listOffset)
        listRestorePending = false
      }
      BookOverviewLayoutMode.Grid, BookOverviewLayoutMode.Books -> if (gridRestorePending) {
        gridState.scrollToItem(index(gridKey, gridIndex), gridOffset)
        gridRestorePending = false
      }
    }
  }

  private fun captureList(state: LazyListState) {
    listIndex = state.firstVisibleItemIndex
    listOffset = state.firstVisibleItemScrollOffset
    listKey = state.layoutInfo.visibleItemsInfo.find { it.index == state.firstVisibleItemIndex }?.key as? String
    listRestorePending = true
  }

  private fun captureGrid(state: LazyGridState) {
    gridIndex = state.firstVisibleItemIndex
    gridOffset = state.firstVisibleItemScrollOffset
    gridKey = state.layoutInfo.visibleItemsInfo.find { it.index == state.firstVisibleItemIndex }?.key as? String
    gridRestorePending = true
  }
}

@Composable
fun BookOverviewScreen(
  scrollState: BookOverviewScrollState,
  modifier: Modifier = Modifier,
  sharedTransitionScope: SharedTransitionScope? = null,
) {
  val bookGraph = retain<BookOverviewGraph> {
    rootGraphAs<BookOverviewGraph.Factory.Provider>()
      .bookOverviewGraphProviderFactory.create()
  }
  val bookOverviewViewModel = bookGraph.bookOverviewViewModel
  val editBookViewModel = bookGraph.editBookViewModel
  val bottomSheetViewModel = bookGraph.bottomSheetViewModel
  val deleteBookViewModel = bookGraph.deleteBookViewModel
  val seriesViewModel = bookGraph.seriesViewModel
  val shelfViewModel = bookGraph.shelfViewModel
  var activeCoverKey by rememberSaveable { mutableStateOf<String?>(null) }
  var openSeries by rememberSaveable { mutableStateOf<String?>(null) }
  var seriesPrepared by remember(openSeries) { mutableStateOf(false) }
  var seriesOrigin by rememberSaveable { mutableStateOf(floatArrayOf(0f, 0f, 0f, 0f)) }
  val listState = remember { scrollState.newListState() }
  val gridState = remember { scrollState.newGridState() }
  val libraryDrag = rememberLibraryShelfDragState(shelfViewModel::dropOnShelf)

  LaunchedEffect(Unit) {
    bookOverviewViewModel.attach()
  }
  val viewState = bookOverviewViewModel.state()
  DisposableEffect(viewState.layoutMode, listState, gridState) {
    onDispose {
      scrollState.captureIfNoRestorePending(viewState.layoutMode, listState, gridState)
    }
  }

  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val resources = LocalResources.current
  LaunchedEffect(shelfViewModel, seriesViewModel, resources) {
    merge(shelfViewModel.notices, seriesViewModel.notices).collectLatest { notice ->
      val result = snackbar.showSnackbar(
        resources.getString(notice.message),
        actionLabel = if (notice.change !=
          null
        ) {
          resources.getString(StringsR.string.undo)
        } else {
          null
        },
        withDismissAction = true,
      )
      if (result == SnackbarResult.ActionPerformed && notice.change != null) shelfViewModel.undo(notice.change)
    }
  }
  BackHandler(enabled = shelfViewModel.selecting) { shelfViewModel.cancelSelection() }
  BackHandler(
    enabled = shelfViewModel.organising && shelfViewModel.dialog == null && shelfViewModel.draft == null,
    onBack = shelfViewModel::finishOrganising,
  )

  var showBottomSheet by remember { mutableStateOf(false) }
  val flatBooks = viewState.books.values.flatMap { it.entries }.associate { it.toPair() }
  // Progress animates in each tile; only fields which affect membership/sorting rebuild sections.
  val sortInputs by remember(flatBooks) {
    derivedStateOf {
      flatBooks.values.map {
        it.value.let { book ->
          listOf(
            book.name,
            book.author,
            book.addedAt,
            book.finished,
            book.progress == 0f,
          )
        }
      }
    }
  }
  val current = viewState.books[BookOverviewCategory.CURRENT].orEmpty().keys.toList()
  val sections = remember(flatBooks, current, viewState.organisation, shelfViewModel.filter, sortInputs) {
    librarySections(flatBooks, current, viewState.organisation ?: LibraryOrganisation(), shelfViewModel.filter)
  }
  val seriesEntries = sections.flatMap { it.entries }
  LaunchedEffect(viewState.isLoading, sections.map { it.id }, viewState.layoutMode) {
    if (!viewState.isLoading && sections.isNotEmpty()) {
      val keys = (if (viewState.showStoragePermissionBugCard) listOf("permission") else emptyList()) + sections.flatMap { section ->
        listOf("header:${section.id}") +
          if (!section.expanded) {
            emptyList()
          } else if (section.entries.isEmpty()) {
            listOf("empty:${section.id}")
          } else {
            section.entries.map { it.key }
          }
      }
      scrollState.restore(viewState.layoutMode, listState, gridState, keys)
    }
  }
  LaunchedEffect(viewState.isLoading, openSeries, seriesEntries.map { it.key }) {
    if (!viewState.isLoading && openSeries != null && seriesEntries.none { it.key == openSeries }) openSeries = null
  }
  Box {
    val overlayOpen =
      openSeries != null || shelfViewModel.dialog != null || shelfViewModel.draft != null || seriesViewModel.state != null ||
        showBottomSheet
    BookOverview(
      openSeriesKey = openSeries.takeIf { seriesPrepared },
      modifier = Modifier.focusProperties {
        onEnter = { if (overlayOpen) cancelFocusChange() }
      }.focusGroup().then(if (overlayOpen) Modifier.clearAndSetSemantics {} else Modifier),
      sharedTransitionScope = sharedTransitionScope,
      listState = listState,
      gridState = gridState,
      viewState = viewState,
      sections = sections,
      libraryBar = {
        ShelfLibraryBar(
          shelfViewModel,
          viewState.organisation ?: LibraryOrganisation(),
          onSearch = { bookOverviewViewModel.onSearchActiveChange(true) },
          onSettings = bookOverviewViewModel::onSettingsClick,
          onImport = bookOverviewViewModel::onBookFolderClick,
          onAddToSeries = {
            seriesViewModel.create(shelfViewModel.selected.toList())
            shelfViewModel.cancelSelection()
          },
        )
      },
      snackbar = { SnackbarHost(snackbar) },
      onShelfToggle = { section -> if (!section.current) shelfViewModel.toggleExpanded(section.id, section.expanded) },
      onShelfMenu = { shelfViewModel.dialog = ShelfDialog.Menu(it) },
      onShelfAdd = { shelfViewModel.addToShelf(it) },
      activeCoverKey = activeCoverKey,
      onCoverOrigin = { activeCoverKey = it },
      selecting = shelfViewModel.selecting,
      selected = shelfViewModel.selected,
      onSelect = { shelfViewModel.toggle(it, viewState.organisation ?: LibraryOrganisation()) },
      organising = shelfViewModel.organising,
      dragState = libraryDrag.takeIf { shelfViewModel.organising },
      onOrganise = shelfViewModel::chooseShelf,
      onSettingsClick = {
        scrollState.capture(viewState.layoutMode, listState, gridState)
        bookOverviewViewModel.onSettingsClick()
      },
      onBookClick = { bookId ->
        scrollState.capture(viewState.layoutMode, listState, gridState)
        bookOverviewViewModel.onBookClick(bookId)
      },
      onBookLongClick = { bookId ->
        scope.launch {
          bottomSheetViewModel.bookSelected(bookId)
          showBottomSheet = true
        }
      },
      onBookFolderClick = {
        scrollState.capture(viewState.layoutMode, listState, gridState)
        bookOverviewViewModel.onBookFolderClick()
      },
      onPlayButtonClick = bookOverviewViewModel::playPause,
      onSearchActiveChange = bookOverviewViewModel::onSearchActiveChange,
      onSearchQueryChange = bookOverviewViewModel::onSearchQueryChange,
      onSearchBookClick = { bookId ->
        activeCoverKey = "search:${bookId.value}"
        scrollState.capture(viewState.layoutMode, listState, gridState)
        bookOverviewViewModel.onSearchBookClick(bookId)
      },
      onSearchBookLongClick = { bookId ->
        scope.launch {
          bottomSheetViewModel.bookSelected(bookId)
          showBottomSheet = true
        }
      },
      onPermissionBugCardClick = bookOverviewViewModel::onPermissionBugCardClick,
      onSeriesClick = { name, bounds ->
        seriesOrigin = floatArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        openSeries = name
      },
      onSeriesLongClick = { bookId ->
        seriesViewModel.edit(bookId)
      },
    )
    val seriesEntry = seriesEntries.find { it.key == openSeries }
    if (seriesEntry != null) {
      key(seriesEntry.key) {
        var showEntireSeries by rememberSaveable { mutableStateOf(false) }
        SeriesDetails(
          entry = if (showEntireSeries) seriesEntry.copy(books = seriesEntry.allBooks) else seriesEntry,
          onShowAll = if (!showEntireSeries && seriesEntry.books.size < seriesEntry.allBooks.size) ({ showEntireSeries = true }) else null,
          booksPerRow = viewState.booksPerRow,
          origin = Rect(seriesOrigin[0], seriesOrigin[1], seriesOrigin[2], seriesOrigin[3]),
          onPrepare = { seriesPrepared = true },
          onDismiss = { openSeries = null },
          onEdit = {
            openSeries = null
            seriesViewModel.edit(seriesEntry.books.first().value.id)
          },
          onBookClick = {
            activeCoverKey = "series:$openSeries:${it.value}"
            scrollState.capture(viewState.layoutMode, listState, gridState)
            bookOverviewViewModel.onBookClick(it)
          },
          onBookLongClick = { bookId ->
            openSeries = null
            scope.launch {
              bottomSheetViewModel.bookSelected(bookId)
              showBottomSheet = true
            }
          },
        )
      }
    }
  }
  ShelfControls(
    shelfViewModel,
    viewState.organisation ?: LibraryOrganisation(),
    sections,
    flatBooks,
    viewState.layoutMode,
    viewState.booksPerRow,
  )
  seriesViewModel.state?.let { editor ->
    SeriesEditorSheet(
      state = editor,
      onDismiss = seriesViewModel::dismiss,
      onNameChange = seriesViewModel::nameChanged,
      onSuggestion = seriesViewModel::selectName,
      onToggle = seriesViewModel::toggle,
      onMove = seriesViewModel::move,
      onSave = { seriesViewModel.save() },
      onUngroup = { seriesViewModel.save(ungroup = true) },
      onShelfChange = seriesViewModel::selectShelf,
    )
  }
  val deleteBookViewState = deleteBookViewModel.state.value
  if (deleteBookViewState != null) {
    DeleteBookDialog(
      viewState = deleteBookViewState,
      onDismiss = deleteBookViewModel::onDismiss,
      onConfirmRemove = deleteBookViewModel::onConfirmRemove,
      onToggleDeleteFiles = deleteBookViewModel::onToggleDeleteFiles,
    )
  }
  val editBookDraft by editBookViewModel.state.collectAsState()
  editBookDraft?.let { EditBookDialog(it, editBookViewModel) }

  if (showBottomSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      modifier = modifier,
      sheetState = sheetState,
      content = {
        BottomSheetContent(
          state = bottomSheetViewModel.state.value,
          onItemClick = { item ->
            scope.launch {
              sheetState.hide()
              showBottomSheet = false
              bottomSheetViewModel.onItemClick(item)
            }
          },
        )
      },
      onDismissRequest = {
        showBottomSheet = false
      },
    )
  }
}

@Composable
internal fun BookOverview(
  sharedTransitionScope: SharedTransitionScope?,
  listState: LazyListState,
  gridState: LazyGridState,
  viewState: BookOverviewViewState,
  onSettingsClick: () -> Unit,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  onBookFolderClick: () -> Unit,
  onPlayButtonClick: () -> Unit,
  onSearchActiveChange: (Boolean) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSearchBookClick: (BookId) -> Unit,
  onSearchBookLongClick: (BookId) -> Unit,
  onPermissionBugCardClick: () -> Unit,
  modifier: Modifier = Modifier,
  onSeriesClick: (String, Rect) -> Unit = { _, _ -> },
  onSeriesLongClick: (BookId) -> Unit = {},
  openSeriesKey: String? = null,
  sections: List<LibrarySection> = emptyList(),
  libraryBar: @Composable () -> Unit = {},
  snackbar: @Composable () -> Unit = {},
  onShelfToggle: (LibrarySection) -> Unit = {},
  onShelfMenu: (String) -> Unit = {},
  onShelfAdd: (String) -> Unit = {},
  activeCoverKey: String? = null,
  onCoverOrigin: (String) -> Unit = {},
  selecting: Boolean = false,
  selected: Set<BookId> = emptySet(),
  onSelect: (Set<BookId>) -> Unit = {},
  organising: Boolean = false,
  dragState: LibraryShelfDragState? = null,
  onOrganise: (Set<BookId>) -> Unit = {},
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      if (!viewState.searchActive) {
        Column {
          libraryBar()
          if (viewState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
          if (viewState.showAddBookHint) {
            TextButton(onClick = onBookFolderClick, modifier = Modifier.padding(horizontal = 16.dp)) {
              Text(stringResource(StringsR.string.library_add_audiobooks))
            }
          }
        }
      } else {
        BookOverviewTopBar(
          sharedTransitionScope = sharedTransitionScope,
          viewState = viewState,
          onBookFolderClick = onBookFolderClick,
          onSettingsClick = onSettingsClick,
          onActiveChange = onSearchActiveChange,
          onQueryChange = onSearchQueryChange,
          onSearchBookClick = onSearchBookClick,
          onSearchBookLongClick = onSearchBookLongClick,
        )
      }
    },
    snackbarHost = snackbar,
    floatingActionButton = {
      if (viewState.playButtonState != null) {
        PlayButton(
          modifier = Modifier.navigationBarsPadding(),
          playing = viewState.playButtonState == BookOverviewViewState.PlayButtonState.Playing,
          fabSize = 56.dp,
          iconSize = 24.dp,
          onPlayClick = onPlayButtonClick,
        )
      }
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { contentPadding ->
    Box(
      Modifier
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding),
    ) {
      when (viewState.layoutMode) {
        BookOverviewLayoutMode.List -> {
          ListBooks(
            openSeriesKey = openSeriesKey,
            sharedTransitionScope = sharedTransitionScope,
            state = listState,
            onSeriesClick = onSeriesClick,
            onSeriesLongClick = onSeriesLongClick,
            sections = sections,
            onShelfToggle = onShelfToggle,
            onShelfMenu = onShelfMenu,
            onShelfAdd = onShelfAdd,
            activeCoverKey = activeCoverKey,
            onCoverOrigin = onCoverOrigin,
            selecting = selecting,
            selected = selected,
            onSelect = onSelect,
            organising = organising,
            dragState = dragState,
            onOrganise = onOrganise,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
            showPermissionBugCard = viewState.showStoragePermissionBugCard,
            onPermissionBugCardClick = onPermissionBugCardClick,
          )
        }
        BookOverviewLayoutMode.Grid, BookOverviewLayoutMode.Books -> {
          GridBooks(
            openSeriesKey = openSeriesKey,
            booksMode = viewState.layoutMode == BookOverviewLayoutMode.Books,
            booksPerRow = viewState.booksPerRow,
            sharedTransitionScope = sharedTransitionScope,
            state = gridState,
            onSeriesClick = onSeriesClick,
            onSeriesLongClick = onSeriesLongClick,
            sections = sections,
            onShelfToggle = onShelfToggle,
            onShelfMenu = onShelfMenu,
            onShelfAdd = onShelfAdd,
            activeCoverKey = activeCoverKey,
            onCoverOrigin = onCoverOrigin,
            selecting = selecting,
            selected = selected,
            onSelect = onSelect,
            organising = organising,
            dragState = dragState,
            onOrganise = onOrganise,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
            showPermissionBugCard = viewState.showStoragePermissionBugCard,
            onPermissionBugCardClick = onPermissionBugCardClick,
          )
        }
      }
    }
  }
}

@Suppress("ktlint:compose:preview-public-check")
@Preview
@Composable
fun BookOverviewPreview(
  @PreviewParameter(BookOverviewPreviewParameterProvider::class)
  viewState: BookOverviewViewState,
) {
  VoiceTheme {
    BookOverview(
      sharedTransitionScope = null,
      listState = LazyListState(),
      gridState = LazyGridState(),
      viewState = viewState,
      onSettingsClick = {},
      onBookClick = {},
      onBookLongClick = {},
      onBookFolderClick = {},
      onPlayButtonClick = {},
      onSearchActiveChange = {},
      onSearchQueryChange = {},
      onSearchBookClick = {},
      onSearchBookLongClick = {},
      onPermissionBugCardClick = {},
    )
  }
}

internal class BookOverviewPreviewParameterProvider : PreviewParameterProvider<BookOverviewViewState> {

  fun book(): BookOverviewItemViewState {
    return BookOverviewItemViewState(
      name = "Book",
      author = "Author",
      cover = null,
      progress = 0.8F,
      id = BookId(UUID.randomUUID().toString()),
      remainingTime = "01:04",
    )
  }

  override val values = sequenceOf(
    BookOverviewViewState(
      books = mapOf(
        BookOverviewCategory.CURRENT to buildMap {
          repeat(10) {
            put(
              BookId(UUID.randomUUID().toString()),
              mutableStateOf(book()),
            )
          }
        },
        BookOverviewCategory.FINISHED to buildMap {
          repeat(2) {
            put(
              BookId(UUID.randomUUID().toString()),
              mutableStateOf(book().copy(progress = 1f, finished = true, finishedOn = "12 Mar 2026")),
            )
          }
        },
      ),
      layoutMode = BookOverviewLayoutMode.List,
      playButtonState = BookOverviewViewState.PlayButtonState.Paused,
      showAddBookHint = false,
      showSearchIcon = true,
      isLoading = true,
      searchActive = true,
      searchViewState = BookSearchViewState.EmptySearch(
        suggestedAuthors = emptyList(),
        recentQueries = emptyList(),
        query = "",
      ),
      showStoragePermissionBugCard = false,
      showFolderPickerIcon = true,
    ),
  )
}
