package voice.features.bookOverview.editBookCategory

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import voice.core.data.ListeningSessionEndReason
import voice.core.data.isCompletion
import voice.core.data.isCompletionMark
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ListeningSessionRepo
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.category
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class EditBookCategoryViewModel(
  private val repo: BookRepository,
  private val sessionRepo: ListeningSessionRepo,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    val book = repo.get(bookId) ?: return emptyList()
    return when (book.category) {
      BookOverviewCategory.CURRENT -> listOf(
        BottomSheetItem.BookCategoryMarkAsNotStarted,
        BottomSheetItem.BookCategoryMarkAsCompleted,
      )
      BookOverviewCategory.NOT_STARTED -> listOf(
        BottomSheetItem.BookCategoryMarkAsCurrent,
        BottomSheetItem.BookCategoryMarkAsCompleted,
      )
      BookOverviewCategory.FINISHED -> listOf(
        BottomSheetItem.BookCategoryMarkAsCurrent,
        BottomSheetItem.BookCategoryMarkAsNotStarted,
      )
    }
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    val book = repo.get(bookId) ?: return

    val (currentChapter, positionInChapter) = when (item) {
      BottomSheetItem.BookCategoryMarkAsCurrent -> {
        book.chapters.first().id to 1L
      }
      BottomSheetItem.BookCategoryMarkAsNotStarted -> {
        book.chapters.first().id to 0L
      }
      BottomSheetItem.BookCategoryMarkAsCompleted -> {
        val lastChapter = book.chapters.last()
        lastChapter.id to lastChapter.duration
      }
      else -> return
    }

    val now = Instant.now()
    val completing = item == BottomSheetItem.BookCategoryMarkAsCompleted
    repo.updateBook(book.id) {
      it.copy(
        currentChapter = currentChapter,
        positionInChapter = positionInChapter,
        // A hand-marked completion is dated now; otherwise a never-played book would show its
        // epoch placeholder as the finish date.
        lastPlayedAt = if (completing) now else it.lastPlayedAt,
      )
    }
    if (completing) recordCompletion(book.id, currentChapter, positionInChapter, now) else forgetMarksFrom(book.id, now)
  }

  /**
   * Stats date a finish from completion records, so a hand-marked one needs its own. Marking the
   * same book completed again on the same day (say after an accidental tap on play) is the same
   * finish, not a second one.
   */
  private suspend fun recordCompletion(
    bookId: BookId,
    chapterId: ChapterId,
    positionMs: Long,
    now: Instant,
  ) {
    val lastFinish = sessionRepo.sessions(bookId).first().firstOrNull { it.isCompletion }
    if (lastFinish != null && sameDay(lastFinish.endedAt, now)) return
    sessionRepo.addSession(
      ListeningSession(
        bookId = bookId,
        chapterId = chapterId,
        startedAt = now,
        endedAt = now,
        durationMs = 0,
        startPositionMs = positionMs,
        endPositionMs = positionMs,
        endChapterId = chapterId,
        endReason = ListeningSessionEndReason.MarkedCompleted.id,
      ),
    )
  }

  /**
   * Un-marking moments after marking undoes a mistaken tap. Later it is a re-listen starting, and the
   * finish stays history.
   */
  private suspend fun forgetMarksFrom(
    bookId: BookId,
    now: Instant,
  ) {
    sessionRepo.sessions(bookId).first()
      .filter { it.isCompletionMark && Duration.between(it.endedAt, now) < UNDO_WINDOW }
      .forEach { sessionRepo.deleteSession(it) }
  }

  private fun sameDay(
    a: Instant,
    b: Instant,
  ): Boolean {
    val zone = ZoneId.systemDefault()
    return a.atZone(zone).toLocalDate() == b.atZone(zone).toLocalDate()
  }
}

private val UNDO_WINDOW: Duration = Duration.ofMinutes(15)
