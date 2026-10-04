package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.repo.internals.dao.BookContentDao

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BookContentRepoImpl(private val dao: BookContentDao) : BookContentRepo {

  private val cacheMutex = Mutex()
  private var cacheFilled = false
  private val cache = MutableStateFlow<List<BookContent>?>(null)

  private suspend fun fillCache() {
    if (cacheFilled) return
    cacheMutex.withLock {
      if (cacheFilled) return@withLock
      cache.value = dao.all()
      cacheFilled = true
    }
  }

  override fun flow(): Flow<List<BookContent>> {
    return cache.onStart { fillCache() }.filterNotNull()
  }

  override suspend fun all(): List<BookContent> {
    fillCache()
    return cache.value!!
  }

  override fun flow(id: BookId): Flow<BookContent?> {
    return cache.onStart { fillCache() }
      .filterNotNull()
      .map { contents -> contents.find { it.id == id } }
      .distinctUntilChanged()
  }

  override suspend fun get(id: BookId): BookContent? {
    fillCache()
    return cache.value!!.find { it.id == id }
  }

  override suspend fun setAllInactiveExcept(ids: List<BookId>) {
    fillCache()
    cacheMutex.withLock {
      val contents = cache.value!!.map { it.copy(isActive = it.id in ids) }
      contents.forEach { dao.insert(it) }
      cache.value = contents
    }
  }

  override suspend fun put(content: BookContent) {
    fillCache()
    cacheMutex.withLock {
      val newContents = cache.value!!.toMutableList()
      val index = newContents.indexOfFirst { it.id == content.id }
      if (index == -1) newContents.add(content) else newContents[index] = content
      dao.insert(content)
      cache.value = newContents
    }
  }

  override suspend fun update(
    id: BookId,
    transform: (BookContent) -> BookContent,
  ): BookContent? {
    fillCache()
    return cacheMutex.withLock {
      val contents = cache.value!!.toMutableList()
      val index = contents.indexOfFirst { it.id == id }
      if (index == -1) return@withLock null
      val current = contents[index]
      val updated = transform(current)
      if (updated != current) {
        contents[index] = updated
        dao.insert(updated)
        cache.value = contents
      }
      updated
    }
  }

  override suspend fun invalidateCache() {
    cacheMutex.withLock {
      cache.value = dao.all()
      cacheFilled = true
    }
  }
}
