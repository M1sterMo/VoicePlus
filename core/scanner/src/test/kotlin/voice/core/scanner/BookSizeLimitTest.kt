package voice.core.scanner

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.ChapterId
import voice.core.data.fitIntoOneBook

class BookSizeLimitTest {

  // a SAF document uri of a typical length
  private val chapter = ChapterId("content://com.android.externalstorage.documents/tree/primary%3AAudiobooks/document/" + "x".repeat(100))

  @Test
  fun `a large book fits, one too big for a database row doesn't`() {
    List(5_000) { chapter }.fitIntoOneBook() shouldBe true
    List(20_000) { chapter }.fitIntoOneBook() shouldBe false
  }
}
