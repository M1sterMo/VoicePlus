package voice.core.scanner

import io.kotest.matchers.shouldBe
import org.junit.Test

class CoverRankTest {

  @Test
  fun `an image named cover is preferred over other images`() {
    listOf("back.jpg", "Folder.jpg", "booklet.png", "Cover.png", "front.webp")
      .sortedBy(::coverRank) shouldBe listOf("Cover.png", "front.webp", "Folder.jpg", "back.jpg", "booklet.png")
  }

  @Test
  fun `images without a cover name keep their listed order`() {
    listOf("b.jpg", null, "a.jpg").sortedBy(::coverRank) shouldBe listOf("b.jpg", null, "a.jpg")
  }
}
