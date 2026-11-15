package voice.features.listeningLog

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.common.resolveChapterName
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningEvent
import voice.core.data.ListeningEventType
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.byMarkKey
import voice.core.data.durationMs
import voice.core.data.isCompletionMark
import voice.core.data.markForPosition
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.repo.ListeningEventRepo
import voice.core.data.repo.ListeningSessionRepo
import voice.core.playback.PlayerController
import voice.core.strings.R
import voice.core.ui.formatTime
import voice.navigation.Navigator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val COALESCE_WINDOW_MS = 2_000L

// A play within this window after a sleep-timer stop is flagged "resumed after sleep".
private const val RESUMED_AFTER_SLEEP_WINDOW_MS = 60L * 60 * 1000

@AssistedInject
class ListeningLogViewModel(
  private val sessionRepo: ListeningSessionRepo,
  private val eventRepo: ListeningEventRepo,
  private val bookRepo: BookRepository,
  private val chapterNameOverrideRepo: ChapterNameOverrideRepo,
  private val playerController: PlayerController,
  private val navigator: Navigator,
  private val context: Context,
  @Assisted private val bookId: BookId,
) : RetainedViewModel() {

  // Respect the device's 12/24-hour setting and locale date order (mirrors the sleep timer's
  // localTimeFormatter, which is internal to features:settings).
  private val timeFormatter = DateTimeFormatter.ofPattern(
    if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a",
    Locale.getDefault(),
  )
  private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

  @Composable
  fun viewState(): ListeningLogViewState {
    val sessions by remember {
      sessionRepo.sessions(bookId).map { all -> all.filterNot { it.isCompletionMark } }
    }.collectAsState(initial = emptyList())
    val events by remember { eventRepo.events(bookId) }.collectAsState(initial = emptyList())
    val book by remember { bookRepo.flow(bookId) }.collectAsState(initial = null)
    val overrides by remember { chapterNameOverrideRepo.overridesForBook(bookId) }.collectAsState(initial = emptyList())
    val groups = remember(sessions, events, book, overrides) {
      val offset = book?.content?.chapterNameOffset ?: 0
      val overrideMap = overrides.byMarkKey()
      buildGroups(sessions, events, book, offset, overrideMap)
    }
    return ListeningLogViewState(
      groups = groups,
      bookTitle = book?.content?.name ?: "",
    )
  }

  private fun buildGroups(
    sessions: List<ListeningSession>,
    events: List<ListeningEvent>,
    book: Book?,
    offset: Int,
    overrideMap: Map<Pair<String, Long>, String>,
  ): List<ListeningLogGroup> {
    // A session that starts shortly after a sleep-timer stop was probably started half-asleep.
    // Marking it lets the morning reader tell the awake stopping point (the Sleep-badged pause)
    // from listening that happened while dozing.
    val resumedAfterSleepIds = buildSet {
      val byStart = sessions.sortedBy { it.startedAt }
      byStart.forEachIndexed { index, session ->
        if (index == 0) return@forEachIndexed
        val previous = byStart[index - 1]
        val gapMs = session.startedAt.toEpochMilli() - previous.endedAt.toEpochMilli()
        if (previous.endReason == ListeningSessionEndReason.Sleep.id && gapMs in 0..RESUMED_AFTER_SLEEP_WINDOW_MS) {
          add(session.id)
        }
      }
    }
    val timed = buildList {
      sessions.forEach { session ->
        add(
          TimedEntry(
            timestamp = session.startedAt,
            entry = Raw.Play(session, resumedAfterSleep = session.id in resumedAfterSleepIds),
          ),
        )
        add(
          TimedEntry(
            timestamp = session.endedAt,
            entry = Raw.Pause(session),
          ),
        )
      }
      events
        .filter { ListeningEventType.fromId(it.type) != null }
        .forEach { event ->
          add(
            TimedEntry(
              timestamp = event.at,
              entry = Raw.Transport(event),
            ),
          )
        }
    }
      .sortedByDescending { it.timestamp }

    val coalesced = coalesce(timed)

    return coalesced
      .groupBy { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }
      .entries
      .sortedByDescending { it.key }
      .map { (date, dayEntries) ->
        ListeningLogGroup(
          dateLabel = date.format(dateFormatter),
          entries = dayEntries.map { it.toEntry(book, offset, overrideMap) },
        )
      }
  }

  // Merge runs of adjacent same-type Transport entries within the coalesce window into one,
  // keeping the newest entry's destination and the oldest-in-run's origin.
  private fun coalesce(sorted: List<TimedEntry>): List<TimedEntry> {
    val result = mutableListOf<TimedEntry>()
    var index = 0
    while (index < sorted.size) {
      val head = sorted[index]
      val headTransport = head.entry as? Raw.Transport
      if (headTransport == null) {
        result += head
        index++
        continue
      }
      var last = head
      var runEnd = index
      // Entries are sorted DESC, so head is the newest; walk towards older entries.
      while (runEnd + 1 < sorted.size) {
        val candidate = sorted[runEnd + 1]
        val candidateTransport = candidate.entry as? Raw.Transport ?: break
        val sameType = candidateTransport.event.type == headTransport.event.type
        // Per-step: each consecutive same-type event within the window joins the run, so a
        // continuous burst of taps collapses into one entry even if the whole run exceeds 2s.
        val withinWindow = last.timestamp.toEpochMilli() - candidate.timestamp.toEpochMilli() <= COALESCE_WINDOW_MS
        if (!sameType || !withinWindow) break
        last = candidate
        runEnd++
      }
      if (runEnd == index) {
        result += head
      } else {
        val oldest = (sorted[runEnd].entry as Raw.Transport).event
        result += head.copy(
          entry = Raw.Transport(
            event = headTransport.event.copy(fromPositionMs = oldest.fromPositionMs),
          ),
        )
      }
      index = runEnd + 1
    }
    return result
  }

  private fun TimedEntry.toEntry(
    book: Book?,
    offset: Int,
    overrideMap: Map<Pair<String, Long>, String>,
  ): ListeningLogEntry {
    val totalDuration = book?.duration ?: 0L
    return when (val raw = entry) {
      is Raw.Play -> {
        val session = raw.session
        val location = book.resolveLocation(session.chapterId, session.startPositionMs, offset, overrideMap)
        ListeningLogEntry.Play(
          id = "s${session.id}-play",
          timeLabel = timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter),
          chapterName = location.chapterName,
          positionLabel = location.positionLabel,
          remainingLabel = remainingLabel(totalDuration, session.startPositionMs),
          chapterId = session.chapterId,
          positionMs = session.startPositionMs,
          resumedAfterSleep = raw.resumedAfterSleep,
        )
      }
      is Raw.Pause -> {
        val session = raw.session
        val endChapterId = session.endChapterId ?: session.chapterId
        val location = book.resolveLocation(endChapterId, session.endPositionMs, offset, overrideMap)
        ListeningLogEntry.Pause(
          id = "s${session.id}-pause",
          timeLabel = timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter),
          endReason = ListeningSessionEndReason.fromId(session.endReason),
          chapterName = location.chapterName,
          positionLabel = location.positionLabel,
          remainingLabel = remainingLabel(totalDuration, session.endPositionMs),
          chapterId = endChapterId,
          positionMs = session.endPositionMs,
        )
      }
      is Raw.Transport -> {
        val event = raw.event
        val location = book.resolveLocation(event.chapterId, event.positionMs, offset, overrideMap)
        ListeningLogEntry.Transport(
          id = "e${event.id}",
          timeLabel = timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter),
          type = checkNotNull(ListeningEventType.fromId(event.type)),
          fromPositionMs = event.fromPositionMs,
          chapterName = location.chapterName,
          positionLabel = location.positionLabel,
          remainingLabel = remainingLabel(totalDuration, event.positionMs),
          chapterId = event.chapterId,
          positionMs = event.positionMs,
        )
      }
    }
  }

  private data class ResolvedLocation(
    val chapterName: String,
    val positionLabel: String,
  )

  private fun Book?.resolveLocation(
    id: ChapterId,
    positionMs: Long,
    offset: Int,
    overrideMap: Map<Pair<String, Long>, String>,
  ): ResolvedLocation {
    val unknown = context.getString(R.string.unknown_chapter)
    if (this == null) return ResolvedLocation(unknown, formatTime(positionMs))
    val index = chapters.indexOfFirst { it.id == id }
    if (index == -1) return ResolvedLocation(unknown, formatTime(positionMs))
    val chapter = chapters[index]
    // The one shared definition of "which mark contains this position" (used by the player screen
    // and bookmarks too), so all surfaces name the same position the same way.
    val mark = chapter.markForPosition(positionMs)
    val override = overrideMap[Pair(chapter.id.value, mark.startMs)]
    // Mirror the other surfaces' fallback: prefer the chapter's own name, then a localized
    // "Unknown chapter" — rather than a synthetic, mis-numbered "Chapter N".
    val name = resolveChapterName(mark.name ?: "", offset, override)
      .ifBlank { chapter.name.orEmpty() }
      .ifBlank { unknown }
    // Position INTO the displayed chapter mark, matching the player screen — for single-file
    // books the raw file position ("7:42:15") says nothing about where in the chapter you are.
    val positionInChapter = (positionMs - mark.startMs).coerceAtLeast(0L)
    return ResolvedLocation(name, formatTime(positionInChapter, mark.durationMs))
  }

  private fun remainingLabel(
    totalDurationMs: Long,
    positionMs: Long,
  ): String {
    val remainingMs = (totalDurationMs - positionMs).coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
    return context.getString(R.string.listening_log_remaining, hours, minutes)
  }

  fun onEntryClick(entry: ListeningLogEntry) {
    playerController.setPosition(entry.positionMs, entry.chapterId, tag = ListeningEventType.GoToChapter)
    navigator.goBack()
  }

  fun clearHistory() {
    scope.launch {
      sessionRepo.deleteAllForBook(bookId)
      eventRepo.deleteAllForBook(bookId)
    }
  }

  fun onClose() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): ListeningLogViewModel
  }
}

