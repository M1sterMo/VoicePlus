package voice.features.upNext

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import voice.core.common.rootGraphAs
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.realTimeRemainingMs
import voice.core.ui.BookReorderState
import voice.core.ui.formatShortTime
import voice.core.ui.rememberBookReorderState
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

/** The current book on top, then the queued books: drag to reorder, swipe away to remove. */
@Composable
fun UpNextSheet(onDismiss: () -> Unit) {
  val viewModel = retain { rootGraphAs<UpNextGraph>().upNextViewModel }
  val state = remember(viewModel) { viewModel.state }.collectAsState(initial = null).value
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    if (state != null) UpNextContent(state, onMove = viewModel::move, onRemove = viewModel::remove)
  }
}

@Composable
private fun UpNextContent(
  state: UpNextViewState,
  onMove: (BookId, BookId) -> Unit,
  onRemove: (BookId) -> Unit,
) {
  val queue = state.queue
  val listState = rememberLazyListState()
  val reorder = rememberBookReorderState(listState) { id, index -> queue.getOrNull(index)?.let { onMove(id, it.id) } }
  Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    state.nowPlaying?.let { book ->
      Text(
        stringResource(StringsR.string.up_next_now_playing),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      NowPlaying(book)
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(StringsR.string.up_next), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
      if (queue.isNotEmpty()) {
        val total = queue.sumOf { it.duration }
        Text(
          pluralStringResource(StringsR.plurals.series_book_count, queue.size, queue.size) + " · " +
            stringResource(StringsR.string.up_next_length, total / 3_600_000, total / 60_000 % 60),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (queue.isEmpty()) {
      Text(stringResource(StringsR.string.up_next_empty), style = MaterialTheme.typography.bodyMedium)
    } else {
      LazyColumn(Modifier.weight(1f, fill = false), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(queue, key = { _, book -> book.id.value }) { index, book ->
          val motion = when (book.id) {
            reorder.draggedId -> Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }
            reorder.returningId -> Modifier.zIndex(1f).graphicsLayer { translationY = reorder.returnOffset.value }
            else -> Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
          }
          QueuedBook(
            position = index + 1,
            book = book,
            reorder = reorder,
            onMoveUp = queue.getOrNull(index - 1)?.let { above -> { onMove(book.id, above.id) } },
            onMoveDown = queue.getOrNull(index + 1)?.let { below -> { onMove(book.id, below.id) } },
            onRemove = { onRemove(book.id) },
            modifier = motion,
          )
        }
      }
      Text(
        stringResource(StringsR.string.up_next_hint),
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun QueuedBook(
  position: Int,
  book: Book,
  reorder: BookReorderState,
  onMoveUp: (() -> Unit)?,
  onMoveDown: (() -> Unit)?,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val up = stringResource(StringsR.string.series_move_up)
  val down = stringResource(StringsR.string.series_move_down)
  val remove = stringResource(StringsR.string.up_next_remove)
  SwipeToDismissBox(
    state = rememberSwipeToDismissBoxState(),
    modifier = modifier.semantics {
      customActions = listOfNotNull(
        onMoveUp?.let { move ->
          CustomAccessibilityAction(up) {
            move()
            true
          }
        },
        onMoveDown?.let { move ->
          CustomAccessibilityAction(down) {
            move()
            true
          }
        },
        CustomAccessibilityAction(remove) {
          onRemove()
          true
        },
      )
    },
    backgroundContent = {
      Box(
        Modifier.fillMaxSize().clip(
          MaterialTheme.shapes.large,
        ).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
      }
    },
    onDismiss = { onRemove() },
  ) {
    Row(
      Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("$position", Modifier.width(20.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
      Cover(book, 56.dp)
      BookText(book, listOfNotNull(book.content.author, formatShortTime(book.duration)).joinToString(" · "), Modifier.weight(1f))
      Icon(
        Icons.Outlined.DragHandle,
        contentDescription = stringResource(StringsR.string.series_reorder_book, book.content.name),
        modifier = Modifier.padding(8.dp).pointerInput(reorder, book.id) {
          detectDragGestures(
            onDragStart = { reorder.start(book.id) },
            onDrag = { change, amount ->
              change.consume()
              reorder.drag(amount.y)
            },
            onDragEnd = reorder::stop,
            onDragCancel = reorder::stop,
          )
        },
      )
    }
  }
}

@Composable
private fun NowPlaying(book: Book) {
  // At the listener's speed, as the library and player say it.
  val timeLeft = stringResource(StringsR.string.book_time_left, formatShortTime(book.realTimeRemainingMs()))
  Row(
    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Cover(book, 72.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      BookText(book, listOfNotNull(book.content.author, timeLeft).joinToString(" · "))
      LinearProgressIndicator(
        progress = { if (book.duration > 0) book.position.toFloat() / book.duration else 0f },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun BookText(
  book: Book,
  detail: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text(book.content.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
  }
}

@Composable
private fun Cover(
  book: Book,
  size: Dp,
) {
  AsyncImage(
    model = book.content.cover,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    placeholder = painterResource(UiR.drawable.album_art),
    error = painterResource(UiR.drawable.album_art),
    modifier = Modifier.size(size).clip(MaterialTheme.shapes.medium),
  )
}
