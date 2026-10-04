package voice.features.bookOverview.series

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import voice.core.data.BookId

@Composable
internal fun rememberSeriesReorderState(
  list: LazyListState,
  onMove: (BookId, Int) -> Unit,
): SeriesReorderState {
  val scope = rememberCoroutineScope()
  val move by rememberUpdatedState(onMove)
  val haptics = LocalHapticFeedback.current
  val state = remember(list, scope, haptics) {
    SeriesReorderState(list, scope, { id, index -> move(id, index) }) {
      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
  }
  val edge = with(LocalDensity.current) { 48.dp.toPx() }
  LaunchedEffect(state, state.draggedId, edge) {
    if (state.draggedId != null) {
      var previous = withFrameMillis { it }
      while (true) {
        val now = withFrameMillis { it }
        state.scrollAtEdge(edge, (now - previous).coerceAtMost(32) / 1000f)
        previous = now
      }
    }
  }
  return state
}

/** Pick up and move within the list; keys keep the held book stable while its neighbours move. */
internal class SeriesReorderState(
  private val list: LazyListState,
  private val scope: CoroutineScope,
  private val onMove: (BookId, Int) -> Unit,
  private val onPickUp: () -> Unit,
) {
  var draggedId: BookId? by mutableStateOf(null)
    private set
  var returningId: BookId? by mutableStateOf(null)
    private set
  val returnOffset = Animatable(0f)
  private var returnJob: Job? = null
  private var top by mutableFloatStateOf(0f)
  private var expectedIndex: Int? = null
  private val item get() = list.layoutInfo.visibleItemsInfo.find { it.key == draggedId?.value }
  val offset: Float get() = item?.let { top - it.offset } ?: 0f

  fun start(id: BookId) {
    val info = list.layoutInfo.visibleItemsInfo.find { it.key == id.value } ?: return
    returnJob?.cancel()
    returningId = null
    draggedId = id
    top = info.offset.toFloat()
    expectedIndex = null
    onPickUp()
  }

  fun drag(delta: Float) {
    top += delta
    val current = item ?: return
    // Wait for the previous move to be measured before choosing another neighbour.
    if (expectedIndex != null && current.index != expectedIndex) return
    expectedIndex = null
    val centre = top + current.size / 2f
    val target = list.layoutInfo.visibleItemsInfo.find {
      it.key != current.key && centre >= it.offset && centre < it.offset + it.size
    } ?: return
    list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
    expectedIndex = target.index
    onMove(draggedId!!, target.index)
  }

  suspend fun scrollAtEdge(
    edge: Float,
    seconds: Float,
  ) {
    val current = item ?: return
    val viewport = list.layoutInfo
    val speed = when {
      top < viewport.viewportStartOffset + edge -> -((viewport.viewportStartOffset + edge - top) / edge).coerceIn(0f, 1f)
      top + current.size > viewport.viewportEndOffset - edge -> ((top + current.size - viewport.viewportEndOffset + edge) / edge).coerceIn(
        0f,
        1f,
      )
      else -> 0f
    }
    if (speed != 0f) list.scrollBy(speed * edge * 10f * seconds)
    drag(0f)
  }

  fun stop() {
    val id = draggedId ?: return
    val distance = offset
    returningId = id
    draggedId = null
    expectedIndex = null
    returnJob = scope.launch {
      returnOffset.snapTo(distance)
      returnOffset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 400f))
      returningId = null
    }
  }
}
