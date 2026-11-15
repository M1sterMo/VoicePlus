package voice.features.characterList

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.BookCharacter
import voice.core.data.BookId
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import java.time.Instant

class CharacterListViewStateTest {

  private fun character(
    id: Long,
    book: String,
    seriesId: String? = "dune",
  ) = BookCharacter(
    id = id,
    bookId = BookId(book),
    name = "c$id",
    description = "",
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    seriesId = seriesId,
  )

  private val organisation = LibraryOrganisation(
    series = listOf(LibrarySeries("dune", "Dune")),
    placements = listOf(
      LibraryPlacement("messiah", position = 0, seriesId = "dune", seriesPosition = 7),
      LibraryPlacement("dune1", position = 0, seriesId = "dune", seriesPosition = 2),
      LibraryPlacement("children", position = 0, seriesId = "dune", seriesPosition = 12),
      LibraryPlacement("other", position = 1),
    ),
  )

  @Test
  fun `series characters show their origin book's rank and mark the open book`() {
    val state = characterListViewState(
      bookId = BookId("messiah"),
      characters = listOf(character(1, "dune1"), character(2, "messiah"), character(3, "children"), character(4, "gone")),
      organisation = organisation,
    )

    state.series shouldBe SharedSeries("Dune", 3)
    state.characters.map { it.originBook } shouldBe listOf(1, 2, 3, null)
    state.characters.map { it.fromOpenBook } shouldBe listOf(false, true, false, false)
    state.characters.map { it.position } shouldBe listOf(1, 2, 3, 4)
  }

  @Test
  fun `a standalone book shows no series line and no chips`() {
    val state = characterListViewState(BookId("other"), listOf(character(1, "other", seriesId = null)), organisation)

    state.series shouldBe null
    state.characters.single().originBook shouldBe null
  }
}
