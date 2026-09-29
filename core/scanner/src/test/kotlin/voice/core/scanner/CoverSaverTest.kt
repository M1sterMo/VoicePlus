package voice.core.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class CoverSaverTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun `failed encoding removes the incomplete file without replacing the cover`() = runTest {
    val context = mockk<Context> { every { filesDir } returns temporaryFolder.root }
    val repo = mockk<BookRepository>(relaxed = true)
    val bitmap = mockk<Bitmap> {
      every { width } returns 10
      every { height } returns 10
      every { compress(any(), any(), any()) } returns false
    }

    CoverSaver(repo, context, mockk(relaxed = true)).save(BookId("encoding-failure"), bitmap)

    File(temporaryFolder.root, "bookCovers").listFiles()!!.toList() shouldBe emptyList()
    coVerify(exactly = 0) { repo.updateBook(any(), any()) }
  }

  @Test
  fun `failed file write preserves the previous cover and database`() = runTest {
    val oldCover = temporaryFolder.newFile("old-cover.png")
    // A file where the cover directory should be makes writes fail deterministically.
    temporaryFolder.newFile("bookCovers")
    val context = mockk<Context> { every { filesDir } returns temporaryFolder.root }
    val repo = mockk<BookRepository>(relaxed = true)
    coEvery { repo.get(any()) } returns mockk<Book> { every { content.cover } returns oldCover }
    val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    try {
      CoverSaver(repo, context, mockk(relaxed = true)).save(BookId("write-failure"), bitmap)
      oldCover.exists() shouldBe true
      coVerify(exactly = 0) { repo.updateBook(any(), any()) }
    } finally {
      bitmap.recycle()
    }
  }

  @Test
  fun `failed direct database update removes the newly encoded cover`() = runTest {
    val context = mockk<Context> { every { filesDir } returns temporaryFolder.root }
    val repo = mockk<BookRepository>()
    coEvery { repo.get(any()) } returns null
    coEvery { repo.updateBook(any(), any()) } throws IOException("Database write failed")
    val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    try {
      shouldThrow<IOException> {
        CoverSaver(repo, context, mockk(relaxed = true)).save(BookId("update-failure"), bitmap)
      }
      File(temporaryFolder.root, "bookCovers").listFiles().orEmpty().toList() shouldBe emptyList()
    } finally {
      bitmap.recycle()
    }
  }

  @Test
  fun `previous cover is deleted only after a successful database update`() = runTest {
    val oldCover = temporaryFolder.newFile("old-cover.png")
    val newCover = temporaryFolder.newFile("new-cover.png")
    val context = mockk<Context> { every { filesDir } returns temporaryFolder.root }
    val repo = mockk<BookRepository>()
    coEvery { repo.get(any()) } returns mockk<Book> { every { content.cover } returns oldCover }
    coEvery { repo.updateBook(any(), any()) } coAnswers {
      oldCover.exists() shouldBe true
      throw IOException("Database write failed")
    }
    val saver = CoverSaver(repo, context, mockk(relaxed = true))
    shouldThrow<IOException> {
      saver.setBookCover(newCover, BookId("update-failure"))
    }.message shouldBe "Database write failed"
    oldCover.exists() shouldBe true
    newCover.exists() shouldBe true

    coEvery { repo.updateBook(any(), any()) } coAnswers { oldCover.exists() shouldBe true }
    saver.setBookCover(newCover, BookId("update-success"))
    oldCover.exists() shouldBe false
    newCover.exists() shouldBe true

    coEvery { repo.get(any()) } returns mockk<Book> { every { content.cover } returns newCover }
    coEvery { repo.updateBook(any(), any()) } returns Unit
    saver.setBookCover(newCover, BookId("same-cover"))
    newCover.exists() shouldBe true
  }

  @Test
  fun `scanner cover cannot replace a valid cover saved after its stale read`() = runTest {
    val customCover = temporaryFolder.newFile("custom-cover.png")
    val scannedCover = temporaryFolder.newFile("scanned-cover.png")
    val current = mockk<BookContent> { every { cover } returns customCover }
    val repo = mockk<BookRepository>()
    coEvery { repo.updateBook(any(), any()) } coAnswers {
      secondArg<(BookContent) -> BookContent>()(current) shouldBe current
      Unit
    }
    val saver = CoverSaver(
      repo,
      mockk { every { filesDir } returns temporaryFolder.root },
      mockk(relaxed = true),
    )

    saver.setBookCoverIfMissing(scannedCover, BookId("concurrent-cover")) shouldBe false
    customCover.exists() shouldBe true
    scannedCover.exists() shouldBe true
  }

  @Test
  fun `saving large covers preserves portrait landscape and square aspect ratios`() = runTest {
    val context = mockk<Context> { every { filesDir } returns temporaryFolder.root }
    val repo = mockk<BookRepository>(relaxed = true)
    coEvery { repo.get(any()) } returns null
    val saver = CoverSaver(repo, context, mockk(relaxed = true))
    val directory = File(context.filesDir, "bookCovers")
    val cases = listOf(
      (1600 to 2400) to (1280 to 1920),
      (2400 to 1600) to (1920 to 1280),
      (2400 to 2400) to (1920 to 1920),
      (600 to 900) to (600 to 900),
      (1201 to 2400) to (961 to 1920),
    )
    cases.forEach { (source, expected) ->
      val (width, height) = source
      val before = directory.listFiles().orEmpty().toSet()
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      try {
        saver.save(BookId("cover-save-test"), bitmap)
        val saved = directory.listFiles().orEmpty().single { it !in before }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(saved.path, options)
        options.outWidth shouldBe expected.first
        options.outHeight shouldBe expected.second
        bitmap.isRecycled shouldBe false
      } finally {
        bitmap.recycle()
      }
    }
    coVerify(exactly = cases.size) { repo.updateBook(BookId("cover-save-test"), any()) }
  }
}
