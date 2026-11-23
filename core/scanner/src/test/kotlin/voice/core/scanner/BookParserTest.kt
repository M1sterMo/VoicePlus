package voice.core.scanner

import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookContentRepo
import voice.core.documentfile.FileBasedDocumentFactory
import voice.core.documentfile.FileBasedDocumentFile
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BookParserTest {

  @get:Rule
  val testFolder = TemporaryFolder()

  private val contentRepo = mockk<BookContentRepo>()
  private val mediaAnalyzer = mockk<MediaAnalyzer>()
  private val parser = BookParser(
    context = ApplicationProvider.getApplicationContext(),
    contentRepo = contentRepo,
    mediaAnalyzer = mediaAnalyzer,
    fileFactory = FileBasedDocumentFactory,
    ignoreFileTagsStore = mockk { every { data } returns MutableStateFlow(false) },
  )

  @Test
  fun descTxtBesideTheAudioWinsOverTags() {
    val bookFolder = testFolder.newFolder("Book")
    File(bookFolder, "DESC.txt").writeText("  From the text file.\n")
    val content = parseFolder(bookFolder, metadata(album = null, title = null, synopsis = "From the tags"))
    content.synopsis shouldBe "From the text file."
  }

  @Test
  fun tagSynopsisIsUsedWithoutADescTxt() {
    val bookFolder = testFolder.newFolder("Book")
    parseFolder(bookFolder, metadata(album = null, title = null, synopsis = "From the tags")).synopsis shouldBe "From the tags"
  }

  @Test
  fun aBookWithoutSynopsisIsMarkedAsLookedFor() {
    val bookFolder = testFolder.newFolder("Book")
    parseFolder(bookFolder, null).synopsis shouldBe ""
  }

  @Test
  fun existingBooksAreReadOnceMoreToPickUpTheirSynopsis() = runTest {
    val bookFolder = testFolder.newFolder("Book")
    val chapters = listOf(chapter(File(bookFolder, "1.mp3").apply { createNewFile() }))
    val id = BookId(bookFolder.toUri())
    var stored = parser.parse(chapters, id, null, FileBasedDocumentFile(bookFolder))
      .copy(synopsis = null, name = "Edited", nameOverridden = true, positionInChapter = 7L, rating = 4, author = "Kept", genre = "Kept")
    coEvery { contentRepo.get(id) } answers { stored }
    coEvery { contentRepo.update(id, any()) } answers { secondArg<(BookContent) -> BookContent>()(stored).also { stored = it } }
    var analyzed = 0
    coEvery { mediaAnalyzer.analyze(any()) } answers {
      analyzed++
      metadata(album = "Album", title = null, synopsis = "Now found")
    }

    parser.parseAndStore(chapters, FileBasedDocumentFile(bookFolder)).synopsis shouldBe "Now found"
    stored.synopsis shouldBe "Now found"
    stored.name shouldBe "Edited"
    stored.positionInChapter shouldBe 7L
    stored.rating shouldBe 4
    // Only the synopsis is filled in: tags read now must not replace anything the book already had.
    stored.author shouldBe "Kept"
    stored.genre shouldBe "Kept"

    // Once looked for, a book is not read again on later scans.
    parser.parseAndStore(chapters, FileBasedDocumentFile(bookFolder)).synopsis shouldBe "Now found"
    analyzed shouldBe 1
  }

  private fun parseFolder(
    bookFolder: File,
    analyzed: Metadata?,
  ): BookContent = parser.parse(
    chapters = listOf(chapter(File(bookFolder, "1.mp3").apply { createNewFile() })),
    id = BookId(bookFolder.toUri()),
    analyzed = analyzed,
    file = FileBasedDocumentFile(bookFolder),
  )

  @Test
  fun folderBookUsesFolderNameWhenAlbumMissing() {
    val bookFolder = testFolder.newFolder("My Audiobook")
    val chapters = listOf(
      chapter(File(bookFolder, "1.mp3").apply { createNewFile() }),
      chapter(File(bookFolder, "2.mp3").apply { createNewFile() }),
    )

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = metadata(album = null, title = "First Chapter Title"),
      file = FileBasedDocumentFile(bookFolder),
    )

    content.name shouldBe "My Audiobook"
  }

  @Test
  fun folderBookWithSingleChapterStillUsesFolderName() {
    val bookFolder = testFolder.newFolder("Harry Potter 3")
    val chapters = listOf(chapter(File(bookFolder, "track01.mp3").apply { createNewFile() }))

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = metadata(album = null, title = "Track Title"),
      file = FileBasedDocumentFile(bookFolder),
    )

    content.name shouldBe "Harry Potter 3"
  }

  @Test
  fun singleFileBookUsesTitleWhenAlbumMissing() {
    val bookFile = testFolder.newFile("book.mp3")
    val chapters = listOf(chapter(bookFile))

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFile.toUri()),
      analyzed = metadata(album = null, title = "The Title"),
      file = FileBasedDocumentFile(bookFile),
    )

    content.name shouldBe "The Title"
  }

  @Test
  fun albumAlwaysWinsOverTitleAndFolderName() {
    val bookFolder = testFolder.newFolder("Folder Name")
    val chapters = listOf(
      chapter(File(bookFolder, "1.mp3").apply { createNewFile() }),
      chapter(File(bookFolder, "2.mp3").apply { createNewFile() }),
    )

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = metadata(album = "Album Name", title = "First Chapter Title"),
      file = FileBasedDocumentFile(bookFolder),
    )

    content.name shouldBe "Album Name"
  }

  @Test
  fun missingMetadataFallsBackToFolderName() {
    val bookFolder = testFolder.newFolder("Fallback Folder")
    val chapters = listOf(
      chapter(File(bookFolder, "1.mp3").apply { createNewFile() }),
      chapter(File(bookFolder, "2.mp3").apply { createNewFile() }),
    )

    val content = parser.parse(
      chapters = chapters,
      id = BookId(bookFolder.toUri()),
      analyzed = null,
      file = FileBasedDocumentFile(bookFolder),
    )

    content.name shouldBe "Fallback Folder"
  }

  private fun chapter(file: File): Chapter = Chapter(
    id = ChapterId(file.toUri()),
    name = "Chapter",
    duration = 1000L,
    fileLastModified = Instant.EPOCH,
    markData = emptyList(),
  )

  private fun metadata(
    album: String?,
    title: String?,
    synopsis: String? = null,
  ): Metadata = Metadata(
    duration = 1000L,
    artist = null,
    album = album,
    title = title,
    fileName = "file",
    chapters = emptyList(),
    genre = null,
    narrator = null,
    series = null,
    part = null,
    synopsis = synopsis,
  )
}
