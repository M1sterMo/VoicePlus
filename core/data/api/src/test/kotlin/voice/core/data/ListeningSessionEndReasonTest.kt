package voice.core.data

import io.kotest.matchers.shouldBe
import org.junit.Test

class ListeningSessionEndReasonTest {

  @Test
  fun `each entry has the expected stable id`() {
    ListeningSessionEndReason.entries.size shouldBe 6
    ListeningSessionEndReason.Paused.id shouldBe 0
    ListeningSessionEndReason.Sleep.id shouldBe 1
    ListeningSessionEndReason.EndOfBook.id shouldBe 2
    ListeningSessionEndReason.BookSwitch.id shouldBe 3
    ListeningSessionEndReason.Interrupted.id shouldBe 4
    ListeningSessionEndReason.MarkedCompleted.id shouldBe 5
  }

  @Test
  fun `fromId round-trips every entry`() {
    ListeningSessionEndReason.entries.forEach { entry ->
      ListeningSessionEndReason.fromId(entry.id) shouldBe entry
    }
  }

  @Test
  fun `fromId returns null for unknown id`() {
    ListeningSessionEndReason.fromId(99) shouldBe null
  }

  @Test
  fun `fromId returns null for null input`() {
    ListeningSessionEndReason.fromId(null) shouldBe null
  }
}
