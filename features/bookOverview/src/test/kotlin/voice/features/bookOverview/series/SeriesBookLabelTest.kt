package voice.features.bookOverview.series

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.features.bookOverview.book
import voice.features.bookOverview.overview.toItemViewState

class SeriesBookLabelTest {
  @Test
  fun `explicit volume prefixes become readable labels without changing the original title`() {
    listOf("Chrysalis: Book 4 — Between a Rock and a Carapace", "Chrysalis:4 Between a Rock and a Carapace").forEach { title ->
      val item = book(name = title).toItemViewState().copy(importedSeries = "Chrysalis")
      seriesBookLabel(item, "My collection") shouldBe SeriesBookLabel("Between a Rock and a Carapace", "4")
      item.name shouldBe title
    }
  }

  @Test
  fun `ambiguous or unmatched titles remain intact with metadata used for volume`() {
    listOf(
      "Chrysalis",
      "Chrysalis: 4",
      "Chrysalis: 4 —  ",
      "Chrysalis: A New Beginning",
      "The Dungeon Anarchist's Cookbook",
      "Chrysalis Returns: 4 — Another Story",
    ).forEach { title ->
      val item = book(name = title).toItemViewState().copy(seriesPart = "3")
      seriesBookLabel(item, "Chrysalis") shouldBe SeriesBookLabel(title, "3")
    }
  }
}
