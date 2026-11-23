package voice.core.scanner

import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.documentfile.FileBasedDocumentFactory
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class CoverScannerTest {

  @get:Rule val temporary = TemporaryFolder()

  private val noEmbeddedCover = MemoryDataStore(emptySet<String>())
  private var embeddedCover: Boolean? = false
  private var extractions = 0
  private var extractionGate: CompletableDeferred<Unit>? = null
  private val extractionStarted = CompletableDeferred<Unit>()
  private val adopted = mutableListOf<File>()

  private val coverDir by lazy { temporary.newFolder("bookCovers") }
  private val extractor = mockk<CoverExtractor> {
    coEvery { extractCover(any(), any()) } coAnswers {
      extractions++
      if (embeddedCover == true) secondArg<File>().writeText("embedded")
      extractionStarted.complete(Unit)
      extractionGate?.await()
      embeddedCover
    }
  }
  private val coverSaver = mockk<CoverSaver> {
    coEvery { newBookCoverFile() } answers { File(coverDir, "${System.nanoTime()}.png") }
    coEvery { setBookCoverIfMissing(any(), any()) } answers {
      adopted += firstArg<File>()
      true
    }
  }
  private val scanner by lazy {
    CoverScanner(
      context = ApplicationProvider.getApplicationContext(),
      coverSaver = coverSaver,
      coverExtractor = extractor,
      documentFileFactory = FileBasedDocumentFactory,
      ignoreFileTagsStore = MemoryDataStore(false),
      noEmbeddedCoverStore = noEmbeddedCover,
    )
  }

  private fun book(vararg files: String): Book {
    val folder = temporary.newFolder()
    val chapters = files.map { name ->
      val file = File(folder, name).apply { writeText("audio") }
      Chapter(
        id = ChapterId(file.toUri()),
        name = name,
        duration = 1000,
        fileLastModified = Instant.EPOCH,
        markData = emptyList(),
        fileSize = 100,
      )
    }
    return Book(
      content = BookContent(
        id = BookId(folder.toUri()), name = folder.name, cover = null,
        playbackSpeed = 1f, skipSilence = false, isActive = true,
        lastPlayedAt = Instant.EPOCH, addedAt = Instant.EPOCH,
        chapters = chapters.map { it.id }, currentChapter = chapters.first().id, positionInChapter = 0L,
        author = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
      ),
      chapters = chapters,
    )
  }

  @Test
  fun `an image named cover in the book folder wins over the others`() = runTest {
    val book = book("1.mp3")
    val folder = File(book.id.toUri().path!!)
    File(folder, "back.jpg").writeText("back")
    File(folder, "Cover.PNG").writeText("cover")
    File(folder, "notes.txt").writeText("notes")

    scanner.scan(listOf(book))

    adopted.single().readText() shouldBe "cover"
    extractions shouldBe 0
  }

  @Test
  fun `files without an embedded cover are read once, until they change`() = runTest {
    val book = book("1.mp3", "2.mp3")

    scanner.scan(listOf(book))
    extractions shouldBe 2
    scanner.scan(listOf(book))
    extractions shouldBe 2

    val changed = book.copy(chapters = book.chapters.map { it.copy(fileSize = 200) })
    scanner.scan(listOf(changed))
    extractions shouldBe 4
    adopted shouldBe emptyList()
  }

  @Test
  fun `a file that can't be read now is tried again`() = runTest {
    val book = book("1.mp3")
    embeddedCover = null

    scanner.scan(listOf(book))
    scanner.scan(listOf(book))

    extractions shouldBe 2
  }

  @Test
  fun `an embedded cover is adopted`() = runTest {
    val book = book("1.mp3")
    embeddedCover = true

    scanner.scan(listOf(book))

    adopted.single().readText() shouldBe "embedded"
  }

  @Test
  fun `a book that left the library is forgotten`() = runTest {
    val book = book("1.mp3")
    scanner.scan(listOf(book))
    noEmbeddedCover.data.value.size shouldBe 1

    scanner.scan(emptyList())

    noEmbeddedCover.data.value shouldBe emptySet()
  }

  @Test
  fun `a cancelled cover search leaves no cover file behind`() = runTest {
    val book = book("1.mp3")
    embeddedCover = true
    extractionGate = CompletableDeferred()

    val job = launch { scanner.scan(listOf(book)) }
    // the folder is listed on the real io dispatcher, so wait in real time until the extraction is underway
    withContext(Dispatchers.Default) { withTimeout(10.seconds) { extractionStarted.await() } }
    coverDir.listFiles()!!.size shouldBe 1
    job.cancel()
    advanceUntilIdle()

    coverDir.listFiles()!!.toList() shouldBe emptyList()
    adopted shouldBe emptyList()
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {
  override val data = MutableStateFlow(initial)

  override suspend fun updateData(transform: suspend (t: T) -> T): T {
    val value = transform(data.value)
    return data.updateAndGet { value }
  }
}
