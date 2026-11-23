package voice.core.data.folders

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.core.net.toUri
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import voice.core.data.repo.internals.MemoryDataStore

@RunWith(RobolectricTestRunner::class)
class AudiobookFoldersImplTest {

  private val folder = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks".toUri()
  private val stores = FolderType.entries.associateWith { MemoryDataStore(emptySet<Uri>()) }
  private val grants = mutableSetOf<Uri>()
  private val resolver = mockk<ContentResolver> {
    every { takePersistableUriPermission(any(), any()) } answers { grants += firstArg<Uri>() }
  }
  private val folders = AudiobookFoldersImpl(
    rootAudioBookFoldersStore = stores.getValue(FolderType.Root),
    singleFolderAudiobookFoldersStore = stores.getValue(FolderType.SingleFolder),
    singleFileAudiobookFoldersStore = stores.getValue(FolderType.SingleFile),
    authorAudiobookFoldersStore = stores.getValue(FolderType.Author),
    context = mockk<Context> { every { contentResolver } returns resolver },
    cachedDocumentFileFactory = mockk(relaxed = true),
    persistedUriPermissions = object : PersistedUriPermissions {
      override fun persistedUris(): Set<Uri> = grants.toSet()
    },
    bookContentRepo = mockk(),
  )

  private fun storeWrites() = shadowOf(Looper.getMainLooper()).idle()

  private suspend fun configured() = stores.mapValues { it.value.data.first() }.filterValues { it.isNotEmpty() }

  @Test
  fun `adding a folder again with another type moves it`() = runTest {
    folders.add(folder, FolderType.Root)
    storeWrites()
    folders.add(folder, FolderType.Author)
    storeWrites()

    configured() shouldBe mapOf(FolderType.Author to setOf(folder))
  }

  @Test
  fun `a folder without a grant is listed apart, and comes back once access is allowed again`() = runTest {
    stores.getValue(FolderType.Root).updateData { setOf(folder) }

    val withAccess = mutableListOf<List<Uri>>()
    val withoutAccess = mutableListOf<List<Uri>>()
    val collect = UnconfinedTestDispatcher(testScheduler)
    backgroundScope.launch(collect) { folders.all().collect { withAccess += it.getValue(FolderType.Root).map { file -> file.uri } } }
    backgroundScope.launch(collect) { folders.withoutAccess().collect { withoutAccess += it.getValue(FolderType.Root) } }
    withAccess.last() shouldBe emptyList()
    withoutAccess.last() shouldBe listOf(folder)

    // the store doesn't change when the same folder is picked again, only the grant does
    folders.add(folder, FolderType.Root)
    storeWrites()
    runCurrent()

    withAccess.last() shouldBe listOf(folder)
    withoutAccess.last() shouldBe emptyList()
  }
}
