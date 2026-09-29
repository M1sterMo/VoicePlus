package voice.core.data.repo.internals

import androidx.room.RoomDatabase
import androidx.room.withTransaction

public suspend fun <T> RoomDatabase.transaction(action: suspend () -> T): T = withTransaction(action)
