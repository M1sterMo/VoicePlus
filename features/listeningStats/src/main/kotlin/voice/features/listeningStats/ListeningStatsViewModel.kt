package voice.features.listeningStats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import voice.core.data.isCompletion
import voice.core.data.isCompletionMark
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ListeningSessionRepo
import voice.core.ui.ImmutableFile
import voice.navigation.Navigator
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

@Inject
class ListeningStatsViewModel(
  private val sessionRepo: ListeningSessionRepo,
  private val bookRepo: BookRepository,
  private val navigator: Navigator,
) {

  /** Null while the first aggregation is still in flight, so the screen can tell "loading" from "no data". */
  @Composable
  fun viewState(): ListeningStatsViewState? {
    val combined by remember {
      // Only stable library metadata is consumed from bookRepo, but its flow re-emits on every
      // position save (~1/second while playing). Deriving and de-duplicating it here keeps the
      // full-table aggregation below from re-running once a second with the screen open.
      val library = bookRepo.flow()
        .map { books ->
          books.map { book ->
            val lastChapter = book.chapters.lastOrNull()
            LibraryBookInfo(
              id = book.id,
              name = book.content.name,
              cover = book.content.cover?.let(::ImmutableFile),
              isCompleted = book.isCompleted(),
              lastPlayedAt = book.content.lastPlayedAt,
              lastChapter = lastChapter?.id,
              lastChapterDurationMs = lastChapter?.duration ?: 0L,
            )
          }
        }
        .distinctUntilChanged()
      combine(sessionRepo.allSessions(), library) { sessions, books ->
        computeStats(
          sessions = sessions,
          books = books,
          zone = ZoneId.systemDefault(),
          today = LocalDate.now(),
          locale = Locale.getDefault(),
        )
      }
    }.collectAsState(initial = null)
    return combined
  }

  fun onClose() {
    navigator.goBack()
  }
}

data class LibraryBookInfo(
  val id: BookId,
  val name: String,
  val cover: ImmutableFile?,
  val isCompleted: Boolean,
  val lastChapter: ChapterId? = null,
  val lastChapterDurationMs: Long = 0L,
  val lastPlayedAt: Instant = Instant.EPOCH,
)

