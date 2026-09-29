package voice.core.data.repo.internals.migrations

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing titles may have been user-edited before the override flag existed. Preserve them. */
internal class Migration69 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.execSQL("UPDATE content2 SET nameOverridden = 1")
  }
}
