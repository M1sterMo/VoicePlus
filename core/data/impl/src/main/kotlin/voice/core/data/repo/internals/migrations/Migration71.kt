package voice.core.data.repo.internals.migrations

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Character lists become shared by a series: the books already grouped merge their lists as if
 * grouped now, in reading order, keeping one of each exact duplicate (same name and description).
 */
internal class Migration71 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    data class Row(
      val id: Long,
      val seriesId: String,
      val name: String,
      val description: String,
    )
    val rows = buildList {
      db.query(
        """SELECT c.id, p.seriesId, c.name, c.description FROM book_character c
        JOIN libraryPlacement p ON p.bookId = c.bookId WHERE p.seriesId IS NOT NULL
        ORDER BY p.seriesId, p.seriesPosition, c.sortOrder, c.createdAt, c.id""",
      ).use { cursor ->
        while (cursor.moveToNext()) add(Row(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)))
      }
    }
    val seen = mutableSetOf<Row>()
    rows.groupBy { it.seriesId }.values.forEach { series ->
      series.filter { seen.add(it.copy(id = 0)) }.forEachIndexed { sortOrder, row ->
        db.execSQL("UPDATE book_character SET seriesId = ?, sortOrder = ? WHERE id = ?", arrayOf<Any>(row.seriesId, sortOrder, row.id))
      }
    }
    db.execSQL(
      "DELETE FROM book_character WHERE seriesId IS NULL AND bookId IN (SELECT bookId FROM libraryPlacement WHERE seriesId IS NOT NULL)",
    )
    db.execSQL("UPDATE book_character SET ownerBookId = bookId WHERE seriesId IS NULL")
  }
}
