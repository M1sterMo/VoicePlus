package voice.features.bookOverview.shelves

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import voice.core.data.BookId

@Composable
internal fun rememberLibraryShelfDragState(
  onDrop: (Set<BookId>, String) -> Unit,
  canReorder: (shelfId: String) -> Boolean = { false },
  onReorder: (shelfId: String, itemId: String, targetItemId: String) -> Unit = { _, _, _ -> },
): LibraryShelfDragState {
  val scope = rememberCoroutineScope()
  val drop by rememberUpdatedState(onDrop)
  val reorderable by rememberUpdatedState(canReorder)
  val reorder by rememberUpdatedState(onReorder)
  val haptic = LocalHapticFeedback.current
  return remember(scope, haptic) {
    LibraryShelfDragState(
      scope = scope,
      onDrop = { books, shelf -> drop(books, shelf) },
      canReorder = { reorderable(it) },
      onReorder = { shelf, item, target -> reorder(shelf, item, target) },
      haptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
    )
  }
}

/**
 * Coordinates a lifted library entry with the visible shelf headings and entries that can receive it.
 * Over another shelf it moves there; over an entry of its own shelf, where that shelf keeps a manual
 * order, it takes that entry's place.
 */
internal class LibraryShelfDragState(
  private val scope: CoroutineScope,
  private val onDrop: (Set<BookId>, String) -> Unit,
  private val canReorder: (String) -> Boolean = { false },
  private val onReorder: (String, String, String) -> Unit = { _, _, _ -> },
  private val haptic: () -> Unit,
) {
  var dragged: String? by mutableStateOf(null)
    private set
  var returning: String? by mutableStateOf(null)
    private set
  var hoveredShelf: String? by mutableStateOf(null)
    private set
  var hoveredEntry: String? by mutableStateOf(null)
    private set
  var offset: Offset by mutableStateOf(Offset.Zero)
    private set
  val settle = Animatable(Offset.Zero, Offset.VectorConverter)

  private val shelfBounds = mutableMapOf<String, Rect>()
  private val entryBounds = mutableMapOf<String, Triple<String, String, Rect>>()
  private var books = emptySet<BookId>()
  private var sourceShelf = ""
  private var pointer = Offset.Zero
  private var settleJob: Job? = null

  fun registerShelf(
    id: String,
    bounds: Rect,
  ) {
    shelfBounds[id] = bounds
    updateHoveredShelf()
  }

  fun unregisterShelf(id: String) {
    shelfBounds.remove(id)
    if (hoveredShelf == id) hoveredShelf = null
  }

  fun registerEntry(
    key: String,
    shelfId: String,
    itemId: String,
    bounds: Rect,
  ) {
    entryBounds[key] = Triple(shelfId, itemId, bounds)
    updateHoveredShelf()
  }

  fun unregisterEntry(key: String) {
    entryBounds.remove(key)
    updateHoveredShelf()
  }

  fun start(
    itemId: String,
    bookIds: Set<BookId>,
    shelfId: String,
    pointerInRoot: Offset,
  ) {
    settleJob?.cancel()
    returning = null
    dragged = itemId
    books = bookIds
    sourceShelf = shelfId
    pointer = pointerInRoot
    offset = Offset.Zero
    hoveredShelf = null
    haptic()
  }

  fun drag(delta: Offset) {
    if (dragged == null) return
    offset += delta
    pointer += delta
    updateHoveredShelf()
  }

  fun drop() {
    val itemId = dragged ?: return
    val destination = hoveredShelf
    val target = hoveredEntry
    val shelf = sourceShelf
    when {
      destination != null -> {
        val movedBooks = books
        clear()
        onDrop(movedBooks, destination)
      }
      target != null -> {
        clear()
        onReorder(shelf, itemId, target)
      }
      else -> returnToOrigin(itemId)
    }
  }

  fun cancel() {
    dragged?.let(::returnToOrigin)
  }

  fun visualOffset(itemId: String): Offset = when (itemId) {
    dragged -> offset
    returning -> settle.value
    else -> Offset.Zero
  }

  private fun updateHoveredShelf() {
    hoveredShelf = shelfBounds.entries.firstOrNull { (id, bounds) -> id != sourceShelf && bounds.contains(pointer) }?.key
      ?: entryBounds.values.firstOrNull { (id, _, bounds) -> id != sourceShelf && bounds.contains(pointer) }?.first
    hoveredEntry = if (hoveredShelf == null && dragged != null && canReorder(sourceShelf)) {
      entryBounds.values.firstOrNull { (id, item, bounds) -> id == sourceShelf && item != dragged && bounds.contains(pointer) }?.second
    } else {
      null
    }
  }

  private fun returnToOrigin(itemId: String) {
    val distance = offset
    dragged = null
    hoveredShelf = null
    hoveredEntry = null
    returning = itemId
    settleJob = scope.launch {
      settle.snapTo(distance)
      settle.animateTo(Offset.Zero, spring(dampingRatio = 1f, stiffness = 400f))
      clear()
    }
  }

  private fun clear() {
    dragged = null
    returning = null
    hoveredShelf = null
    hoveredEntry = null
    books = emptySet()
    sourceShelf = ""
    pointer = Offset.Zero
    offset = Offset.Zero
  }
}
