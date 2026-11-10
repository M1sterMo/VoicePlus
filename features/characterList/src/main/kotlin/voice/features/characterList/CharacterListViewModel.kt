package voice.features.characterList

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.BookCharacter
import voice.core.data.BookId
import voice.core.data.LibraryOrganisation
import voice.core.data.repo.BookCharacterRepo
import voice.core.data.repo.LibraryOrganisationRepo
import voice.navigation.Navigator
import java.time.Instant

@AssistedInject
class CharacterListViewModel(
  private val characterRepo: BookCharacterRepo,
  private val organisationRepo: LibraryOrganisationRepo,
  private val navigator: Navigator,
  @Assisted private val bookId: BookId,
) : RetainedViewModel() {

  @Composable
  fun viewState(): CharacterListViewState {
    val characters by remember { characterRepo.characters(bookId) }.collectAsState(initial = emptyList())
    val organisation by remember { organisationRepo.flow() }.collectAsState(initial = LibraryOrganisation())
    return characterListViewState(bookId, characters, organisation)
  }

  fun addCharacter(
    name: String,
    description: String,
  ) {
    if (name.isBlank()) return
    scope.launch {
      val now = Instant.now()
      characterRepo.upsert(
        BookCharacter(
          bookId = bookId,
          name = name.trim(),
          description = description.trim(),
          sortOrder = characterRepo.nextSortOrder(bookId),
          createdAt = now,
          updatedAt = now,
        ),
      )
    }
  }

  fun updateCharacter(
    id: Long,
    name: String,
    description: String,
    position: Int,
  ) {
    if (name.isBlank()) return
    scope.launch {
      val allChars = characterRepo.characters(bookId).firstOrNull() ?: return@launch
      val current = allChars.find { it.id == id } ?: return@launch

      // Reorder: move the character to the target position then reassign sortOrder indices
      val mutable = allChars.toMutableList()
      mutable.remove(current)
      val targetIndex = (position - 1).coerceIn(0, mutable.size)
      mutable.add(targetIndex, current)

      val now = Instant.now()
      // One atomic write of only the rows that actually changed: a partially-applied reorder would
      // leave duplicate sortOrders that never self-heal, and a row-at-a-time loop would make the
      // list visibly step through intermediate orders as Room re-emits after each write.
      val updates = mutable.mapIndexedNotNull { index, char ->
        val updated = if (char.id == id) {
          char.copy(name = name.trim(), description = description.trim(), sortOrder = index, updatedAt = now)
        } else {
          char.copy(sortOrder = index)
        }
        updated.takeIf { it != char }
      }
      characterRepo.updateAll(updates)
    }
  }

  fun deleteCharacter(id: Long) {
    scope.launch { characterRepo.delete(id) }
  }

  fun onClose() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): CharacterListViewModel
  }
}

internal fun characterListViewState(
  bookId: BookId,
  characters: List<BookCharacter>,
  organisation: LibraryOrganisation,
): CharacterListViewState {
  val seriesId = organisation.placements.find { it.bookId == bookId.value }?.seriesId
  // Raw series positions have gaps, so a book's number is its rank in the series' reading order.
  val books = organisation.placements.filter { seriesId != null && it.seriesId == seriesId }
    .sortedBy { it.seriesPosition }.map { it.bookId }
  return CharacterListViewState(
    characters = characters.mapIndexed { index, character ->
      CharacterItemViewState(
        id = character.id,
        name = character.name,
        description = character.description,
        position = index + 1,
        originBook = books.indexOf(character.bookId.value).takeIf { it >= 0 }?.plus(1),
        fromOpenBook = character.bookId == bookId,
      )
    },
    series = organisation.series.find { it.id == seriesId }?.let { SharedSeries(it.name, books.size) },
  )
}

data class CharacterListViewState(
  val characters: List<CharacterItemViewState>,
  val series: SharedSeries? = null,
)

/** The series whose books all share this list. */
data class SharedSeries(
  val name: String,
  val bookCount: Int,
)

data class CharacterItemViewState(
  val id: Long,
  val name: String,
  val description: String,
  val position: Int,
  /** The 1-based place in the series of the book the character was added in, if it is still in the series. */
  val originBook: Int? = null,
  val fromOpenBook: Boolean = false,
)
