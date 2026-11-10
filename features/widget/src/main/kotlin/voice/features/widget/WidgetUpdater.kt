package voice.features.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.app.features.widget.BaseWidgetProvider
import voice.core.common.resolveChapterName
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.SeekTimeStore
import voice.core.playback.CurrentBookResolver
import voice.core.playback.notification.MainActivityIntentProvider
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.receiver.WidgetButtonReceiver
import voice.core.ui.dpToPxRounded
import voice.features.widget.config.WidgetConfigStore
import java.io.File
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@SingleIn(AppScope::class)
@Inject
class WidgetUpdater(
  private val context: Context,
  private val currentBookResolver: CurrentBookResolver,
  private val chapterNameOverrideRepo: ChapterNameOverrideRepo,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  @SeekTimeStore
  private val seekTimeStore: DataStore<Int>,
  private val configStore: WidgetConfigStore,
  private val playStateManager: PlayStateManager,
  private val mainActivityIntentProvider: MainActivityIntentProvider,
) {

  private val appWidgetManager = AppWidgetManager.getInstance(context)
  private val scope = CoroutineScope(Dispatchers.IO)

  // Serializes refreshes. Without it, concurrent updates each read the play state at their own
  // moment and write to the widget in whatever order they finish: a slow refresh started while
  // paused can land after a fast one started while playing, leaving a play icon on a playing book.
  private val updateMutex = Mutex()

  /** Fire-and-forget refresh for callers that can't suspend (the widget provider, the config screen). */
  fun update() {
    scope.launch { updateNow() }
  }

  /**
   * Suspends until the widget has been redrawn. Callers driven by a flow should use this rather than
   * [update] so that conflation upstream actually collapses a burst of changes into one refresh.
   */
  suspend fun updateNow() {
    updateMutex.withLock {
      val book = currentBookStore.data.first()?.let { currentBookResolver.book(it) }
      val seekSeconds = seekTimeStore.data.first()
      val chapterName = book?.let { resolveCurrentChapterName(it) }.orEmpty()
      val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, BaseWidgetProvider::class.java))
      for (widgetId in ids) {
        appWidgetManager.updateAppWidget(widgetId, render(widgetId, book, chapterName, seekSeconds))
      }
    }
  }

  private suspend fun resolveCurrentChapterName(book: Book): String {
    val override = chapterNameOverrideRepo.overridesForBook(book.content.id).first()
      .firstOrNull { it.chapterId == book.currentChapter.id.value && it.markStartMs == book.currentMark.startMs }
      ?.name
    return resolveChapterName(book.currentMark.name ?: "", book.content.chapterNameOffset, override)
      .ifBlank { book.currentChapter.name.orEmpty() }
  }

  private suspend fun render(
    widgetId: Int,
    book: Book?,
    chapterName: String,
    seekSeconds: Int,
  ): RemoteViews {
    val opts = appWidgetManager.getAppWidgetOptions(widgetId)
    val width = widgetWidth(opts)
    val height = widgetHeight(opts)
    // Until the launcher reports a size, show everything and let the host scale the cover.
    val sizeKnown = width > 0 && height > 0
    val padding = context.dpToPxRounded(12F)
    val coverHeight = (if (sizeKnown) height - 2 * padding else context.dpToPxRounded(96F)).coerceAtLeast(1)
    val coverWidth = (coverHeight * configStore.getCoverShape(widgetId).aspectRatio).roundToInt().coerceAtLeast(1)
    val scale = configStore.getTextScale(widgetId)
    val titleSize = context.resources.getDimension(R.dimen.widget_title_size) * scale
    val summarySize = context.resources.getDimension(R.dimen.widget_summary_size) * scale

    val remoteViews = RemoteViews(context.packageName, R.layout.widget_compact)
    remoteViews.setInt(R.id.widgetBackground, "setImageAlpha", configStore.getAlpha(widgetId))
    remoteViews.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_PX, titleSize)
    remoteViews.setTextViewTextSize(R.id.summary, TypedValue.COMPLEX_UNIT_PX, summarySize)
    remoteViews.setOnClickPendingIntent(R.id.wholeWidget, mainActivityIntentProvider.toCurrentBook())
    remoteViews.setImageViewBitmap(R.id.imageView, coverBitmap(book?.content?.cover, coverWidth, coverHeight))

    // Visibility has to be set on every render, not merely hidden when needed: the host reuses the
    // inflated view and applies only the actions this RemoteViews carries, so a GONE set earlier
    // persists and leaves a playing book with no controls.
    if (book == null) {
      // No book, so nothing wires the controls up: leaving them visible offers buttons that do
      // nothing (and that TalkBack still announces).
      remoteViews.setTextViewText(R.id.title, context.getString(StringsR.string.widget_no_book))
      remoteViews.setVisible(R.id.title, true)
      remoteViews.setVisible(R.id.summary, false)
      remoteViews.setVisible(R.id.progress, false)
      remoteViews.setVisible(R.id.buttons, false)
      return remoteViews
    }

    remoteViews.setTextViewText(R.id.title, book.content.name)
    remoteViews.setTextViewText(R.id.summary, chapterName)
    remoteViews.setProgressBar(R.id.progress, 100, book.progressPercent(), false)
    bindButtons(remoteViews, seekSeconds)

    // A 40dp button plus its 8dp spacing, both across the row and above it.
    val buttonSize = context.dpToPxRounded(48F)
    val progressRow = context.dpToPxRounded(12F)
    val titleRow = textLineHeight(titleSize)
    val summaryRow = textLineHeight(summarySize)
    val availableHeight = if (sizeKnown) height - 2 * padding else Int.MAX_VALUE
    remoteViews.setVisible(R.id.buttons, true)
    remoteViews.setVisible(R.id.title, availableHeight >= buttonSize + titleRow)
    remoteViews.setVisible(R.id.progress, availableHeight >= buttonSize + titleRow + progressRow)
    remoteViews.setVisible(R.id.summary, availableHeight >= buttonSize + titleRow + progressRow + summaryRow)

    val buttonSlots = if (sizeKnown) (width - coverWidth - 3 * padding) / buttonSize else 5
    val chapterButtons = configStore.getChapterButtons(widgetId) && buttonSlots >= 5
    remoteViews.setVisible(R.id.previousChapter, chapterButtons)
    remoteViews.setVisible(R.id.nextChapter, chapterButtons)
    remoteViews.setVisible(R.id.rewind, buttonSlots >= 3)
    remoteViews.setVisible(R.id.fastForward, buttonSlots >= 3)
    return remoteViews
  }

  private fun bindButtons(
    remoteViews: RemoteViews,
    seekSeconds: Int,
  ) {
    fun bind(
      viewId: Int,
      action: WidgetButtonReceiver.Action,
    ) {
      remoteViews.setOnClickPendingIntent(viewId, WidgetButtonReceiver.pendingIntent(context, action))
    }
    bind(R.id.playPause, WidgetButtonReceiver.Action.PlayPause)
    bind(R.id.rewind, WidgetButtonReceiver.Action.Rewind)
    bind(R.id.fastForward, WidgetButtonReceiver.Action.FastForward)
    bind(R.id.previousChapter, WidgetButtonReceiver.Action.PreviousChapter)
    bind(R.id.nextChapter, WidgetButtonReceiver.Action.NextChapter)

    // The glyph colours live in the drawables, not here: a colour resolved in this process gets
    // baked into the RemoteViews and would not follow a system theme switch until the next redraw.
    val playIcon = if (playStateManager.playState == PlayStateManager.PlayState.Playing) {
      R.drawable.widget_ic_pause
    } else {
      R.drawable.widget_ic_play
    }
    remoteViews.setImageViewResource(R.id.playPause, playIcon)
    remoteViews.setContentDescription(R.id.fastForward, context.getString(StringsR.string.widget_skip_forward, seekSeconds))
    remoteViews.setContentDescription(R.id.rewind, context.getString(StringsR.string.widget_skip_back, seekSeconds))
  }

  /** The cover, center-cropped to the widget's cover shape with rounded corners; the placeholder art when there is none. */
  private suspend fun coverBitmap(
    cover: File?,
    width: Int,
    height: Int,
  ): Bitmap {
    suspend fun load(data: Any): Bitmap? {
      val request = ImageRequest.Builder(context)
        .data(data)
        .size(width, height)
        .scale(Scale.FILL)
        .transformations(RoundedCornersTransformation(context.dpToPxRounded(12F).toFloat()))
        .allowHardware(false)
        .build()
      return (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }
    return cover?.let { load(it) } ?: load(UiR.drawable.album_art)!!
  }

  private fun widgetWidth(opts: Bundle): Int {
    val key = if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
    return context.dpToPxRounded(opts.getInt(key).toFloat())
  }

  private fun widgetHeight(opts: Bundle): Int {
    val key = if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
    return context.dpToPxRounded(opts.getInt(key).toFloat())
  }

  private val isPortrait: Boolean
    get() = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}

private fun RemoteViews.setVisible(
  viewId: Int,
  visible: Boolean,
) {
  setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
}

/** A one-line TextView's height, font padding included. */
private fun textLineHeight(textSize: Float): Int {
  val metrics = Paint().apply { this.textSize = textSize }.fontMetricsInt
  return metrics.bottom - metrics.top
}

internal fun Book.progressPercent(): Int = if (duration > 0) (position * 100 / duration).toInt() else 0
