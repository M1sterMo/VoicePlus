package voice.core.data.repo.internals.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import java.time.Instant

// MP4 chapter parsing now reads tracks whose ids are not 1..n and titles packed into one chunk.
// Clear fileLastModified so the next scan re-reads chapter marks without a file change.
@ContributesIntoSet(
  scope = AppScope::class,
  binding = binding<Migration>(),
)
public class Migration70 : IncrementalMigration(70) {

  override fun migrate(db: SupportSQLiteDatabase) {
    val lastModifiedCv = ContentValues().apply {
      put("fileLastModified", Instant.EPOCH.toString())
    }
    db.update(
      "chapters2",
      SQLiteDatabase.CONFLICT_FAIL,
      lastModifiedCv,
      null,
      null,
    )
  }
}
