package voice.features.widget

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.MarkData
import java.time.Instant

/**
 * The widget redraws on book changes, but the position is written roughly once per second during
 * playback. This filter is the only thing between that and a full widget refresh — Room read, two
 * DataStore reads, and a cover decode — every second the user listens.
 */
class WidgetRefreshFilterTest {

  private val chapterOne = chapter(
    id = "c1",
    marks = listOf(MarkData(startMs = 0, name = "Mark one"), MarkData(startMs = 5_000, name = "Mark two")),
  )
  private val chapterTwo = chapter(id = "c2", marks = listOf(MarkData(startMs = 0, name = "Chapter two")))

  @Test
  fun `advancing the position within the same mark does not refresh the widget`() {
    val before = book(positionInChapter = 1_000)
    val after = book(positionInChapter = 2_000)

    widgetRelevantFieldsEqual(before, after) shouldBe true
  }

  @Test
  fun `advancing a whole percent of the book refreshes the widget`() {
    // Two 10-minute chapters, so 1% of the book is 12s. Both positions are in the second mark.
    val before = book(positionInChapter = 6_000)
    val after = book(positionInChapter = 13_000)

    widgetRelevantFieldsEqual(before, after) shouldBe false
  }

  @Test
  fun `crossing a chapter mark refreshes the widget`() {
    // Same chapter, but a different mark: the widget shows the mark's name.
    val before = book(positionInChapter = 1_000)
    val after = book(positionInChapter = 6_000)

    widgetRelevantFieldsEqual(before, after) shouldBe false
  }

  @Test
  fun `changing chapter refreshes the widget`() {
    val before = book(positionInChapter = 1_000)
    val after = book(currentChapter = chapterTwo.id, positionInChapter = 0)

    widgetRelevantFieldsEqual(before, after) shouldBe false
  }

  @Test
  fun `renumbering chapters refreshes the widget`() {
    val before = book(positionInChapter = 1_000)
    val after = book(positionInChapter = 1_000, chapterNameOffset = 1)

    widgetRelevantFieldsEqual(before, after) shouldBe false
  }

  private fun chapter(
    id: String,
    marks: List<MarkData>,
  ) = Chapter(
    id = ChapterId(id),
    name = "Chapter $id",
    duration = 600_000,
    fileLastModified = Instant.EPOCH,
    markData = marks,
  )

  private fun book(
    positionInChapter: Long,
    currentChapter: ChapterId = chapterOne.id,
    chapterNameOffset: Int = 0,
  ): Book {
    val chapters = listOf(chapterOne, chapterTwo)
    return Book(
      content = BookContent(
        id = BookId("content://book"),
        playbackSpeed = 1f,
        skipSilence = false,
        isActive = true,
        lastPlayedAt = Instant.EPOCH,
        author = null,
        name = "A book",
        addedAt = Instant.EPOCH,
        chapters = chapters.map { it.id },
        currentChapter = currentChapter,
        positionInChapter = positionInChapter,
        cover = null,
        gain = 0f,
        genre = null,
        narrator = null,
        series = null,
        part = null,
        chapterNameOffset = chapterNameOffset,
      ),
      chapters = chapters,
    )
  }
}