private data class TimedEntry(
  val timestamp: Instant,
  val entry: Raw,
)

private sealed interface Raw {
  data class Play(
    val session: ListeningSession,
    val resumedAfterSleep: Boolean = false,
  ) : Raw

  data class Pause(val session: ListeningSession) : Raw
  data class Transport(val event: ListeningEvent) : Raw
}

data class ListeningLogViewState(
  val groups: List<ListeningLogGroup>,
  val bookTitle: String,
)

data class ListeningLogGroup(
  val dateLabel: String,
  val entries: List<ListeningLogEntry>,
)

sealed interface ListeningLogEntry {
  val id: String
  val timeLabel: String
  val chapterId: ChapterId
  val positionMs: Long
  val chapterName: String
  val positionLabel: String
  val remainingLabel: String

  data class Play(
    override val id: String,
    override val timeLabel: String,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
    val resumedAfterSleep: Boolean = false,
  ) : ListeningLogEntry

  data class Pause(
    override val id: String,
    override val timeLabel: String,
    val endReason: ListeningSessionEndReason?,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry

  data class Transport(
    override val id: String,
    override val timeLabel: String,
    val type: ListeningEventType,
    val fromPositionMs: Long?,
    override val chapterName: String,
    override val positionLabel: String,
    override val remainingLabel: String,
    override val chapterId: ChapterId,
    override val positionMs: Long,
  ) : ListeningLogEntry
}
