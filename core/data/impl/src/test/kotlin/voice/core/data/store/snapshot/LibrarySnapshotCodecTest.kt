package voice.core.data.store.snapshot

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.Test
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.MarkData
import java.time.Instant

class LibrarySnapshotCodecTest {

  @Test
  fun `legacy backup titles are protected as user overrides`() {
    val legacy = LibrarySnapshot(
      schemaVersion = 6,
      sequence = 1,
      savedAtEpochMillis = 0,
      totalCount = 1,
      activeCount = 1,
      books = listOf(book("b1", true).copy(name = "Saved title").toDto().copy(nameOverridden = false)),
      bookmarks = emptyList(),
      characters = emptyList(),
      chapterNameOverrides = emptyList(),
    )

    val compatible = legacy.withTitleOverrideCompatibility()

    compatible.books.single().name shouldBe "Saved title"
    compatible.books.single().nameOverridden shouldBe true
  }

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private fun book(
    id: String,
    active: Boolean,
  ): BookContent = BookContent(
    id = BookId(id), playbackSpeed = 1f, skipSilence = false, isActive = active,
    lastPlayedAt = Instant.ofEpochMilli(10), author = null, name = id,
    addedAt = Instant.ofEpochMilli(20), chapters = listOf(ChapterId("ch-$id")),
    currentChapter = ChapterId("ch-$id"), positionInChapter = 5, cover = null,
    gain = 0f, genre = null, narrator = null, series = null, part = null,
  )

  @Test
  fun `BookContent round-trips through dto`() {
    val original = book("b1", active = true).copy(seriesGroup = "My series", seriesOrder = 3, series = "Imported metadata")
    val restored = original.toDto().toBookContentOrNull()
    restored shouldBe original
  }

  @Test
  fun `LibrarySnapshot round-trips through json`() {
    val snap = LibrarySnapshot(
      schemaVersion = LibrarySnapshot.SCHEMA_VERSION, sequence = 3, savedAtEpochMillis = 99,
      totalCount = 1, activeCount = 1,
      books = listOf(book("b1", true).copy(seriesGroup = "Manual series", seriesOrder = 3).toDto()),
      bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
    )
    val text = json.encodeToString(LibrarySnapshot.serializer(), snap)
    json.decodeFromString(LibrarySnapshot.serializer(), text) shouldBe snap
  }

  @Test
  fun `Chapter round-trips through dto (relName is restore-only metadata)`() {
    val original = Chapter(
      id = ChapterId("ch-1"),
      name = "One",
      duration = 1_000,
      fileLastModified = Instant.ofEpochMilli(42),
      markData = listOf(MarkData(0, "intro"), MarkData(500, "two")),
      fileSize = 321L,
    )
    val dto = original.toDto(relName = "01.mp3")
    val text = json.encodeToString(ChapterDto.serializer(), dto)
    val decoded = json.decodeFromString(ChapterDto.serializer(), text)

    decoded.relName shouldBe "01.mp3"
    decoded.toChapter() shouldBe original
  }

  @Test
  fun `legacy ChapterDto json without fileSize defaults fileSize to zero`() {
    val text = """{"id":"ch-legacy","name":"Legacy","duration":1000,"fileLastModifiedEpochMillis":42,""" +
      """"markData":[{"startMs":0,"name":"intro"}],"relName":"legacy.mp3"}"""

    val dto = json.decodeFromString(ChapterDto.serializer(), text)

    dto.fileSize shouldBe 0L
    dto.toChapter().fileSize shouldBe 0L
  }

  @Test
  fun `v3 LibrarySnapshot round-trips endReason, events, hidden books and settings`() {
    val snap = LibrarySnapshot(
      schemaVersion = LibrarySnapshot.SCHEMA_VERSION, sequence = 7, savedAtEpochMillis = 1,
      totalCount = 1, activeCount = 1,
      books = listOf(book("b1", true).toDto()),
      bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
      sessions = listOf(
        ListeningSessionDto(
          id = 3, bookId = "b1", chapterId = "ch-b1", startedAtEpochMillis = 10, endedAtEpochMillis = 20,
          durationMs = 10, startPositionMs = 0, endPositionMs = 10, endChapterId = null, endReason = 1,
        ),
      ),
      chapters = listOf(
        Chapter(ChapterId("ch-b1"), "C", 1_000, Instant.ofEpochMilli(0), listOf(MarkData(0, "m")))
          .toDto(relName = "ch-b1.mp3"),
      ),
      events = listOf(
        ListeningEventDto(bookId = "b1", type = 0, chapterId = "ch-b1", positionMs = 5, atEpochMillis = 15),
      ),
      hiddenBooks = setOf("b-hidden"),
      settings = mapOf("darkTheme" to "true"),
    )
    val text = json.encodeToString(LibrarySnapshot.serializer(), snap)
    val decoded = json.decodeFromString(LibrarySnapshot.serializer(), text)
    decoded shouldBe snap
    decoded.sessions.single().endReason shouldBe 1
  }

  @Test
  fun `a v2 bundle with a stored identity stamp still decodes (unknown keys ignored)`() {
    // Written by the v2 schema: per-book identity stamps were stored; v3 derives them instead.
    val v2 = """{"schemaVersion":2,"dbVersion":65,"sequence":4,"savedAtEpochMillis":9,"totalCount":1,
      "activeCount":1,"books":[{"id":"b1","playbackSpeed":1.0,"skipSilence":false,"isActive":true,
      "lastPlayedAtEpochMillis":0,"author":null,"name":"b1","addedAtEpochMillis":0,"chapters":["ch-b1"],
      "currentChapter":"ch-b1","positionInChapter":0,"coverPath":null,"genre":null,"narrator":null,
      "series":null,"part":null,
      "identity":{"authority":"a","isSingleFile":false,"relPath":"p","folderName":"f",
      "children":[{"relName":"c.mp3","size":0}]}}],
      "bookmarks":[],"characters":[],"chapterNameOverrides":[]}"""
    val decoded = json.decodeFromString(LibrarySnapshot.serializer(), v2)
    decoded.books.single().id shouldBe "b1"
    decoded.books.single().seriesGroup shouldBe null
    decoded.books.single().seriesOrder shouldBe null
    decoded.sessions shouldBe emptyList()
    decoded.hiddenBooks shouldBe emptySet()
    decoded.settings shouldBe emptyMap()
  }

  @Test
  fun `a dto violating BookContent invariant maps to null, not a crash`() {
    val bad = book("b1", true).toDto().copy(currentChapter = "ch-does-not-exist")
    bad.toBookContentOrNull().shouldBeNull()
  }

  @Test
  fun `unknown json keys are tolerated`() {
    val text = """{"schemaVersion":1,"sequence":1,"savedAtEpochMillis":0,"totalCount":0,"activeCount":0,""" +
      """"books":[],"bookmarks":[],"characters":[],"chapterNameOverrides":[],"futureField":123}"""
    json.decodeFromString(LibrarySnapshot.serializer(), text).sequence shouldBe 1L
  }

  @Test
  fun `activeIds reflects only active books`() {
    val snap = LibrarySnapshot(
      schemaVersion = 1, sequence = 1, savedAtEpochMillis = 0, totalCount = 2, activeCount = 1,
      books = listOf(book("b1", true).toDto(), book("b2", false).toDto()),
      bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
    )
    snap.activeIds() shouldBe setOf("b1")
  }
}
