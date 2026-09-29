package voice.core.scanner

import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookContentRepoImpl
import voice.core.data.repo.BookRepositoryImpl
import voice.core.data.repo.ChapterRepoImpl
import voice.core.data.repo.internals.AppDb
import voice.core.data.toUri
import voice.core.documentfile.FileBasedDocumentFactory
import voice.core.documentfile.FileBasedDocumentFile
import java.io.Closeable
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class MediaScannerTest {

  @Test
  fun singleFileDeletion() = test {
    val audiobookFolder = folder("audiobooks")

    val book1 = File(audiobookFolder, "book1")
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    scan(FolderType.Root, audiobookFolder)

    book1Chapters.first().delete()

    scan(FolderType.Root, audiobookFolder)

    assertBookContents(
      BookContentView(
        id = book1,
        chapters = book1Chapters.drop(1),
      ),
    )
  }

  @Test
  fun coldImportAnalyzesEachChapterOnce() = test {
    val audiobookFolder = folder("audiobooks")
    val book = File(audiobookFolder, "book")
    val chapter1 = audioFile(book, "1.mp3")
    val chapter2 = audioFile(book, "2.mp3")

    scan(FolderType.Root, audiobookFolder)

    coVerify(exactly = 1) {
      mediaAnalyzer.analyze(match { it.uri == chapter1.toUri() }).let { }
    }
    coVerify(exactly = 1) {
      mediaAnalyzer.analyze(match { it.uri == chapter2.toUri() }).let { }
    }
  }

  @Test
  fun metadataPreservedOnDeletion() = test {
    val audiobookFolder = folder("audiobooks")

    val book1 = File(audiobookFolder, "book1")
    val book1Id = BookId(book1.toUri())
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    scan(FolderType.Root, audiobookFolder)

    val contentWithPositionAtLastChapter =
      bookContentRepo.get(BookId(book1.toUri()))!!.copy(currentChapter = ChapterId(book1Chapters.last().toUri()))
    bookContentRepo.put(contentWithPositionAtLastChapter)

    book1Chapters.forEach { it.toUri().toFile().delete() }

    scan(FolderType.Root, audiobookFolder)

    audioFile(book1, "1.mp3")
    audioFile(book1, "2.mp3")
    audioFile(book1, "10.mp3")

    bookContentRepo.get(book1Id) shouldBe contentWithPositionAtLastChapter
  }

  @Test
  fun forceReParseReDerivesNamesPreservingPlayback() = test {
    val audiobookFolder = folder("audiobooks")

    val book1 = File(audiobookFolder, "book1")
    val book1Id = BookId(book1.toUri())
    audioFile(book1, "1.mp3")
    audioFile(book1, "2.mp3")

    scan(FolderType.Root, audiobookFolder)

    bookContentRepo.get(book1Id)!!.name shouldBe "Book Name"

    // Simulate user playback progress that must survive a re-parse.
    val played = bookContentRepo.get(book1Id)!!.copy(
      playbackSpeed = 1.5F,
      positionInChapter = 500L,
    )
    bookContentRepo.put(played)

    ignoreFileTags.value = true

    // A routine (non-forced) scan must NOT touch the existing book's name.
    scan(FolderType.Root, audiobookFolder)
    bookContentRepo.get(book1Id)!!.name shouldBe "Book Name"

    scan(FolderType.Root, audiobookFolder, forceReParse = true)
    val updated = bookContentRepo.get(book1Id)!!
    updated.name shouldBe "book1"
    updated.author shouldBe null
    updated.playbackSpeed shouldBe 1.5F
    updated.positionInChapter shouldBe 500L
  }

  @Test
  fun forceReParsePreservesAUserEditedBookTitle() = test {
    val audiobookFolder = folder("audiobooks")
    val book = File(audiobookFolder, "book1")
    val id = BookId(book.toUri())
    audioFile(book, "1.mp3")

    scan(FolderType.Root, audiobookFolder)
    bookContentRepo.put(bookContentRepo.get(id)!!.copy(name = "My title", nameOverridden = true))
    ignoreFileTags.value = true

    scan(FolderType.Root, audiobookFolder, forceReParse = true)

    bookContentRepo.get(id)!!.name shouldBe "My title"
  }

  @Test
  fun editedTitleAndCoverSurviveRemovalRestoreAndRescan() = test {
    val audiobookFolder = folder("audiobooks")
    val book = File(audiobookFolder, "book1")
    val id = BookId(book.toUri())
    audioFile(book, "1.mp3")
    scan(FolderType.Root, audiobookFolder)
    val played = bookContentRepo.get(id)!!.copy(playbackSpeed = 1.75F, positionInChapter = 321L)
    bookContentRepo.put(played)

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val editor = BookEditDraftStore(bookRepo, context, mockk())
    editor.begin(id)
    editor.titleChanged("My edited title")
    val expectedCover = byteArrayOf(1, 2, 3, 4)
    val draftCover = File.createTempFile("edited-cover-", ".png", context.cacheDir).apply {
      writeBytes(expectedCover)
    }
    editor.stageCover(editor.draft.value!!.token, draftCover) shouldBe true
    editor.save() shouldBe true

    val edited = bookContentRepo.get(id)!!
    val savedCover = edited.cover!!
    try {
      edited.name shouldBe "My edited title"
      edited.nameOverridden shouldBe true
      savedCover.exists() shouldBe true
      savedCover.readBytes().toList() shouldBe expectedCover.toList()

      // Removing a book keeps its content row and excludes it from subsequent scans.
      excludedBooks.value = setOf(id.value)
      bookContentRepo.put(edited.copy(isActive = false))
      scan(FolderType.Root, audiobookFolder, forceReParse = true)
      bookContentRepo.get(id)!!.isActive shouldBe false

      // Restoring the same book reactivates that row before scanning it again.
      excludedBooks.value = emptySet()
      bookContentRepo.put(bookContentRepo.get(id)!!.copy(isActive = true))
      scan(FolderType.Root, audiobookFolder, forceReParse = true)

      val restored = bookContentRepo.get(id)!!
      restored.name shouldBe "My edited title"
      restored.nameOverridden shouldBe true
      restored.cover shouldBe savedCover
      restored.cover!!.exists() shouldBe true
      restored.cover!!.readBytes().toList() shouldBe expectedCover.toList()
      restored.playbackSpeed shouldBe played.playbackSpeed
      restored.currentChapter shouldBe played.currentChapter
      restored.positionInChapter shouldBe played.positionInChapter
    } finally {
      savedCover.delete()
    }
  }

  @Test
  fun editedTitleAndCoverSurviveFilesDisappearingAndReturning() = test {
    val audiobookFolder = folder("audiobooks")
    val editedBook = File(audiobookFolder, "edited")
    val otherBook = File(audiobookFolder, "other")
    val id = BookId(editedBook.toUri())
    audioFile(editedBook, "1.mp3")
    audioFile(otherBook, "1.mp3")
    scan(FolderType.Root, audiobookFolder)

    val expectedCover = byteArrayOf(5, 6, 7, 8)
    val cover = File.createTempFile("saved-cover-", ".png").apply { writeBytes(expectedCover) }
    try {
      val edited = bookContentRepo.get(id)!!.copy(
        name = "My edited title",
        nameOverridden = true,
        cover = cover,
      )
      bookContentRepo.put(edited)

      editedBook.deleteRecursively() shouldBe true
      scan(FolderType.Root, audiobookFolder)
      bookContentRepo.get(id) shouldBe edited.copy(isActive = false)

      audioFile(editedBook, "1.mp3")
      scan(FolderType.Root, audiobookFolder, forceReParse = true)

      val restored = bookContentRepo.get(id)!!
      restored.isActive shouldBe true
      restored.name shouldBe "My edited title"
      restored.nameOverridden shouldBe true
      restored.cover shouldBe cover
      restored.cover!!.exists() shouldBe true
      restored.cover!!.readBytes().toList() shouldBe expectedCover.toList()
    } finally {
      cover.delete()
    }
  }

  @Test
  fun forceReParseDoesNotOverwriteAnEditSavedWhileMetadataIsBeingRead() = test {
    val audiobookFolder = folder("audiobooks")
    val book = File(audiobookFolder, "book1")
    val id = BookId(book.toUri())
    audioFile(book, "1.mp3")
    scan(FolderType.Root, audiobookFolder)

    val analysisStarted = CompletableDeferred<Unit>()
    val finishAnalysis = CompletableDeferred<Unit>()
    coEvery { mediaAnalyzer.analyze(any()) } coAnswers {
      analysisStarted.complete(Unit)
      finishAnalysis.await()
      Metadata(
        duration = 1000L,
        artist = "Rescanned author",
        album = "Rescanned title",
        fileName = "Chapter",
        chapters = emptyList(),
        title = "Title",
        genre = "Genre",
        narrator = "Narrator",
        series = "Series",
        part = "Part",
      )
    }

    coroutineScope {
      val scanning = launch { scan(FolderType.Root, audiobookFolder, forceReParse = true) }
      analysisStarted.await()

      val context = ApplicationProvider.getApplicationContext<android.content.Context>()
      val editor = BookEditDraftStore(bookRepo, context, mockk())
      editor.begin(id)
      editor.titleChanged("Saved during scan")
      val expectedCover = byteArrayOf(4, 3, 2, 1)
      val draftCover = File.createTempFile("concurrent-edit-", ".png", context.cacheDir).apply {
        writeBytes(expectedCover)
      }
      editor.stageCover(editor.draft.value!!.token, draftCover) shouldBe true
      editor.save() shouldBe true
      val savedCover = bookContentRepo.get(id)!!.cover!!

      try {
        finishAnalysis.complete(Unit)
        scanning.join()

        val restored = bookContentRepo.get(id)!!
        restored.name shouldBe "Saved during scan"
        restored.nameOverridden shouldBe true
        restored.cover shouldBe savedCover
        restored.cover!!.readBytes().toList() shouldBe expectedCover.toList()
        restored.author shouldBe "Rescanned author"
      } finally {
        finishAnalysis.complete(Unit)
        scanning.cancel()
        savedCover.delete()
      }
    }
  }

  @Test
  fun forceReParseKeepsChapterAndPositionWhenFileTemporarilyUnreadable() = test {
    val audiobookFolder = folder("audiobooks")
    val book1 = File(audiobookFolder, "book1")
    val book1Id = BookId(book1.toUri())
    val chapter1 = audioFile(book1, "1.mp3")
    val chapter2 = audioFile(book1, "2.mp3")

    scan(FolderType.Root, audiobookFolder)

    // User is partway through chapter 2.
    val content = bookContentRepo.get(book1Id)!!.copy(
      currentChapter = ChapterId(chapter2.toUri()),
      positionInChapter = 1234L,
    )
    bookContentRepo.put(content)

    // Chapter 2's file is momentarily unreadable during a forced re-parse — it must be kept from
    // the cache, not dropped (which would shrink the book and reset the playback position).
    failToAnalyze(chapter2)
    scan(FolderType.Root, audiobookFolder, forceReParse = true)

    val updated = bookContentRepo.get(book1Id)!!
    updated.chapters.shouldContainExactlyInAnyOrder(
      ChapterId(chapter1.toUri()),
      ChapterId(chapter2.toUri()),
    )
    updated.currentChapter shouldBe ChapterId(chapter2.toUri())
    updated.positionInChapter shouldBe 1234L
  }

  @Test
  fun partialScanKeepsBooksUnderAFolderThatReturnedNoEntries() = test {
    val root1 = folder("audiobooks1")
    val book1 = File(root1, "book1")
    val chapter1 = audioFile(book1, "1.mp3")

    val root2 = folder("audiobooks2")
    val book2 = File(root2, "book2")
    val chapter2 = audioFile(book2, "1.mp3")

    scan(FolderType.Root, root1, root2)
    assertBookContents(
      BookContentView(book1, chapters = listOf(chapter1)),
      BookContentView(book2, chapters = listOf(chapter2)),
    )

    // root2 comes back empty (e.g. a dropped SAF permission / unmounted volume). Its books must
    // NOT be deactivated just because this single scan couldn't see them.
    book2.deleteRecursively()
    scan(FolderType.Root, root1, root2)

    assertBookContents(
      BookContentView(book1, chapters = listOf(chapter1)),
      BookContentView(book2, chapters = listOf(chapter2)),
    )
  }

  @Test
  fun emptyScanKeepsExistingBooksActive() = test {
    val audiobookFolder = folder("audiobooks")
    val book1 = File(audiobookFolder, "book1")
    val chapter = audioFile(book1, "1.mp3")

    scan(FolderType.Root, audiobookFolder)
    assertBookContents(BookContentView(book1, chapters = listOf(chapter)))

    // A scan that finds nothing (e.g. a dropped folder permission) must NOT deactivate the library.
    val emptyFolder = folder("empty")
    scan(FolderType.Root, emptyFolder)

    assertBookContents(BookContentView(book1, chapters = listOf(chapter)))
  }

  @Test
  fun multipleRoots() = test {
    val audiobookFolder1 = folder("audiobooks1")

    val topFileBook = audioFile(parent = audiobookFolder1, "test.mp3")

    val book1 = File(audiobookFolder1, "book1")
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    val audiobookFolder2 = folder("audiobooks1")

    val book2 = File(audiobookFolder2, "book2")
    val book2Chapters = listOf(audioFile(book2, "1.mp3"))

    scan(FolderType.Root, audiobookFolder1, audiobookFolder2)

    assertBookContents(
      BookContentView(topFileBook, chapters = listOf(topFileBook)),
      BookContentView(book1, chapters = book1Chapters),
      BookContentView(book2, chapters = book2Chapters),
    )
  }

  @Test
  fun scanRoot() = test {
    val audiobookFolder = folder("audiobooks1")

    val topFileBook = audioFile(parent = audiobookFolder, "test.mp3")

    val book1 = File(audiobookFolder, "book1")
    val book1Chapters = listOf(
      audioFile(book1, "1.mp3"),
      audioFile(book1, "2.mp3"),
      audioFile(book1, "10.mp3"),
    )

    val book2 = File(audiobookFolder, "book2")
    val book2Chapters = listOf(
      audioFile(book2, "1.mp3"),
      audioFile(book2, "2.mp3"),
      audioFile(book2, "10.mp3"),
    )

    scan(FolderType.Root, audiobookFolder)

    assertBookContents(
      BookContentView(topFileBook, chapters = listOf(topFileBook)),
      BookContentView(book1, chapters = book1Chapters),
      BookContentView(book2, chapters = book2Chapters),
    )
  }

  @Test
  fun scanSingleFile() = test {
    val book = audioFile(parent = folder("audiobooks1"), "test.mp3")
    scan(FolderType.SingleFile, book)
    assertBookContents(
      BookContentView(book, chapters = listOf(book)),
    )
  }

  @Test
  fun scanSingleFolder() = test {
    val folder = folder("book")
    val book = audioFile(parent = folder, "test.mp3")
    scan(FolderType.SingleFolder, folder)
    assertBookContents(
      BookContentView(folder, chapters = listOf(book)),
    )
  }

  @Test
  fun scanAuthor() = test {
    val audioBooks = folder("audiobooks")

    val book1 = audioFile(parent = audioBooks, "test.mp3")

    val book2 = audioFile(parent = audioBooks, "author1/test.mp3")

    val book3 = File(audioBooks, "author1/book1")
    val book3Chapter1 = audioFile(parent = book3, "c1.mp3")
    val book3Chapter2 = audioFile(parent = book3, "c2.mp3")

    val book4 = File(audioBooks, "author1/book2")
    val book4Chapter1 = audioFile(book4, "a.mp3")

    scan(FolderType.Author, audioBooks)
    assertBookContents(
      BookContentView(book1, chapters = listOf(book1)),
      BookContentView(book2, chapters = listOf(book2)),
      BookContentView(book3, chapters = listOf(book3Chapter1, book3Chapter2)),
      BookContentView(book4, chapters = listOf(book4Chapter1)),
    )
  }

  private fun test(test: suspend TestEnvironment.() -> Unit) {
    runTest {
      TestEnvironment().use { test(it) }
    }
  }

  private class TestEnvironment : Closeable {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java)
      .allowMainThreadQueries()
      .build()
    val bookContentRepo = BookContentRepoImpl(db.bookContentDao())
    val chapterRepo = ChapterRepoImpl(db.chapterDao())
    val ignoreFileTags = MutableStateFlow(false)
    val excludedBooks = MutableStateFlow<Set<String>>(emptySet())
    val mediaAnalyzer = mockk<MediaAnalyzer>()

    // The scanner now gates activation on the LIVE folder set via AudiobookFolders.isManaged. These tests drive
    // scanner.scan(folders) with a literal folder map, so the fake simply mirrors that same set: every folder
    // passed to scan() is treated as currently-configured. (The remove-during-scan race — where this set diverges
    // from the snapshot mid-scan — is covered by ScanFolderRemovalRaceTest.)
    val configuredFolders = FakeManagedFolders()
    private val scanner = MediaScanner(
      contentRepo = bookContentRepo,
      chapterParser = ChapterParser(
        chapterRepo = chapterRepo,
        mediaAnalyzer = mediaAnalyzer,
        ignoreFileTagsStore = mockk { every { data } returns ignoreFileTags },
      ),
      bookParser = BookParser(
        contentRepo = bookContentRepo,
        mediaAnalyzer = mediaAnalyzer,
        fileFactory = FileBasedDocumentFactory,
        ignoreFileTagsStore = mockk { every { data } returns ignoreFileTags },
      ),
      deviceHasPermissionBug = mockk(),
      audiobookFolders = configuredFolders,
      excludedBooksStore = mockk { every { data } returns excludedBooks },
    )

    val bookRepo = BookRepositoryImpl(chapterRepo, bookContentRepo)

    private val root: File = Files.createTempDirectory(this::class.java.canonicalName!!).toFile()

    suspend fun scan(
      type: FolderType = FolderType.Root,
      vararg roots: File,
      forceReParse: Boolean = false,
    ) {
      configuredFolders.set(type, roots.map { it.toUri() })
      scanner.scan(mapOf(type to roots.map(::FileBasedDocumentFile)), forceReParse)
    }

    @IgnorableReturnValue
    fun audioFile(
      parent: File,
      name: String,
    ): File {
      check(name.endsWith(".mp3"))
      return File(parent, name)
        .also {
          it.parentFile?.mkdirs()
          check(it.createNewFile())
        }
        .also {
          coEvery { mediaAnalyzer.analyze(any()) } coAnswers {
            Metadata(
              duration = 1000L,
              artist = "Author",
              album = "Book Name",
              fileName = "Chapter",
              chapters = emptyList(),
              title = "Title",
              genre = "Genre",
              narrator = "Narrator",
              series = "Series",
              part = "Part",
            )
          }
        }
    }

    fun folder(name: String): File {
      return File(root, name)
        .also { it.mkdirs() }
    }

    /** Make the analyzer return null for [file] only, simulating a transient read failure. */
    fun failToAnalyze(file: File) {
      coEvery { mediaAnalyzer.analyze(match { it.uri == file.toUri() }) } returns null
    }

    suspend fun assertBookContents(vararg expected: BookContentView) {
      bookRepo.all()
        .map {
          BookContentView(
            id = it.id.toUri().toFile(),
            chapters = it.content.chapters.map { chapter ->
              chapter.toUri().toFile()
            },
          )
        }
        .shouldContainExactlyInAnyOrder(expected.toList())
    }

    override fun close() {
      root.delete()
    }
  }

  data class BookContentView(
    val id: File,
    val chapters: List<File>,
  )
}
