package voice.core.data.repo.internals

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterNameOverride
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import voice.core.data.LibraryShelf
import voice.core.data.ListeningEvent
import voice.core.data.ListeningSession
import voice.core.data.RecentBookSearch
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookContentDao
import voice.core.data.repo.internals.dao.BookSearchFts
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
import voice.core.data.repo.internals.dao.LibraryOrganisationDao
import voice.core.data.repo.internals.dao.ListeningEventDao
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.repo.internals.dao.RecentBookSearchDao
import voice.core.data.repo.internals.migrations.Migration56
import voice.core.data.repo.internals.migrations.Migration68
import voice.core.data.repo.internals.migrations.Migration69

@Database(
  entities = [
    Chapter::class,
    BookContent::class,
    Bookmark::class,
    BookSearchFts::class,
    RecentBookSearch::class,
    ListeningSession::class,
    ListeningEvent::class,
    BookCharacter::class,
    ChapterNameOverride::class,
    LibraryShelf::class,
    LibrarySeries::class,
    LibraryPlacement::class,
  ],
  version = AppDb.VERSION,
  autoMigrations = [
    AutoMigration(from = 51, to = 52),
    AutoMigration(from = 52, to = 53),
    AutoMigration(from = 54, to = 55),
    AutoMigration(from = 55, to = 56),
    AutoMigration(from = 56, to = 57, spec = Migration56::class),
    AutoMigration(from = 57, to = 58),
    AutoMigration(from = 58, to = 59),
    AutoMigration(from = 59, to = 60),
    AutoMigration(from = 60, to = 61),
    AutoMigration(from = 61, to = 62),
    AutoMigration(from = 62, to = 63),
    AutoMigration(from = 63, to = 64),
    AutoMigration(from = 64, to = 65),
    AutoMigration(from = 65, to = 66),
    AutoMigration(from = 66, to = 67),
    AutoMigration(from = 67, to = 68),
    AutoMigration(from = 68, to = 69, spec = Migration68::class),
    AutoMigration(from = 69, to = 70, spec = Migration69::class),
  ],
)
@TypeConverters(Converters::class)
public abstract class AppDb : RoomDatabase() {

  public abstract fun chapterDao(): ChapterDao
  public abstract fun bookContentDao(): BookContentDao
  public abstract fun bookmarkDao(): BookmarkDao
  public abstract fun listeningSessionDao(): ListeningSessionDao
  public abstract fun listeningEventDao(): ListeningEventDao
  public abstract fun bookCharacterDao(): BookCharacterDao

  public abstract fun recentBookSearchDao(): RecentBookSearchDao
  public abstract fun chapterNameOverrideDao(): ChapterNameOverrideDao
  public abstract fun libraryOrganisationDao(): LibraryOrganisationDao

  internal companion object {
    const val VERSION = 70
    const val DATABASE_NAME = "autoBookDB"
  }
}
