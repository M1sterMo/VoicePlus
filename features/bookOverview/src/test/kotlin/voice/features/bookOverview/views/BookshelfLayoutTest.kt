package voice.features.bookOverview.views

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.Test

class BookshelfLayoutTest {
  @Test fun `phone honours the chosen density`() {
    booksColumnCount(392.dp, 2, 1f) shouldBe 2
    booksColumnCount(392.dp, 3, 1f) shouldBe 3
    booksColumnCount(320.dp, 3, 1f) shouldBe 3
  }

  @Test fun `large text gets wider cells instead of smaller text`() {
    booksColumnCount(392.dp, 3, 1.6f) shouldBe 2
    booksColumnCount(320.dp, 3, 2f) shouldBe 1
    booksColumnCount(720.dp, 3, 2f) shouldBe 3
  }

  @Test fun `narrow windows and invalid stored counts remain usable`() {
    booksColumnCount(80.dp, 3, 1f) shouldBe 1
    booksColumnCount(720.dp, 0, 1f) shouldBe 2
    booksColumnCount(720.dp, 9, 1f) shouldBe 3
  }
}
