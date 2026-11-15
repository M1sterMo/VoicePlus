package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.ListeningSession
import voice.core.data.repo.internals.dao.ListeningSessionDao

@ContributesBinding(AppScope::class)
public class ListeningSessionRepoImpl
internal constructor(private val dao: ListeningSessionDao) : ListeningSessionRepo {

  override suspend fun addSession(session: ListeningSession) {
    dao.insert(session)
  }

  override suspend fun updateSession(session: ListeningSession) {
    dao.upsert(session)
  }

  override suspend fun deleteSession(session: ListeningSession) {
    dao.deleteAt(session.bookId, session.startedAt, session.startPositionMs)
  }

  override fun sessions(bookId: BookId): Flow<List<ListeningSession>> {
    return dao.sessionsForBook(bookId)
  }

  override suspend fun deleteAllForBook(bookId: BookId) {
    dao.deleteAllForBook(bookId)
  }

  override fun allSessions(): Flow<List<ListeningSession>> = dao.allSessions()
}
