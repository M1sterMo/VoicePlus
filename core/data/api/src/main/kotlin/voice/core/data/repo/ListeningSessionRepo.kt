package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ListeningSession

public interface ListeningSessionRepo {
  public suspend fun addSession(session: ListeningSession)

  /** Replaces the stored session with the same id. */
  public suspend fun updateSession(session: ListeningSession)
  public suspend fun deleteSession(session: ListeningSession)
  public fun sessions(bookId: BookId): Flow<List<ListeningSession>>
  public suspend fun deleteAllForBook(bookId: BookId)
  public fun allSessions(): Flow<List<ListeningSession>>
}
