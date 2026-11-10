package voice.features.folderPicker.selectType

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.BookId
import voice.core.documentfile.FileBasedDocumentFile
import java.io.File

@RunWith(AndroidJUnit4::class)
class FolderStructureTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `a folder of chapter files is one book`() {
    assertGuess(FolderMode.SingleBook, "01.mp3", "02.mp3", "03.mp3")
  }

  @Test
  fun `a folder split into discs is one book`() {
    assertGuess(FolderMode.SingleBook, "CD 1/01.mp3", "CD 2/01.mp3", "My Book - Disc2/01.mp3")
  }

  @Test
  fun `chapter files with a bonus folder are one book`() {
    assertGuess(FolderMode.SingleBook, "01.mp3", "02.mp3", "03.mp3", "Bonus/interview.mp3")
  }

  @Test
  fun `several audiobook files are a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune.m4b", "Hyperion.m4b")
  }

  @Test
  fun `audiobook files with a stray chapter file are a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune.m4b", "Hyperion.m4b", "intro.mp3")
  }

  @Test
  fun `book folders are a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune/01.mp3", "Hyperion/01.mp3", "Hyperion/CD 2/01.mp3", "Neuromancer.m4b")
  }

  @Test
  fun `folders of book folders are authors`() {
    assertGuess(
      FolderMode.Authors,
      "Frank Herbert/Dune/01.mp3",
      "Frank Herbert/Children of Dune/01.mp3",
      "Dan Simmons/Hyperion/01.mp3",
    )
  }

  @Test
  fun `folders of whole-book files are authors`() {
    assertGuess(
      FolderMode.Authors,
      "Frank Herbert/Dune.m4b",
      "Frank Herbert/Children of Dune.m4b",
      "Dan Simmons/Hyperion.m4b",
      "Dan Simmons/Endymion.m4b",
    )
  }

  @Test
  fun `book folders holding one audiobook file each are a library`() {
    assertGuess(FolderMode.Audiobooks, "Dune/Dune.m4b", "Hyperion/Hyperion.m4b")
  }

  @Test
  fun `a book split into chapter m4b files is one book`() {
    assertGuess(FolderMode.SingleBook, "Chapter 01.m4b", "Chapter 02.m4b", "Chapter 03.m4b")
  }

  @Test
  fun `a book split into numbered m4b files is one book`() {
    assertGuess(FolderMode.SingleBook, "01.m4b", "02.m4b", "Dune - Part 3.m4b")
  }

  @Test
  fun `numbered titles of a series are still books`() {
    assertGuess(FolderMode.Audiobooks, "Harry Potter 1.m4b", "Harry Potter 2.m4b")
  }

  @Test
  fun `excluded books are not counted`() = runTest {
    val folder = folder("Dune/01.mp3", "Hyperion/01.mp3")
    val excluded = folder.children.first { it.name == "Dune" }.uri

    folder.books(FolderMode.Audiobooks, excludedIds = setOf(BookId(excluded).value)).map { it.name } shouldBe
      listOf("Hyperion")
  }

  @Test
  fun `each mode counts the books the scanner would find`() = runTest {
    val folder = folder(
      "Frank Herbert/Dune/01.mp3",
      "Frank Herbert/Dune/02.mp3",
      "Frank Herbert/Children of Dune/01.mp3",
      "Dan Simmons/Hyperion/01.mp3",
      "Loose.m4b",
      "Ursula K. Le Guin/A Wizard of Earthsea.m4b",
    )

    folder.books(FolderMode.SingleBook) shouldBe listOf(book("folder", fileCount = 6))
    folder.books(FolderMode.Audiobooks).sortedBy { it.name } shouldBe listOf(
      book("Dan Simmons", fileCount = 1),
      book("Frank Herbert", fileCount = 3),
      book("Loose", fileCount = 1),
      book("Ursula K. Le Guin", fileCount = 1),
    )
    folder.books(FolderMode.Authors).sortedBy { it.name } shouldBe listOf(
      book("A Wizard of Earthsea", author = "Ursula K. Le Guin", fileCount = 1),
      book("Children of Dune", author = "Frank Herbert", fileCount = 1),
      book("Dune", author = "Frank Herbert", fileCount = 2),
      book("Hyperion", author = "Dan Simmons", fileCount = 1),
      book("Loose", fileCount = 1),
    )
  }

  @Test
  fun `folders without audio are no books`() = runTest {
    folder("Dune/01.mp3", "Covers/dune.jpg").books(FolderMode.Audiobooks).map { it.name } shouldBe listOf("Dune")
  }

  @Test
  fun `a folder without audio has no books in any mode`() = runTest {
    val folder = folder("cover.jpg", "Notes/readme.txt")

    FolderMode.entries.forEach { folder.books(it) shouldBe emptyList() }
  }

  private fun book(
    name: String,
    author: String? = null,
    fileCount: Int,
  ) = SelectFolderTypeViewState.Book(name = name, author = author, fileCount = fileCount)

  private fun assertGuess(
    expected: FolderMode,
    vararg files: String,
  ) = runTest {
    folder(*files).guessFolderMode() shouldBe expected
  }

  private fun folder(vararg files: String): FileBasedDocumentFile {
    val root = temporaryFolder.newFolder("folder")
    files.forEach { path ->
      File(root, path).apply {
        parentFile!!.mkdirs()
        createNewFile()
      }
    }
    return FileBasedDocumentFile(root)
  }
}
