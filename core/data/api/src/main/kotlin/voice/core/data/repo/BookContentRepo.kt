package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.BookContent
import voice.core.data.BookId

public interface BookContentRepo {
  public fun flow(): Flow<List<BookContent>>

  public suspend fun all(): List<BookContent>
  public fun flow(id: BookId): Flow<BookContent?>

  public suspend fun get(id: BookId): BookContent?

  public suspend fun setAllInactiveExcept(ids: List<BookId>)

  public suspend fun put(content: BookContent)

  /** Atomically updates the latest row so scanners and user edits cannot overwrite each other. */
  @IgnorableReturnValue
  public suspend fun update(
    id: BookId,
    transform: (BookContent) -> BookContent,
  ): BookContent? {
    val current = get(id) ?: return null
    val updated = transform(current)
    if (updated != current) put(updated)
    return updated
  }

  /** Re-read the backing store into the in-memory cache (used after an out-of-band restore). */
  public suspend fun invalidateCache()
}

public suspend inline fun BookContentRepo.getOrPut(
  id: BookId,
  defaultValue: () -> BookContent,
): BookContent {
  return get(id) ?: defaultValue().also { put(it) }
}