internal fun computeStats(
  sessions: List<ListeningSession>,
  books: List<LibraryBookInfo>,
  zone: ZoneId,
  today: LocalDate,
  locale: Locale = Locale.getDefault(),
): ListeningStatsViewState {
  val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
  val finishedBooks = finishedBooks(sessions, books, zone, dateFormatter)
  // Completion marks date a finish; they are not listening, so they stay out of every time metric.
  val listening = sessions.filterNot { it.isCompletionMark }
  // Count TITLES, not library rows: the same audiobook can exist under several identities
  // (folder-level book alongside a file-level or parent-folder one), and the Finished shelf
  // collapses those — the "N of M completed" card must agree with it.
  val booksInLibrary = books.distinctBy { it.name }.size
  val booksCompleted = finishedBooks.size

  if (listening.isEmpty()) {
    return ListeningStatsViewState.Empty.copy(
      booksCompleted = booksCompleted,
      booksInLibrary = booksInLibrary,
      finishedBooks = finishedBooks,
    )
  }

  val weekFields = WeekFields.of(locale)
  val dailyTotals = dailyTotals(listening, zone)

  // Summary metrics
  val totalLifetimeMs = listening.sumOf { it.durationMs }
  val todayMs = dailyTotals[today] ?: 0L
  val weekStart = today.with(weekFields.dayOfWeek(), 1)
  val thisWeekMs = dailyTotals.entries.filter { it.key >= weekStart }.sumOf { it.value }
  val thisMonthMs = dailyTotals.entries
    .filter { it.key.month == today.month && it.key.year == today.year }
    .sumOf { it.value }

  val previousWeekStart = weekStart.minusWeeks(1)
  val previousWeekEnd = previousWeekStart.plusDays(today.toEpochDay() - weekStart.toEpochDay())
  val previousWeekMs = dailyTotals.entries
    .filter { it.key in previousWeekStart..previousWeekEnd }
    .sumOf { it.value }
  val weekChangePercent = previousWeekMs.takeIf { it > 0 }?.let {
    (((thisWeekMs - it) * 100.0) / it).roundToInt()
  }

  val firstDay = dailyTotals.keys.min()
  val avgSessionMs = totalLifetimeMs / listening.size

  // Longest day
  val longestEntry = dailyTotals.maxByOrNull { it.value }
  val longestDayMs = longestEntry?.value ?: 0L
  val longestDayLabel = longestEntry?.key?.format(dateFormatter)

  val monthTotals = dailyTotals.entries
    .groupBy({ YearMonth.from(it.key) }, { it.value })
    .mapValues { (_, totals) -> totals.sum() }

  // Biggest month, over the whole history — it's a lifetime record, not a chart bucket.
  val biggestMonth = monthTotals.maxByOrNull { it.value }
  val biggestMonthMs = biggestMonth?.value ?: 0L
  val biggestMonthLabel = biggestMonth?.key
    ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

  val (currentStreak, longestStreak) = computeStreaks(dailyTotals.keys, today)

  val bestDayOfWeek = dailyTotals.entries
    .groupBy({ it.key.dayOfWeek }, { it.value })
    .mapValues { (_, totals) -> totals.sum() }
    .maxByOrNull { it.value }
    ?.key
    ?.getDisplayName(TextStyle.FULL, locale)

  // Monthly chart — last 12 months. Narrow (single-letter) labels so all 12 fit on a phone.
  val monthlyData = (11 downTo 0).map { monthsBack ->
    val month = YearMonth.from(today.minusMonths(monthsBack.toLong()))
    ChartDataPoint(
      label = month.month.getDisplayName(TextStyle.NARROW, locale),
      valueMs = monthTotals[month] ?: 0L,
    )
  }

  return ListeningStatsViewState(
    totalLifetimeMs = totalLifetimeMs,
    todayMs = todayMs,
    thisWeekMs = thisWeekMs,
    thisMonthMs = thisMonthMs,
    weekChangePercent = weekChangePercent,
    firstListeningDateLabel = firstDay.format(dateFormatter),
    booksCompleted = booksCompleted,
    booksInLibrary = booksInLibrary,
    monthlyData = monthlyData,
    avgSessionMs = avgSessionMs,
    longestDayMs = longestDayMs,
    longestDayLabel = longestDayLabel,
    biggestMonthMs = biggestMonthMs,
    biggestMonthLabel = biggestMonthLabel,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    bestDayOfWeek = bestDayOfWeek,
    finishedBooks = finishedBooks,
  )
}

/**
 * One shelf entry per finished TITLE, newest finish first. The same audiobook can exist under
 * several library identities (a folder-level book alongside a file-level or parent-folder one, from
 * grants added over time), so hours are summed across all same-named copies. There is no explicit
 * "completed at" timestamp: the finish date is the last
 * session that demonstrably REACHED the end of the book, so a later re-listen that stops mid-book
 * cannot bump a March finish to August. For a book finished before session tracking existed both
 * duration and date stay empty rather than showing zeros.
 */
private fun finishedBooks(
  sessions: List<ListeningSession>,
  books: List<LibraryBookInfo>,
  zone: ZoneId,
  dateFormatter: DateTimeFormatter,
): List<FinishedBookStats> {
  if (books.isEmpty()) return emptyList()
  val attribution = BookAttribution(books)
  val sessionsByBook = sessions.groupBy { attribution.attribute(it.bookId) }

  return books
    .groupBy { it.name }
    .mapNotNull { (name, copies) ->
      val copySessions = copies.map { it to sessionsByBook[it.id].orEmpty() }
      val allSessions = copySessions.flatMap { it.second }.filterNot { it.isCompletionMark }
      val nativeCopySessions = copySessions.map { (copy, s) -> copy to s.filter { it.bookId == copy.id } }
      val explicitCompletions = nativeCopySessions.flatMap { (_, s) -> s.filter { it.isCompletion } }
      val legacyCompletions = nativeCopySessions.flatMap { (copy, s) -> legacyCompletionSessions(copy, s) }
      val completions = explicitCompletions + legacyCompletions
      // Books marked completed before marks were recorded fall back to when they were last touched,
      // the same date the library shows, so they still sort among the dated finishes.
      val completedAt = completions.maxOfOrNull { it.endedAt }
        ?: copies.filter { it.isCompleted }.maxOfOrNull { it.lastPlayedAt }?.takeIf { it > Instant.EPOCH }
      // Current position can move away from the end when a relisten begins, so completion evidence
      // also keeps the title on this historical shelf.
      if (copies.none { it.isCompleted } && completions.isEmpty()) return@mapNotNull null
      val finishedOn = completedAt?.atZone(zone)?.toLocalDate()
      FinishedBookStats(
        name = name,
        cover = copies.firstNotNullOfOrNull { it.cover },
        listenedMs = allSessions.sumOf { it.durationMs },
        sessionCount = allSessions.size,
        // A legacy near-end record can establish that a finish happened, but not an exact count.
        completionCount = explicitCompletions.size.takeIf { it > 0 && legacyCompletions.isEmpty() },
        firstListenedDateLabel = allSessions.minOfOrNull { it.startedAt }
          ?.atZone(zone)
          ?.toLocalDate()
          ?.format(dateFormatter),
        finishedDate = finishedOn,
        finishedDateLabel = finishedOn?.format(dateFormatter),
      )
    }
    // compareByDescending flips the comparator, so nullsFirst ends up putting undated books last.
    .sortedWith(compareByDescending(nullsFirst()) { it.finishedDate })
}

