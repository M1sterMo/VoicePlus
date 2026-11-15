package voice.features.listeningStats

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * The stats arithmetic is pure calendar maths with no UI — and until this file existed, none of it
 * was exercised. Every case below is a user-visible number on the statistics screen.
 */
class ListeningStatsTest {

  private val zone: ZoneId = ZoneId.of("Europe/Berlin")
  private val today: LocalDate = LocalDate.of(2026, 8, 2)

  private fun session(
    date: LocalDate,
    startHour: Int,
    durationMinutes: Long,
    startMinute: Int = 0,
    bookId: BookId = BookId("content://book"),
    chapterId: ChapterId = ChapterId("content://chapter"),
    endPositionMs: Long? = null,
    endReason: Int? = null,
  ): ListeningSession {
    val start = date.atTime(startHour, startMinute).atZone(zone).toInstant()
    val durationMs = durationMinutes * 60_000
    return ListeningSession(
      bookId = bookId,
      chapterId = chapterId,
      startedAt = start,
      endedAt = start.plusMillis(durationMs),
      startPositionMs = 0,
      endPositionMs = endPositionMs ?: durationMs,
      durationMs = durationMs,
      endReason = endReason,
    )
  }

  private fun book(
    id: String,
    name: String = id,
    isCompleted: Boolean = false,
    lastChapter: ChapterId? = null,
    lastChapterDurationMs: Long = 0L,
  ) = LibraryBookInfo(
    id = BookId(id),
    name = name,
    cover = null,
    isCompleted = isCompleted,
    lastChapter = lastChapter,
    lastChapterDurationMs = lastChapterDurationMs,
  )

  private val defaultBooks = listOf(
    book("content://book", name = "The book", isCompleted = true),
    book("content://other"),
    book("content://third"),
  )

  private fun stats(
    sessions: List<ListeningSession>,
    books: List<LibraryBookInfo> = defaultBooks,
  ) = computeStats(
    sessions = sessions,
    books = books,
    zone = zone,
    today = today,
    locale = Locale.UK,
  )

  @Test
  fun `a streak that ended yesterday is still the current streak`() {
    // The user listened for five days straight but has not pressed play yet today. Before the fix
    // the count-back started at today, found nothing, and reported no streak at all every morning.
    val sessions = (1..5).map { session(today.minusDays(it.toLong()), startHour = 20, durationMinutes = 30) }
    val result = stats(sessions)
    result.currentStreak shouldBe 5
    result.longestStreak shouldBe 5
  }

  @Test
  fun `listening today extends the streak`() {
    val sessions = (0..2).map { session(today.minusDays(it.toLong()), startHour = 20, durationMinutes = 30) }
    stats(sessions).currentStreak shouldBe 3
  }

  @Test
  fun `a gap of two days breaks the current streak but keeps the longest`() {
    val sessions = (2..5).map { session(today.minusDays(it.toLong()), startHour = 20, durationMinutes = 30) }
    val result = stats(sessions)
    result.currentStreak shouldBe 0
    result.longestStreak shouldBe 4
  }

  @Test
  fun `a session crossing midnight is split across both days`() {
    // 23:30 -> 00:30. Bedtime listening is the app's core use case; billing the whole hour to the
    // previous day left "Today" at zero and stopped the new day counting toward a streak.
    val sessions = listOf(session(today.minusDays(1), startHour = 23, startMinute = 30, durationMinutes = 60))
    val result = stats(sessions)

    result.todayMs shouldBe 30 * 60_000L
    result.totalLifetimeMs shouldBe 60 * 60_000L
    result.currentStreak shouldBe 2
    // The split parts still add up to exactly what was recorded.
    dailyTotals(sessions, zone).values.sum() shouldBe 60 * 60_000L
  }

  @Test
  fun `a session inside one day is billed whole to that day`() {
    val sessions = listOf(session(today, startHour = 9, durationMinutes = 45))
    val result = stats(sessions)
    result.todayMs shouldBe 45 * 60_000L
    dailyTotals(sessions, zone).keys shouldBe setOf(today)
  }

