package voice.core.scanner

import android.content.Context
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import java.io.File
import java.io.IOException
import java.time.Instant

class BookEditDraftTest {
  @get:Rule val temporary = TemporaryFolder()
  private val id = BookId("edited-book")
  private val chapter = ChapterId("file:///chapter.mp3")
  private val repo = mockk<BookRepository>()

  private fun setup(): Pair<BookEditDraftStore, () -> BookContent> {
    var content = BookContent(
      id = id, name = "Original title", cover = temporary.newFile("original.png"),
      playbackSpeed = 1f, skipSilence = false, isActive = true,
      lastPlayedAt = Instant.EPOCH, addedAt = Instant.EPOCH,
      chapters = listOf(chapter), currentChapter = chapter, positionInChapter = 42L,
      author = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
    )
    coEvery { repo.get(id) } answers { mockk<Book> { every { this@mockk.content } returns content } }
    coEvery { repo.updateBook(id, any()) } answers { content = secondArg<(BookContent) -> BookContent>()(content) }
    val context = mockk<Context> { every { filesDir } returns temporary.root }
    return BookEditDraftStore(repo, context, mockk()) to { content }
  }

  @Test fun `cancel leaves the original title and cover untouched`() = runTest {
    val (store, content) = setup()
    store.begin(id)
    store.titleChanged("Draft title")
    val cover = temporary.newFile("draft.png")
    store.stageCover(store.draft.value!!.token, cover)
    content().name shouldBe "Original title"
    store.discard()
    content().cover!!.exists() shouldBe true
    cover.exists() shouldBe false
    store.draft.value shouldBe null
    coVerify(exactly = 0) { repo.updateBook(any(), any()) }
  }

  @Test fun `opening the editor removes stale draft files from an earlier process`() = runTest {
    val stale = File(temporary.root, "draftBookCovers/stale.png").apply {
      parentFile!!.mkdirs()
      writeText("orphan")
    }
    val (store, _) = setup()
    store.begin(id)
    stale.exists() shouldBe false
  }

  @Test fun `save adopts the draft file and preserves playback data`() = runTest {
    val (store, content) = setup()
    val original = content()
    store.begin(id)
    store.titleChanged("  New title  ")
    val cover = temporary.newFile("draft.png")
    store.stageCover(store.draft.value!!.token, cover)
    store.save() shouldBe true
    content().name shouldBe "New title"
    content().nameOverridden shouldBe true
    content().cover!!.parentFile shouldBe File(temporary.root, "bookCovers")
    content().copy(name = original.name, cover = original.cover, nameOverridden = false) shouldBe original
    original.cover!!.exists() shouldBe false
    store.discard()
    cover.exists() shouldBe false
    content().cover!!.exists() shouldBe true
  }

  @Test fun `saving only a title keeps the existing cover unchanged`() = runTest {
    val (store, content) = setup()
    val originalCover = content().cover
    val originalBytes = byteArrayOf(9, 8, 7, 6)
    originalCover!!.writeBytes(originalBytes)

    store.begin(id)
    store.titleChanged("New title")
    store.save() shouldBe true

    content().name shouldBe "New title"
    content().cover shouldBe originalCover
    content().cover!!.readBytes().toList() shouldBe originalBytes.toList()
  }

  @Test fun `late cover results cannot change a new editor session`() = runTest {
    val (store, content) = setup()
    store.begin(id)
    val token = store.draft.value!!.token
    val first = temporary.newFile("first.png")
    store.stageCover(token, first)
    val second = temporary.newFile("second.png")
    store.stageCover(token, second)
    first.exists() shouldBe false
    store.begin(id)
    second.exists() shouldBe false
    val late = temporary.newFile("late.png")
    store.stageCover(token, late) shouldBe false
    late.exists() shouldBe false
    store.draft.value!!.cover shouldBe content().cover
  }

  @Test fun `blank title and failed database update retain the draft`() = runTest {
    val (store, content) = setup()
    store.begin(id)
    store.titleChanged(" ")
    store.save() shouldBe false
    coVerify(exactly = 0) { repo.updateBook(any(), any()) }
    store.titleChanged("New title")
    val cover = temporary.newFile("draft.png")
    store.stageCover(store.draft.value!!.token, cover)
    coEvery { repo.updateBook(id, any()) } throws IOException("Database unavailable")
    shouldThrow<IOException> { store.save() }
    content().cover!!.exists() shouldBe true
    cover.exists() shouldBe false
    store.draft.value!!.cover!!.exists() shouldBe true
    store.draft.value!!.title shouldBe "New title"
  }

  @Test fun `cancellation during commit cannot delete the adopted cover`() = runTest {
    val (store, _) = setup()
    store.begin(id)
    val cover = temporary.newFile("draft.png")
    store.stageCover(store.draft.value!!.token, cover)
    val started = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()
    coEvery { repo.updateBook(id, any()) } coAnswers {
      started.complete(Unit)
      finish.await()
    }
    val saving = launch { store.save() shouldBe true }
    started.await()
    saving.cancel()
    finish.complete(Unit)
    saving.join()
    store.draft.value shouldBe null
    store.discard()
    cover.exists() shouldBe false
    File(temporary.root, "bookCovers").listFiles().orEmpty().single().exists() shouldBe true
  }
}
