package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.runForMaxSqlVariableNumber
import java.util.Collections

// One cache for the whole app: the scanner, the player and the library all read and write it, so a
// chapter the scanner updates is what the library shows. The scanner reads from several coroutines.
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class ChapterRepoImpl(private val dao: ChapterDao) : ChapterRepo {

  // Missing chapters aren't cached: a restore inserts chapters through the DAO, past this cache.
  private val cache: MutableMap<ChapterId, Chapter> = Collections.synchronizedMap(mutableMapOf())

  override suspend fun get(id: ChapterId): Chapter? {
    cache[id]?.let { return it }
    val chapter = dao.chapter(id) ?: return null
    // a put while this was read is newer
    return cache.putIfAbsent(id, chapter) ?: chapter
  }

  internal suspend fun warmup(ids: List<ChapterId>) {
    val missing = ids.filter { it !in cache }
    missing
      .runForMaxSqlVariableNumber {
        dao.chapters(it)
      }
      .forEach { cache.putIfAbsent(it.id, it) }
  }

  override suspend fun put(chapter: Chapter) {
    dao.insert(chapter)
    cache[chapter.id] = chapter
  }
}
