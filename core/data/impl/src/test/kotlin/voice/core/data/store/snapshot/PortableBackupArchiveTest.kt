package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PortableBackupArchiveTest {
  @get:Rule val temporary = TemporaryFolder()
  private val json = snapshotTestJson
  private fun snapshot(): LibrarySnapshot = (
    ExternalBackupBundleCodec.decode(
      json,
      backupFixture("db65-envelope-without-chapter-file-size.json"),
    ) as ExternalBackupBundleDecodeResult.Valid
    ).snapshot

  private fun archive(
    snapshot: LibrarySnapshot,
    covers: File,
  ): ByteArray = ByteArrayOutputStream().also {
    PortableBackupArchive.write(json, snapshot, covers, it)
  }.toByteArray()

  @Test fun `custom title and actual cover survive after the original files are gone`() {
    val source = temporary.newFolder("source")
    val image = File(source, "cover.png").apply { writeBytes(byteArrayOf(1, 3, 5, 7)) }
    val snapshot = snapshot().let { it.copy(books = listOf(it.books.single().copy(name = "My edited title", coverPath = image.path))) }
    val bytes = archive(snapshot, source)
    image.delete() shouldBe true
    val restored = PortableBackupArchive.read(
      json,
      bytes.inputStream(),
      temporary.newFolder("restored"),
    ) as ExternalBackupBundleDecodeResult.Valid
    restored.snapshot.books.single().name shouldBe "My edited title"
    restored.covers.getValue(snapshot.books.single().id).readBytes().toList() shouldBe listOf<Byte>(1, 3, 5, 7)
    restored.covers.getValue(snapshot.books.single().id).path shouldNotBe image.path
  }

  @Test fun `validation does not extract and books do not share deletable cover files`() {
    val source = temporary.newFolder("source")
    val image = File(source, "cover.png").apply { writeText("same image") }
    val snapshot = snapshot().let {
      it.copy(
        books = listOf(
          it.books.single().copy(id = "one", coverPath = image.path),
          it.books.single().copy(id = "two", coverPath = image.path),
        ),
      )
    }
    val bytes = archive(snapshot, source)
    (PortableBackupArchive.read(json, bytes.inputStream(), null) as ExternalBackupBundleDecodeResult.Valid).covers shouldBe emptyMap()
    val restored = PortableBackupArchive.read(
      json,
      bytes.inputStream(),
      temporary.newFolder("restored"),
    ) as ExternalBackupBundleDecodeResult.Valid
    restored.covers.getValue("one") shouldNotBe restored.covers.getValue("two")
    restored.covers.getValue("one").delete()
    restored.covers.getValue("two").readText() shouldBe "same image"
  }

  @Test fun `missing damaged extra and traversal cover entries fail without leaving files`() {
    val source = temporary.newFolder("source")
    val image = File(source, "cover.png").apply { writeText("artwork") }
    val snapshot = snapshot().let { it.copy(books = listOf(it.books.single().copy(coverPath = image.path))) }
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(archive(snapshot, source).inputStream()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entries[entry.name] = zip.readBytes()
      }
    }
    val cover = entries.keys.single { it.startsWith("covers/") }
    listOf(
      entries - cover,
      entries + (cover to "tampered".encodeToByteArray()),
      entries + ("../escape.png" to byteArrayOf(1)),
      entries + ("unexpected.png" to byteArrayOf(1)),
      entries + ("covers.json" to """{"version":1,"covers":{}}""".encodeToByteArray()),
    ).forEachIndexed { index, edited ->
      val bytes = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
          edited.forEach { (name, data) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
          }
        }
      }.toByteArray()
      val directory = temporary.newFolder("bad-$index")
      PortableBackupArchive.read(json, ByteArrayInputStream(bytes), directory) shouldBe ExternalBackupBundleDecodeResult.Corrupt
      directory.listFiles()!!.toList() shouldBe emptyList()
    }
  }

  @Test fun `book without a cover round trips without inventing artwork`() {
    val snapshot = snapshot().let { it.copy(books = listOf(it.books.single().copy(coverPath = null))) }
    val covers = temporary.newFolder()
    PortableBackupArchive.read(json, archive(snapshot, covers).inputStream(), covers) shouldBe
      ExternalBackupBundleDecodeResult.Valid(snapshot)
  }

  @Test fun `missing cover is omitted without losing the rest of the backup`() {
    val covers = temporary.newFolder()
    val original = snapshot().let {
      it.copy(books = listOf(it.books.single().copy(name = "Still backed up", coverPath = File(covers, "missing.png").path)))
    }
    val bytes = archive(original, covers)
    val restored = PortableBackupArchive.read(json, bytes.inputStream(), temporary.newFolder("restored-missing"))
      as ExternalBackupBundleDecodeResult.Valid
    restored.snapshot.books.single().name shouldBe "Still backed up"
    restored.snapshot.books.single().coverPath shouldBe null
    restored.covers shouldBe emptyMap()

    val recovered = File(covers, "missing.png").apply { writeText("recovered") }
    PortableBackupArchive.withAvailableCovers(original, covers).books.single().coverPath shouldBe recovered.path
  }
}
