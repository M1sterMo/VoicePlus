package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookCharacter
import voice.core.data.BookId
import voice.core.data.repo.internals.dao.BookCharacterDao

@ContributesBinding(AppScope::class)
public class BookCharacterRepoImpl
internal constructor(private val dao: BookCharacterDao) : BookCharacterRepo {

  override suspend fun upsert(character: BookCharacter) {
    if (character.id == 0L) {
      dao.add(character)
    } else {
      dao.update(character)
    }
  }

  override suspend fun updateAll(characters: List<BookCharacter>) {
    if (characters.isEmpty()) return
    dao.update(characters)
  }

  override fun characters(bookId: BookId): Flow<List<BookCharacter>> = dao.charactersForBook(bookId)

  override fun characterCount(bookId: BookId): Flow<Int> = dao.countForBook(bookId)

  override suspend fun nextSortOrder(bookId: BookId): Int = dao.nextSortOrder(bookId)

  override suspend fun delete(id: Long) {
    dao.delete(id)
  }
}
