package voice.features.folderPicker.selectType

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
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
  fun `each mode counts the books the scanner would find`() {
    val folder = folder(
      "Frank Herbert/Dune/01.mp3",
      "Frank Herbert/Dune/02.mp3",
      "Frank Herbert/Children of Dune/01.mp3",
      "Dan Simmons/Hyperion/01.mp3",
      "Loose.m4b",
    )

    folder.books(FolderMode.SingleBook) shouldBe listOf(book("folder", fileCount = 5))
    folder.books(FolderMode.Audiobooks).sortedBy { it.name } shouldBe listOf(
      book("Dan Simmons", fileCount = 1),
      book("Frank Herbert", fileCount = 3),
      book("Loose", fileCount = 1),
    )
    folder.books(FolderMode.Authors).sortedBy { it.name } shouldBe listOf(
      book("Children of Dune", author = "Frank Herbert", fileCount = 1),
      book("Dune", author = "Frank Herbert", fileCount = 2),
      book("Hyperion", author = "Dan Simmons", fileCount = 1),
      book("Loose", fileCount = 1),
    )
  }

  @Test
  fun `folders without audio are no books`() {
    folder("Dune/01.mp3", "Covers/dune.jpg").books(FolderMode.Audiobooks).map { it.name } shouldBe listOf("Dune")
  }

  @Test
  fun `a folder without audio has no books in any mode`() {
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
  ) {
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