  @Test
  fun `a session spanning the spring-forward DST gap keeps its recorded duration`() {
    // Europe/Berlin skips 02:00->03:00 on 2026-03-29, so this day is 23 hours long.
    val dstDay = LocalDate.of(2026, 3, 28)
    val start = LocalDateTime.of(dstDay, java.time.LocalTime.of(23, 0)).atZone(zone).toInstant()
    val durationMs = 5L * 60 * 60 * 1000 // 5 hours of wall clock, ending 05:00 next day
    val session = ListeningSession(
      bookId = BookId("content://book"),
      chapterId = ChapterId("content://chapter"),
      startedAt = start,
      endedAt = start.plusMillis(durationMs),
      startPositionMs = 0,
      endPositionMs = durationMs,
      durationMs = durationMs,
    )
    val totals = dailyTotals(listOf(session), zone)
    totals.values.sum() shouldBe durationMs
    totals.keys shouldBe setOf(dstDay, dstDay.plusDays(1))
    // One hour before midnight, the rest after.
    totals.getValue(dstDay) shouldBe 60 * 60_000L
  }

  @Test
  fun `today, this week and this month cover the right ranges`() {
    // today is Sunday 2 Aug 2026; with a UK (Monday-start) week this week runs Mon 27 Jul - Sun 2 Aug,
    // so the week straddles the month boundary while August itself contains only today.
    val sessions = listOf(
      session(today, startHour = 10, durationMinutes = 20), // Sun 2 Aug — this week, this month
      session(today.minusDays(2), startHour = 10, durationMinutes = 30), // Fri 31 Jul — this week, last month
      session(today.minusDays(9), startHour = 10, durationMinutes = 40), // Fri 24 Jul — last week
      session(LocalDate.of(2026, 7, 15), startHour = 10, durationMinutes = 50), // earlier in July
    )
    val result = stats(sessions)

    result.todayMs shouldBe 20 * 60_000L
    result.thisWeekMs shouldBe 50 * 60_000L
    result.thisMonthMs shouldBe 20 * 60_000L
    result.totalLifetimeMs shouldBe 140 * 60_000L
  }

  @Test
  fun `average session divides the total by the number of recorded sessions`() {
    val sessions = listOf(
      session(today, startHour = 10, durationMinutes = 30),
      session(today.minusDays(2), startHour = 10, durationMinutes = 90),
    )
    stats(sessions).avgSessionMs shouldBe 60 * 60_000L
  }

  @Test
  fun `week change compares week to date with the same days last week`() {
    val thisWeek = session(today, startHour = 10, durationMinutes = 60)
    val samePeriodLastWeek = session(today.minusWeeks(1), startHour = 10, durationMinutes = 50)

    stats(listOf(thisWeek, samePeriodLastWeek)).weekChangePercent shouldBe 20
  }