/** Pre-end-reason sessions that demonstrably reached the final chapter's end. */
private fun legacyCompletionSessions(
  book: LibraryBookInfo,
  bookSessions: List<ListeningSession>,
): List<ListeningSession> {
  if (book.lastChapter == null || book.lastChapterDurationMs <= 0) return emptyList()
  // Chapter identity is compared by document path only when the raw ids differ: the same file
  // appears under different URI shapes (tree-grant vs plain document) depending on which folder
  // grant recorded the session.
  val lastChapterPath by lazy { book.lastChapter.value.contentDocumentPath() }
  // "-60s" alone goes vacuous on a short outro chapter (any 3-second touch would count as
  // finishing); never accept less than half the chapter.
  val threshold = (book.lastChapterDurationMs - 60_000).coerceAtLeast(book.lastChapterDurationMs / 2)
  return bookSessions
    .filter { session ->
      if (session.endReason != null) return@filter false
      val endChapter = session.endChapterId ?: session.chapterId
      session.endPositionMs >= threshold &&
        (endChapter == book.lastChapter || endChapter.value.contentDocumentPath() == lastChapterPath)
    }
}

/**
 * Sessions recorded against a book identity that no longer exists (the scanner has re-keyed a file
 * into a folder book, or an old file-level grant died) are re-attributed by SAF document path: a
 * session whose document path equals or lies inside a library book's document path belongs to that
 * book. Deepest (most specific) book wins, so a nested book beats its parent-folder book. Decoded
 * paths and verdicts are cached — attribution runs once per distinct session book id, not per
 * session row.
 */
private class BookAttribution(private val books: List<LibraryBookInfo>) {
  private val liveIds = books.mapTo(hashSetOf()) { it.id }
  private val pathById = books.mapNotNull { book ->
    book.id.value.contentDocumentPath()?.let { book.id to it }
  }
  private val cache = HashMap<BookId, BookId>()

  fun attribute(sessionBookId: BookId): BookId = cache.getOrPut(sessionBookId) {
    if (sessionBookId in liveIds) return@getOrPut sessionBookId
    val sessionPath = sessionBookId.value.contentDocumentPath() ?: return@getOrPut sessionBookId
    pathById
      .filter { (_, path) -> sessionPath == path || sessionPath.startsWith("$path/") }
      .maxByOrNull { (_, path) -> path.length }
      ?.first
      ?: sessionBookId
  }
}

/** The decoded SAF document path of a content URI, e.g. "primary:Download/audiobooks/Book". */
private fun String.contentDocumentPath(): String? {
  val encoded = substringAfter("/document/", "").substringBefore('?').takeIf { it.isNotEmpty() } ?: return null
  // URLDecoder decodes a literal '+' as a space; document ids keep '+' literal, so protect it.
  return runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrNull()
}

/**
 * Listening time per local calendar day. A session is split at midnight rather than billed whole to
 * the day it started on — bedtime listening routinely crosses midnight, and crediting a 23:30→00:45
 * session entirely to the earlier day leaves the new day showing zero (and unable to extend a streak).
 */
internal fun dailyTotals(
  sessions: List<ListeningSession>,
  zone: ZoneId,
): Map<LocalDate, Long> = buildMap {
  sessions.forEach { session ->
    session.dailyShares(zone).forEach { (date, ms) ->
      merge(date, ms, Long::plus)
    }
  }
}

