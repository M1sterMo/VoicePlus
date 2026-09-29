package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.logging.api.Logger
import voice.core.ui.ImmutableFile
import voice.core.ui.formatTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: ImmutableFile?,
  val progress: Float,
  val id: BookId,
  val remainingTime: String,
  val finished: Boolean = false,
  /** Localized finish date; null while in progress, or when a finished book has no usable date. */
  val finishedOn: String? = null,
  val seriesGroup: String? = null,
  val seriesOrder: Int? = null,
  val seriesPart: String? = null,
  val importedSeries: String? = null,
  val addedAt: Long = 0L,
)

/**
 * @param finishedAt when the book last demonstrably reached its end according to listening sessions.
 * Preferred over [BookContent.lastPlayedAt], which every play bumps: a finished book replayed for a
 * second would otherwise re-date its finish to today.
 */
internal fun Book.toItemViewState(finishedAt: Instant? = null): BookOverviewItemViewState {
  val finished = category == BookOverviewCategory.FINISHED
  return BookOverviewItemViewState(
    name = content.name,
    author = content.author,
    cover = content.cover?.let(::ImmutableFile),
    id = id,
    progress = progress(),
    remainingTime = formatTime(realTimeRemainingMs()),
    finished = finished,
    finishedOn = if (finished) finishedOnLabel(finishedAt) else null,
    seriesGroup = content.seriesGroup,
    seriesOrder = content.seriesOrder,
    seriesPart = content.part,
    importedSeries = content.series,
    addedAt = content.addedAt.toEpochMilli(),
  )
}

private fun Book.finishedOnLabel(finishedAt: Instant?): String? {
  // Books scanned but never played, and rows seeded by old migrations, sit at the epoch; a book
  // marked complete by hand from that state has no real date to show.
  val instant = finishedAt ?: content.lastPlayedAt.takeIf { it > Instant.EPOCH } ?: return null
  return instant
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

/**
 * Wall-clock listening time left at this book's playback speed, not raw audio time —
 * at 2x, a 2h remainder is one real hour (GitHub issue #6).
 */
internal fun Book.realTimeRemainingMs(): Long {
  val remaining = (duration - position).coerceAtLeast(0)
  val speed = content.playbackSpeed
  return if (speed > 0f) (remaining / speed.toDouble()).toLong() else remaining
}

private fun Book.progress(): Float {
  val globalPosition = position
  val totalDuration = duration
  val progress = globalPosition.toFloat() / totalDuration.toFloat()
  if (progress < 0F) {
    Logger.w("Couldn't determine progress for book=$this")
  }
  return progress.coerceIn(0F, 1F)
}