  @Test
  fun `finished books carry their listening time and completion date, newest first`() {
    val first = BookId("content://first")
    val second = BookId("content://second")
    val books = listOf(
      book("content://first", name = "First book", isCompleted = true),
      book("content://second", name = "Second book", isCompleted = true),
      book("content://third", name = "Unfinished"),
    )
    val sessions = listOf(
      session(
        today.minusDays(10),
        startHour = 9,
        durationMinutes = 20,
        bookId = first,
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
      session(
        today,
        startHour = 10,
        durationMinutes = 40,
        bookId = second,
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
      session(today.minusDays(1), startHour = 11, durationMinutes = 30, bookId = second),
    )

    val finished = stats(sessions, books).finishedBooks
    finished.map { it.name } shouldBe listOf("Second book", "First book")
    finished[0].listenedMs shouldBe 70 * 60_000L
    finished[0].finishedDate shouldBe today
    finished[1].listenedMs shouldBe 20 * 60_000L
  }

  @Test
  fun `a book marked completed is dated by its mark, which is not listening`() {
    val marked = BookId("content://marked")
    val books = listOf(
      book("content://earlier", name = "Earlier", isCompleted = true),
      book("content://marked", name = "Marked", isCompleted = true),
    )
    val sessions = listOf(
      session(
        today.minusDays(5),
        startHour = 9,
        durationMinutes = 20,
        bookId = BookId("content://earlier"),
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
      session(today.minusDays(3), startHour = 21, durationMinutes = 45, bookId = marked),
      session(today, startHour = 8, durationMinutes = 0, bookId = marked, endReason = ListeningSessionEndReason.MarkedCompleted.id),
    )

    val state = stats(sessions, books)
    state.finishedBooks.map { it.name } shouldBe listOf("Marked", "Earlier")
    with(state.finishedBooks[0]) {
      finishedDate shouldBe today
      completionCount shouldBe 1
      sessionCount shouldBe 1
      listenedMs shouldBe 45 * 60_000L
    }
    state.todayMs shouldBe 0L
    state.currentStreak shouldBe 0
    state.avgSessionMs shouldBe (20 + 45) * 60_000L / 2
  }

  @Test
  fun `a book marked completed before marks existed is dated by its last listen`() {
    val unmarked = BookId("content://unmarked")
    val books = listOf(
      book("content://dated", name = "Dated", isCompleted = true),
      book("content://unmarked", name = "Unmarked", isCompleted = true),
    )
    val sessions = listOf(
      session(
        today.minusDays(9),
        startHour = 9,
        durationMinutes = 20,
        bookId = BookId("content://dated"),
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
      session(today.minusDays(4), startHour = 9, durationMinutes = 50, bookId = unmarked),
      session(today.minusDays(2), startHour = 20, durationMinutes = 30, bookId = unmarked),
    )

    val finished = stats(sessions, books).finishedBooks
    finished.map { it.name } shouldBe listOf("Unmarked", "Dated")
    finished[0].finishedDate shouldBe today.minusDays(2)
    finished[0].completionCount shouldBe null
  }

  @Test
  fun `a book finished before session tracking existed shows without duration or date, after dated ones`() {
    val tracked = BookId("content://tracked")
    val books = listOf(
      book("content://tracked", name = "Tracked", isCompleted = true),
      book("content://legacy", name = "Legacy", isCompleted = true),
    )
    val sessions = listOf(session(today, startHour = 10, durationMinutes = 30, bookId = tracked))

    val finished = stats(sessions, books).finishedBooks
    finished.map { it.name } shouldBe listOf("Tracked", "Legacy")
    finished[1].listenedMs shouldBe 0L
    finished[1].finishedDate shouldBe null
    finished[1].finishedDateLabel shouldBe null
    finished[1].completionCount shouldBe null
    finished[1].firstListenedDateLabel shouldBe null
  }

  @Test
  fun `a re-listen that stops mid-book does not bump the finish date`() {
    // Finished in March (the session reaches the end of the last chapter); re-listened in August but
    // stopped mid-book. The shelf must keep the March finish date, not jump to August. The book's
    // chapter is a tree-grant URI while the sessions recorded a plain document URI for the same file —
    // completion matching must bridge the two shapes via the document path.
    val auth = "content://com.android.externalstorage.documents"
    val bid = BookId("$auth/tree/primary%3AAudiobooks/document/primary%3AAudiobooks%2FBook")
    val treeChapter = ChapterId("$auth/tree/primary%3AAudiobooks/document/primary%3AAudiobooks%2FBook%2Ffile.m4b")
    val plainChapter = ChapterId("$auth/document/primary%3AAudiobooks%2FBook%2Ffile.m4b")
    val books = listOf(
      book(
        bid.value,
        name = "The book",
        isCompleted = true,
        lastChapter = treeChapter,
        lastChapterDurationMs = 60 * 60_000L,
      ),
    )
    val finishDay = LocalDate.of(2026, 3, 20)
    val sessions = listOf(
      // ends at 60m = chapter end
      session(finishDay, startHour = 20, durationMinutes = 60, bookId = bid, chapterId = plainChapter),
      // re-listen, stops mid-book
      session(today, startHour = 10, durationMinutes = 30, bookId = bid, chapterId = plainChapter),
    )

    val result = stats(sessions, books).finishedBooks.single()
    result.finishedDate shouldBe finishDay
    result.listenedMs shouldBe 90 * 60_000L
  }

  @Test
  fun `end of book sessions count completions and populate book details`() {
    val bid = BookId("content://book")
    val sessions = listOf(
      session(
        LocalDate.of(2026, 1, 8),
        startHour = 10,
        durationMinutes = 60,
        bookId = bid,
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
      session(
        LocalDate.of(2026, 4, 12),
        startHour = 10,
        durationMinutes = 60,
        bookId = bid,
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
      session(
        today,
        startHour = 10,
        durationMinutes = 60,
        bookId = bid,
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
      session(
        today,
        startHour = 12,
        durationMinutes = 20,
        bookId = bid,
        endReason = ListeningSessionEndReason.Paused.id,
      ),
    )

    val result = stats(sessions).finishedBooks.single()
    result.completionCount shouldBe 3
    result.sessionCount shouldBe 4
    result.firstListenedDateLabel shouldBe "8 Jan 2026"
    result.finishedDateLabel shouldBe "2 Aug 2026"
  }

  @Test
  fun `a finished book stays in history after a relisten moves its current position`() {
    val books = listOf(book("content://book", name = "The book", isCompleted = false))
    val finish = session(
      LocalDate.of(2026, 3, 20),
      startHour = 20,
      durationMinutes = 60,
      endReason = ListeningSessionEndReason.EndOfBook.id,
    )

    val result = stats(listOf(finish), books)
    result.finishedBooks.single().name shouldBe "The book"
    result.finishedBooks.single().completionCount shouldBe 1
    result.booksCompleted shouldBe 1
  }

  @Test
  fun `legacy near-end sessions establish a finish without claiming an exact count`() {
    val bid = BookId("content://book")
    val books = listOf(
      book(
        bid.value,
        name = "The book",
        isCompleted = true,
        lastChapter = ChapterId("content://chapter"),
        lastChapterDurationMs = 60 * 60_000L,
      ),
    )
    val sessions = listOf(
      session(
        LocalDate.of(2026, 2, 1),
        startHour = 10,
        durationMinutes = 59,
        bookId = bid,
      ),
      session(
        LocalDate.of(2026, 5, 1),
        startHour = 10,
        durationMinutes = 59,
        bookId = bid,
      ),
      session(
        today,
        startHour = 10,
        durationMinutes = 59,
        bookId = bid,
        endReason = ListeningSessionEndReason.Paused.id,
      ),
    )

    val result = stats(sessions, books).finishedBooks.single()
    result.completionCount shouldBe null
    result.finishedDateLabel shouldBe "1 May 2026"
  }

  @Test
  fun `orphan sessions are attributed to the deepest library book containing their document path`() {
    // The scanner has since re-keyed the file into a folder book; sessions recorded against the old
    // file-level id must follow the content, and the nested Book 3 folder must beat the parent folder.
    val base = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks/document/"
    val parent = base + "primary%3AAudiobooks%2FDCC"
    val folderBook = base + "primary%3AAudiobooks%2FDCC%2FBook%203"
    val deadFileId = base + "primary%3AAudiobooks%2FDCC%2FBook%203%2Fbook.m4b"
    val books = listOf(
      book(parent, name = "DCC omnibus", isCompleted = true),
      book(folderBook, name = "Book 3", isCompleted = true),
    )
    val sessions = listOf(
      session(
        today,
        startHour = 10,
        durationMinutes = 60,
        bookId = BookId(deadFileId),
        endReason = ListeningSessionEndReason.EndOfBook.id,
      ),
    )

    val finished = stats(sessions, books).finishedBooks
    finished.single { it.name == "Book 3" }.listenedMs shouldBe 60 * 60_000L
    finished.single { it.name == "Book 3" }.completionCount shouldBe null
    finished.single { it.name == "DCC omnibus" }.listenedMs shouldBe 0L
  }

  @Test
  fun `a short outro chapter does not make every touch of it count as finishing`() {
    // Threshold "duration - 60s" would be negative for a 30s outro; the guard requires at least
    // half the chapter, so a 3-second August touch cannot re-date a March finish.
    val bid = BookId("content://book")
    val books = listOf(
      book(
        "content://book",
        name = "The book",
        isCompleted = true,
        lastChapter = ChapterId("content://chapter"),
        lastChapterDurationMs = 30_000L,
      ),
    )
    val finishDay = LocalDate.of(2026, 3, 20)
    val sessions = listOf(
      session(finishDay, startHour = 20, durationMinutes = 1, bookId = bid, endPositionMs = 25_000), // real finish
      session(today, startHour = 10, durationMinutes = 1, bookId = bid, endPositionMs = 3_000), // brief touch
    )

    stats(sessions, books).finishedBooks.single().finishedDate shouldBe finishDay
  }

  @Test
  fun `hours on a duplicate identity do not invent a completion date`() {
    val historyHolder = BookId("content://history-holder")
    val books = listOf(
      book("content://completed-copy", name = "Same Book", isCompleted = true),
      book("content://history-holder", name = "Same Book"),
    )
    val sessions = listOf(session(today, startHour = 10, durationMinutes = 45, bookId = historyHolder))

    val entry = stats(sessions, books).finishedBooks.single()
    entry.name shouldBe "Same Book"
    entry.listenedMs shouldBe 45 * 60_000L
    entry.finishedDate shouldBe null
  }

  @Test
  fun `duplicate library copies of the same title collapse to the copy with the listening history`() {
    val listened = BookId("content://listened")
    val books = listOf(
      book("content://listened", name = "Same Book", isCompleted = true),
      book("content://bare-copy", name = "Same Book", isCompleted = true),
    )
    val sessions = listOf(session(today, startHour = 10, durationMinutes = 30, bookId = listened))

    val finished = stats(sessions, books).finishedBooks
    finished.map { it.name } shouldBe listOf("Same Book")
    finished.single().listenedMs shouldBe 30 * 60_000L
  }

  @Test
  fun `no sessions yields an empty view state that still reports library counts and finished books`() {
    val result = stats(emptyList())
    result.booksInLibrary shouldBe 3
    result.booksCompleted shouldBe 1
    result.finishedBooks.map { it.name } shouldBe listOf("The book")
    result.currentStreak shouldBe 0
    result.monthlyData shouldBe emptyList()
  }

  @Test
  fun `the chart covers the trailing 12 months ending today`() {
    val result = stats(listOf(session(today, startHour = 10, durationMinutes = 15)))
    result.monthlyData.size shouldBe 12
    result.monthlyData.last().valueMs shouldBe 15 * 60_000L
    result.monthlyData.dropLast(1).sumOf { it.valueMs } shouldBe 0L
  }

  @Test
  fun `monthly buckets do not merge the same month from different years`() {
    val sessions = listOf(
      session(LocalDate.of(2025, 8, 10), startHour = 10, durationMinutes = 60),
      session(LocalDate.of(2026, 8, 1), startHour = 10, durationMinutes = 30),
    )
    val result = stats(sessions)
    // Twelve trailing months ends at Aug 2026; Aug 2025 is outside it, so only this year's 30m shows.
    result.monthlyData.last().valueMs shouldBe 30 * 60_000L
    result.thisMonthMs shouldBe 30 * 60_000L
  }

  @Test
  fun `the biggest month is a lifetime record, not limited to the chart window`() {
    val sessions = listOf(
      session(LocalDate.of(2024, 3, 10), startHour = 10, durationMinutes = 300), // outside the 12-month chart
      session(today, startHour = 10, durationMinutes = 30),
    )
    val result = stats(sessions)
    result.biggestMonthMs shouldBe 300 * 60_000L
    result.biggestMonthLabel shouldBe "March 2024"
  }

  @Test
  fun `the busiest weekday reflects the split day totals`() {
    val sessions = listOf(
      session(LocalDate.of(2026, 7, 27), startHour = 10, durationMinutes = 90), // Monday
      session(LocalDate.of(2026, 7, 28), startHour = 10, durationMinutes = 30), // Tuesday
    )
    stats(sessions).bestDayOfWeek shouldBe "Monday"
  }

  @Test
  fun `a re-read is measured from its own first listen and cannot be re-dated before a listened finish`() {
    val id = BookId("content://book")
    val sessions = listOf(
      session(today.minusDays(400), startHour = 9, durationMinutes = 60, bookId = id),
      session(today.minusDays(390), startHour = 9, durationMinutes = 60, bookId = id, endReason = ListeningSessionEndReason.EndOfBook.id),
      session(today.minusDays(4), startHour = 9, durationMinutes = 60, bookId = id),
      session(today, startHour = 8, durationMinutes = 0, bookId = id, endReason = ListeningSessionEndReason.MarkedCompleted.id),
    )

    val book = stats(sessions).finishedBooks.single()
    book.readStartDate shouldBe today.minusDays(4)
    book.dailyMs.size shouldBe 5
    book.firstListenedDate shouldBe today.minusDays(400)
    book.canChangeFinishDate shouldBe true
    book.finishDateFloor shouldBe today.minusDays(390)
  }
}
