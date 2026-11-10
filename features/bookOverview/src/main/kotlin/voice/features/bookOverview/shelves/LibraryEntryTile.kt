package voice.features.bookOverview.shelves

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import voice.core.data.BookId
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.series.LibraryEntry
import voice.features.bookOverview.series.SeriesCard
import voice.features.bookOverview.views.LibraryGridBook
import voice.features.bookOverview.views.ListBookRow

@Composable
internal fun LibraryEntryTile(
  entry: LibraryEntry,
  layout: BookOverviewLayoutMode,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  onSeriesClick: (String, Rect) -> Unit,
  onSeriesLongClick: ((BookId) -> Unit)?,
  onCoverOrigin: (String) -> Unit,
  activeCoverKey: String?,
  sharedTransitionScope: SharedTransitionScope?,
  openSeriesKey: String?,
  selecting: Boolean,
  selected: Set<BookId>,
  onSelect: (Set<BookId>) -> Unit,
  modifier: Modifier = Modifier,
  compactBooks: Boolean = false,
  dragState: LibraryShelfDragState? = null,
  onOrganise: ((Set<BookId>) -> Unit)? = null,
) {
  val ids = entry.allBooks.map { it.value.id }.toSet()
  val toggle = { onSelect(ids) }
  var bounds by remember(entry.key) { mutableStateOf(Rect.Zero) }
  DisposableEffect(dragState, entry.key) {
    onDispose { dragState?.unregisterEntry(entry.key) }
  }
  val moving = dragState?.let { it.dragged == entry.itemId || it.returning == entry.itemId } == true
  // Holding a book lifts it; letting go without moving still opens its menu, as a long press did.
  val holdMenu: (() -> Unit)? = when {
    selecting || onOrganise != null -> null
    entry.seriesName != null -> onSeriesLongClick?.let { { it(entry.allBooks.first().value.id) } }
    else -> onBookLongClick?.let { { it(entry.books.single().value.id) } }
  }
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val dragModifier = if (dragState == null) {
    Modifier
  } else {
    Modifier
      .onGloballyPositioned {
        bounds = it.boundsInRoot()
        dragState.registerEntry(entry.key, entry.sectionId, entry.itemId, bounds)
      }
      .pointerInput(entry.key, dragState, holdMenu) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
          dragState.start(entry.itemId, ids, entry.sectionId, bounds.topLeft + press.position)
          // After the hold this gesture owns the finger: it reads events before the row does and consumes
          // them, so the row never turns the release into a tap.
          while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id } ?: break
            val delta = change.positionChange()
            change.consume()
            if (!change.pressed) break
            dragState.drag(delta)
          }
          if (dragState.offset.getDistance() < touchSlop) {
            dragState.cancel()
            holdMenu?.invoke()
          } else {
            dragState.drop()
          }
        }
      }
      .zIndex(if (moving) 2f else 0f)
      .graphicsLayer {
        val offset = dragState.visualOffset(entry.itemId)
        translationX = offset.x
        translationY = offset.y
        if (moving) {
          scaleX = 1.035f
          scaleY = 1.035f
          alpha = .94f
          shadowElevation = 12.dp.toPx()
        }
      }
  }
  val target = if (dragState?.hoveredEntry == entry.itemId) {
    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
  } else {
    Modifier
  }
  Box(modifier.then(dragModifier).then(target).then(if (entry.seriesName == null) Modifier.testTag(entry.key) else Modifier)) {
    if (entry.seriesName != null) {
      SeriesCard(
        entry,
        onClick = { bounds ->
          when {
            selecting -> toggle()
            onOrganise != null -> onOrganise(ids)
            else -> onSeriesClick(entry.key, bounds)
          }
        },
        onLongClick = when {
          selecting -> toggle
          onOrganise != null || dragState != null -> null
          else -> onSeriesLongClick?.let { { it(entry.allBooks.first().value.id) } }
        },
        listMode = layout == BookOverviewLayoutMode.List,
        booksMode = layout == BookOverviewLayoutMode.Books,
        expanded = entry.key == openSeriesKey,
        compact = compactBooks,
      )
    } else {
      val click: (BookId) -> Unit = {
        if (selecting) {
          toggle()
        } else if (onOrganise != null) {
          onOrganise(ids)
        } else {
          onCoverOrigin(entry.key)
          onBookClick(it)
        }
      }
      val longClick: ((BookId) -> Unit)? = when {
        selecting -> ({ toggle() })
        onOrganise != null || dragState != null -> null
        else -> onBookLongClick
      }
      val scope = sharedTransitionScope.takeIf { entry.key == activeCoverKey }
      if (layout == BookOverviewLayoutMode.List) {
        ListBookRow(entry.books.single().value, click, longClick, scope)
      } else {
        LibraryGridBook(entry.books.single().value, click, longClick, scope, layout == BookOverviewLayoutMode.Books, compactBooks)
      }
    }
    if (selecting) Checkbox(selected.containsAll(ids), onCheckedChange = { toggle() }, Modifier.align(Alignment.TopEnd).padding(4.dp))
  }
}
