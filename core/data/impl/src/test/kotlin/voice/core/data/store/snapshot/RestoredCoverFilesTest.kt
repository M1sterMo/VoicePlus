package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoredCoverFilesTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test
  fun `cleanup removes only replaced unreferenced app covers`() {
    val covers = temporary.newFolder("bookCovers")
    val old = File(covers, "old.png").apply { writeText("old") }
    val shared = File(covers, "shared.png").apply { writeText("shared") }
    val imported = File(covers, "imported.png").apply { writeText("new") }
    val outside = temporary.newFile("outside.png")

    deleteReplacedCovers(
      oldCovers = listOf(old, shared, outside),
      importedCovers = listOf(imported),
      referencedCovers = listOf(shared, imported),
    )

    old.exists() shouldBe false
    shared.exists() shouldBe true
    imported.exists() shouldBe true
    outside.exists() shouldBe true
  }
}