private fun ListeningSession.dailyShares(zone: ZoneId): Map<LocalDate, Long> {
  val startDate = startedAt.atZone(zone).toLocalDate()
  val endDate = endedAt.atZone(zone).toLocalDate()
  val spanMs = endedAt.toEpochMilli() - startedAt.toEpochMilli()
  // Same-day, or a duration that doesn't match the recorded span (finalized/interrupted sessions):
  // don't invent a distribution, bill it where it started.
  if (startDate == endDate || spanMs <= 0L) return mapOf(startDate to durationMs)

  val shares = LinkedHashMap<LocalDate, Long>()
  var assigned = 0L
  var date = startDate
  while (date <= endDate) {
    // Real instants, so DST-shortened and -lengthened days apportion correctly.
    val dayStart = maxOf(startedAt, date.atStartOfDay(zone).toInstant())
    val dayEnd = minOf(endedAt, date.plusDays(1).atStartOfDay(zone).toInstant())
    val overlapMs = dayEnd.toEpochMilli() - dayStart.toEpochMilli()
    if (overlapMs > 0L) {
      val share = durationMs * overlapMs / spanMs
      shares[date] = share
      assigned += share
    }
    date = date.plusDays(1)
  }
  // Integer division loses up to a millisecond per day; keep the parts summing to the recorded total.
  shares.keys.lastOrNull()?.let { last ->
    shares[last] = shares.getValue(last) + (durationMs - assigned)
  }
  return shares
}

/** @return current streak (counting back from [today]) to longest streak ever. */
internal fun computeStreaks(
  daysWithListening: Set<LocalDate>,
  today: LocalDate,
): Pair<Int, Int> {
  if (daysWithListening.isEmpty()) return 0 to 0

  val sortedDays = daysWithListening.sorted()
  var longest = 1
  var run = 1
  for (i in 1 until sortedDays.size) {
    run = if (sortedDays[i].minusDays(1) == sortedDays[i - 1]) run + 1 else 1
    if (run > longest) longest = run
  }

  // Count back from today, or from yesterday when today hasn't been listened to yet — otherwise the
  // streak would read 0 every morning until the user next pressed play.
  var check = if (today in daysWithListening) today else today.minusDays(1)
  var current = 0
  while (check in daysWithListening) {
    current++
    check = check.minusDays(1)
  }
  return current to longest
}

private fun Book.isCompleted(): Boolean {
  return duration > 0 && position >= duration - 5_000L
}

data class ChartDataPoint(
  val label: String,
  val valueMs: Long,
)

data class FinishedBookStats(
  val name: String,
  val cover: ImmutableFile?,
  val listenedMs: Long,
  val sessionCount: Int,
  val completionCount: Int?,
  val firstListenedDateLabel: String?,
  val finishedDate: LocalDate?,
  val finishedDateLabel: String?,
)

data class ListeningStatsViewState(
  val totalLifetimeMs: Long,
  val todayMs: Long,
  val thisWeekMs: Long,
  val thisMonthMs: Long,
  val weekChangePercent: Int?,
  val firstListeningDateLabel: String?,
  val booksCompleted: Int,
  val booksInLibrary: Int,
  val monthlyData: List<ChartDataPoint>,
  val avgSessionMs: Long,
  val longestDayMs: Long,
  val longestDayLabel: String?,
  val biggestMonthMs: Long,
  val biggestMonthLabel: String?,
  val currentStreak: Int,
  val longestStreak: Int,
  val bestDayOfWeek: String?,
  val finishedBooks: List<FinishedBookStats>,
) {
  companion object {
    val Empty = ListeningStatsViewState(
      totalLifetimeMs = 0L,
      todayMs = 0L,
      thisWeekMs = 0L,
      thisMonthMs = 0L,
      weekChangePercent = null,
      firstListeningDateLabel = null,
      booksCompleted = 0,
      booksInLibrary = 0,
      monthlyData = emptyList(),
      avgSessionMs = 0L,
      longestDayMs = 0L,
      longestDayLabel = null,
      biggestMonthMs = 0L,
      biggestMonthLabel = null,
      currentStreak = 0,
      longestStreak = 0,
      bestDayOfWeek = null,
      finishedBooks = emptyList(),
    )
  }
}
