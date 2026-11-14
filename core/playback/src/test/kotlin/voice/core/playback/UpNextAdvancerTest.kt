package voice.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.playback.session.MediaId
import voice.core.playback.session.search.book
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerState
import java.time.Instant

class UpNextAdvancerTest {

  private val current = BookId("current")
  private val first = BookId("first")
  private val second = BookId("second")
  private val gone = BookId("gone")
  private val playerController = mockk<PlayerController>(relaxed = true)
  private val sleepTimer = FakeSleepTimer()
  private val player = mockk<Player>(relaxed = true) {
    every { playWhenReady } returns true
  }
  private val currentBook = MemoryDataStore<BookId?>(current)
  private lateinit var upNext: MemoryDataStore<List<BookId>>
  private val books = mutableMapOf<BookId, Book>()
  private val repo = mockk<BookRepository>().also { repo ->
    coEvery { repo.get(any()) } answers { if (firstArg<BookId>() == gone) null else books.getOrPut(firstArg()) { testBook(firstArg()) } }
    coEvery { repo.updateBook(any(), any()) } answers {
      val id = firstArg<BookId>()
      books[id] = books.getValue(id).update(secondArg<(BookContent) -> BookContent>())
    }
  }

  @Test
  fun `the first queued book plays when the book ends`() = runTest {
    advancer(listOf(first, second)).onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    verify(exactly = 1) { playerController.playPauseForBook(first) }
  }

  @Test
  fun `a finished queued book starts again from the beginning`() = runTest {
    books[first] = testBook(first, positionInChapter = 599_000L)

    advancer(listOf(first)).onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    books.getValue(first).position shouldBe 0L
    verify(exactly = 1) { playerController.playPauseForBook(first) }
  }

  @Test
  fun `a book in progress resumes where it was`() = runTest {
    books[first] = testBook(first, positionInChapter = 300_000L)

    advancer(listOf(first)).onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    books.getValue(first).position shouldBe 300_000L
  }

  @Test
  fun `books no longer in the library are skipped and leave the queue`() = runTest {
    val removed = BookId("removed")
    books[removed] = testBook(removed).update { it.copy(isActive = false) }

    advancer(listOf(gone, removed, second)).onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    verify(exactly = 1) { playerController.playPauseForBook(second) }
    upNext.data.first() shouldBe listOf(second)
  }

  @Test
  fun `the book that starts playing leaves the queue, not the one merely open`() = runTest {
    // The open book is `current`; a headset resumes `first`, which the player still has loaded.
    every { player.currentMediaItem } returns
      MediaItem.Builder().setMediaId(Json.encodeToString(MediaId.serializer(), MediaId.Book(first))).build()
    val advancer = advancer(listOf(first, current, second))

    advancer.onIsPlayingChanged(false)
    runCurrent()
    upNext.data.first() shouldBe listOf(first, current, second)

    advancer.onIsPlayingChanged(true)
    runCurrent()
    upNext.data.first() shouldBe listOf(current, second)
  }

  @Test
  fun `an empty queue plays nothing`() = runTest {
    advancer(emptyList()).onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    verify(exactly = 0) { playerController.playPauseForBook(any()) }
  }

  @Test
  fun `only the end of the book advances, not a pause`() = runTest {
    val advancer = advancer(listOf(first))
    advancer.onPlaybackStateChanged(Player.STATE_READY)

    every { player.playWhenReady } returns false
    advancer.onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    verify(exactly = 0) { playerController.playPauseForBook(any()) }
  }

  @Test
  fun `a chapter sleep timer that ends with the book does not advance`() = runTest {
    sleepTimer.state.value = SleepTimerState.Enabled.WithEndOfChapter(chaptersRemaining = 1)

    advancer(listOf(first)).onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    verify(exactly = 0) { playerController.playPauseForBook(any()) }
  }

  @Test
  fun `a chapter sleep timer with chapters left counts the end and advances`() = runTest {
    sleepTimer.state.value = SleepTimerState.Enabled.WithEndOfChapter(chaptersRemaining = 2)

    advancer(listOf(first)).onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    verify(exactly = 1) { playerController.playPauseForBook(first) }
    sleepTimer.state.value shouldBe SleepTimerState.Enabled.WithEndOfChapter(chaptersRemaining = 1)
  }

  @Test
  fun `a chapter timer counts the end of each book it runs through`() = runTest {
    sleepTimer.state.value = SleepTimerState.Enabled.WithEndOfChapter(chaptersRemaining = 2)
    val advancer = advancer(listOf(first, second))

    advancer.onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()
    currentBook.updateData { first }
    advancer.onPlaybackStateChanged(Player.STATE_ENDED)
    runCurrent()

    verify(exactly = 1) { playerController.playPauseForBook(first) }
    verify(exactly = 0) { playerController.playPauseForBook(second) }
    sleepTimer.state.value shouldBe SleepTimerState.Disabled
  }

  private fun TestScope.advancer(queue: List<BookId>): UpNextAdvancer {
    return UpNextAdvancer(
      upNextStore = MemoryDataStore(queue).also { upNext = it },
      currentBookStore = currentBook,
      bookRepository = repo,
      playerController = playerController,
      sleepTimer = sleepTimer,
      scope = this,
    ).also { it.attachTo(player) }
  }
}

private fun testBook(
  id: BookId,
  positionInChapter: Long = 0L,
) = book(
  id = id,
  positionInChapter = positionInChapter,
  chapters = listOf(Chapter(ChapterId("${id.value}-chapter"), "chapter", 600_000L, Instant.EPOCH, emptyList())),
)

private class FakeSleepTimer : SleepTimer {
  override val state = MutableStateFlow<SleepTimerState>(SleepTimerState.Disabled)
  override fun enable(mode: SleepTimerMode) = Unit
  override fun disable() {
    state.value = SleepTimerState.Disabled
  }

  override fun reset() = Unit

  // Like the real timer, a boundary counts once.
  private val counted = mutableSetOf<String>()
  override fun onChapterBoundaryReached(boundaryId: String) {
    val current = state.value as? SleepTimerState.Enabled.WithEndOfChapter ?: return
    if (!counted.add(boundaryId)) return
    state.value =
      if (current.chaptersRemaining > 1) current.copy(chaptersRemaining = current.chaptersRemaining - 1) else SleepTimerState.Disabled
  }
}
