package voice.features.bookOverview.overview

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.realTimeRemainingMs
import voice.core.ui.formatShortTime
import voice.features.bookOverview.book
import voice.features.bookOverview.chapter

class RealTimeRemainingTest {

  @Test
  fun `remaining time is wall-clock at the book's playback speed`() {
    val book = book(
      chapters = listOf(chapter(duration = 10_000), chapter(duration = 10_000)),
      time = 4_000,
    )
    book.realTimeRemainingMs() shouldBe 16_000L

    val atDoubleSpeed = book.copy(content = book.content.copy(playbackSpeed = 2f))
    atDoubleSpeed.realTimeRemainingMs() shouldBe 8_000L

    val finished = book.copy(content = book.content.copy(positionInChapter = 10_000, currentChapter = book.chapters.last().id))
    finished.realTimeRemainingMs() shouldBe 0L
  }

  @Test
  fun `short times read as hours and minutes`() {
    formatShortTime((14 * 60 + 20) * 60_000L + 59_000) shouldBe "14h 20m"
    formatShortTime(3 * 3_600_000L) shouldBe "3h 0m"
    formatShortTime(45 * 60_000L) shouldBe "45m"
    formatShortTime(20_000) shouldBe "1m"
    formatShortTime(0) shouldBe "0m"
  }
}
