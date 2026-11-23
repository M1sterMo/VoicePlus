package voice.core.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.LibraryPlacement
import voice.core.data.LibraryShelf
import voice.core.data.ListeningEvent
import voice.core.data.ListeningSession
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.MemoryDataStore
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookMoverImplTest {

  private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDb::class.java)
    .allowMainThreadQueries()
    .build()
  private val currentBook = MemoryDataStore<BookId?>(null)
  private val upNext = MemoryDataStore(emptyList<BookId>())
  private val mover = BookMoverImpl(db, db.bookMoveDao(), currentBook, upNext)

  private val old = BookId("old")
  private val new = BookId("new")
  private val oldChapters = listOf(ChapterId("old/1"), ChapterId("old/2"))
  private val newChapters = listOf(ChapterId("new/1"), ChapterId("new/2"))
  private val gone = ChapterId("old/gone")
  private val chapters = oldChapters.zip(newChapters).toMap()

  @After fun close() = db.close()

  private suspend fun book(
    id: BookId,
    chapters: List<ChapterId>,
  ) = db.bookContentDao().insert(
    BookContent(
      id, 1f, false, true, Instant.EPOCH, null, id.value, Instant.EPOCH,
      chapters, chapters.first(), 0, null, 0f, null, null, null, null,
    ),
  )

  private fun session(
    chapter: ChapterId,
    end: ChapterId? = null,
  ) = ListeningSession(
    bookId = old,
    chapterId = chapter,
    startedAt = Instant.ofEpochMilli(chapter.hashCode().toLong()),
    endedAt = Instant.EPOCH,
    durationMs = 1,
    startPositionMs = 0,
    endPositionMs = 1,
    endChapterId = end,
  )

  @Test
  fun `what belongs to the moved chapters and the book follows it to its new id`() = runTest {
    book(old, oldChapters + gone)
    book(new, newChapters)
    val bookmark = Bookmark(old, oldChapters[1], "here", 10, Instant.EPOCH, false, Bookmark.Id.random())
    val bookmarkInGoneChapter = bookmark.copy(chapterId = gone, id = Bookmark.Id.random())
    db.bookmarkDao().addBookmark(bookmark)
    db.bookmarkDao().addBookmark(bookmarkInGoneChapter)
    db.listeningSessionDao().insert(session(oldChapters[0], end = oldChapters[1]))
    // skipped back: started in the second chapter, ended in the first
    db.listeningSessionDao().insert(session(oldChapters[1], end = oldChapters[0]))
    db.listeningSessionDao().insert(session(gone))
    db.listeningEventDao().insert(ListeningEvent(bookId = old, type = 0, chapterId = oldChapters[0], positionMs = 5, at = Instant.EPOCH))
    db.chapterNameOverrideDao().insert(ChapterNameOverride(oldChapters[1].value, 0, old.value, "Prologue"))
    db.bookCharacterDao().insert(
      BookCharacter(bookId = old, name = "Paul", description = "", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
    )
    val shelf = LibraryShelf(id = "fiction", name = "Fiction", position = 0)
    db.libraryOrganisationDao().putShelves(listOf(shelf))
    db.libraryOrganisationDao().putPlacements(listOf(LibraryPlacement(bookId = old.value, shelfId = shelf.id, position = 3)))
    currentBook.updateData { old }
    upNext.updateData { listOf(BookId("other"), old) }

    mover.move(from = listOf(old), to = new, chapters = chapters, sameBook = old, playedHere = listOf(old))

    db.bookmarkDao().all().toSet() shouldBe setOf(bookmark.copy(bookId = new, chapterId = newChapters[1]), bookmarkInGoneChapter)
    db.listeningSessionDao().all().map { Triple(it.bookId, it.chapterId, it.endChapterId) }.toSet() shouldBe setOf(
      Triple(new, newChapters[0], newChapters[1]),
      Triple(new, newChapters[1], newChapters[0]),
      // a session in a chapter that's gone still counts for the book
      Triple(new, gone, null),
    )
    db.listeningEventDao().all().single().let { (it.bookId to it.chapterId) shouldBe (new to newChapters[0]) }
    db.chapterNameOverrideDao().all().single() shouldBe ChapterNameOverride(newChapters[1].value, 0, new.value, "Prologue")
    db.bookCharacterDao().all().single().let { (it.bookId to it.ownerBookId) shouldBe (new to new) }
    db.libraryOrganisationDao().placements().map { it.bookId to it.shelfId }.toSet() shouldBe setOf(new.value to shelf.id)
    currentBook.data.first() shouldBe new
    upNext.data.first() shouldBe listOf(BookId("other"), new)
  }

  @Test
  fun `a split up book only takes what belongs to its own chapters`() = runTest {
    book(old, oldChapters)
    book(new, newChapters.take(1))
    db.listeningSessionDao().insert(session(oldChapters[1]))
    currentBook.updateData { old }

    mover.move(
      from = listOf(old),
      to = new,
      chapters = mapOf(oldChapters[0] to newChapters[0]),
      sameBook = null,
      playedHere = emptyList(),
    )

    db.listeningSessionDao().all().single().bookId shouldBe old
    db.libraryOrganisationDao().placements().count { it.bookId == old.value } shouldBe 1
    currentBook.data.first() shouldBe old
  }
}
