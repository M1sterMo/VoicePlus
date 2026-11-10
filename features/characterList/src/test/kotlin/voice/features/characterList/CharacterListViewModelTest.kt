package voice.features.characterList

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import voice.core.data.BookCharacter
import voice.core.data.BookId
import voice.core.data.repo.BookCharacterRepo
import voice.core.data.repo.LibraryOrganisationRepo
import voice.navigation.Navigator
import java.time.Instant

/**
 * Covers the reorder arithmetic — the only logic in this feature that can silently corrupt the
 * user's hand-maintained ordering in the database rather than just misdraw it.
 */
class CharacterListViewModelTest {

  private val bookId = BookId("content://books/1")
  private val repo = FakeBookCharacterRepo()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = CharacterListViewModel(repo, mockk<LibraryOrganisationRepo>(), mockk<Navigator>(relaxed = true), bookId)

  private fun seed(vararg names: String) {
    repo.characters.value = names.mapIndexed { index, name ->
      BookCharacter(
        id = (index + 1).toLong(),
        bookId = bookId,
        name = name,
        description = "",
        sortOrder = index,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
      )
    }
  }

  private fun order(): List<String> = repo.characters.value.sortedBy { it.sortOrder }.map { it.name }

  @Test
  fun `moving the first character to the last position`() = runTest {
    seed("A", "B", "C")
    viewModel().updateCharacter(id = 1, name = "A", description = "", position = 3)
    order() shouldBe listOf("B", "C", "A")
    repo.characters.value.map { it.sortOrder }.sorted() shouldBe listOf(0, 1, 2)
  }

  @Test
  fun `moving the last character to the first position`() = runTest {
    seed("A", "B", "C")
    viewModel().updateCharacter(id = 3, name = "C", description = "", position = 1)
    order() shouldBe listOf("C", "A", "B")
  }

  @Test
  fun `a position beyond the list is clamped to the ends`() = runTest {
    seed("A", "B", "C")
    val vm = viewModel()
    vm.updateCharacter(id = 1, name = "A", description = "", position = 99)
    order() shouldBe listOf("B", "C", "A")
    vm.updateCharacter(id = 1, name = "A", description = "", position = 0)
    order() shouldBe listOf("A", "B", "C")
  }

  @Test
  fun `renaming a single character keeps it in place`() = runTest {
    seed("Only")
    viewModel().updateCharacter(id = 1, name = "Renamed", description = "notes", position = 1)
    order() shouldBe listOf("Renamed")
    repo.characters.value.single().description shouldBe "notes"
  }

  @Test
  fun `an edit that does not move anything writes only the edited row, once`() = runTest {
    seed("A", "B", "C")
    viewModel().updateCharacter(id = 2, name = "B renamed", description = "", position = 2)

    order() shouldBe listOf("A", "B renamed", "C")
    // One atomic call, carrying only the row that actually changed: a reorder applied row-by-row
    // could be interrupted midway and leave duplicate sortOrders behind.
    repo.updateAllCalls shouldBe 1
    repo.lastUpdateAll.map { it.id } shouldBe listOf(2L)
  }

  @Test
  fun `a blank name is rejected and changes nothing`() = runTest {
    seed("A", "B")
    viewModel().updateCharacter(id = 1, name = "   ", description = "", position = 2)
    order() shouldBe listOf("A", "B")
    repo.updateAllCalls shouldBe 0

    viewModel().addCharacter(name = " ", description = "")
    repo.characters.value shouldHaveNames listOf("A", "B")
  }

  @Test
  fun `added characters append to the end of the roster`() = runTest {
    seed("A", "B")
    val vm = viewModel()
    vm.addCharacter(name = " New ", description = " notes ")
    order() shouldBe listOf("A", "B", "New")
    repo.characters.value.last().description shouldBe "notes"

    vm.addCharacter(name = "Newer", description = "")
    order() shouldBe listOf("A", "B", "New", "Newer")
  }

  private infix fun List<BookCharacter>.shouldHaveNames(expected: List<String>) {
    map { it.name } shouldBe expected
  }
}

private class FakeBookCharacterRepo : BookCharacterRepo {

  val characters = MutableStateFlow<List<BookCharacter>>(emptyList())
  var updateAllCalls = 0
    private set
  var lastUpdateAll: List<BookCharacter> = emptyList()
    private set

  override suspend fun upsert(character: BookCharacter) {
    characters.value = if (character.id == 0L) {
      val id = (characters.value.maxOfOrNull { it.id } ?: 0L) + 1
      characters.value + character.copy(id = id)
    } else {
      characters.value.map { if (it.id == character.id) character else it }
    }
  }

  override suspend fun updateAll(characters: List<BookCharacter>) {
    if (characters.isEmpty()) return
    updateAllCalls++
    lastUpdateAll = characters
    val byId = characters.associateBy { it.id }
    this.characters.value = this.characters.value.map { byId[it.id] ?: it }
  }

  override fun characters(bookId: BookId): Flow<List<BookCharacter>> =
    characters.map { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.createdAt })) }

  override fun characterCount(bookId: BookId): Flow<Int> = characters.map { it.size }

  override suspend fun nextSortOrder(bookId: BookId): Int = (characters.value.maxOfOrNull { it.sortOrder } ?: -1) + 1

  override suspend fun delete(id: Long) {
    characters.value = characters.value.filterNot { it.id == id }
  }
}
