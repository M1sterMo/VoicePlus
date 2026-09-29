package voice.core.data.store.snapshot

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Streams artwork separately from JSON: a large library must not hold all covers in memory. */
internal object PortableBackupArchive {
  private const val MAX_JSON = 64L * 1024 * 1024
  private const val MAX_COVER = 32L * 1024 * 1024
  private const val MAX_TOTAL = 1024L * 1024 * 1024
  private val coverName = Regex("covers/[0-9a-f]{64}\\.img")

  @Serializable
  private data class Index(
    val version: Int = 1,
    val covers: Map<String, String>,
  )

  fun withAvailableCovers(
    snapshot: LibrarySnapshot,
    coverDirectory: File,
  ): LibrarySnapshot = snapshot.copy(
    books = snapshot.books.map { book ->
      val file = book.coverPath?.let(::File)
      val usable = runCatching {
        file?.takeIf {
          it.canonicalFile.parentFile == coverDirectory.canonicalFile &&
            it.isFile &&
            it.length() in 1..MAX_COVER
        }
      }.getOrNull()
      if (usable == null && book.coverPath != null) book.copy(coverPath = null) else book
    },
  )

  @IgnorableReturnValue
  fun write(
    json: Json,
    snapshot: LibrarySnapshot,
    coverDirectory: File,
    output: OutputStream,
  ): LibrarySnapshot {
    val exported = withAvailableCovers(snapshot, coverDirectory)
    val files = exported.books.mapNotNull { book ->
      val file = book.coverPath?.let(::File) ?: return@mapNotNull null
      book.id to file
    }.toMap()
    val index = Index(covers = files.mapValues { (_, file) -> "covers/${file.inputStream().use(::digest)}.img" })
    require(files.size <= 10_000)
    require(files.values.sumOf { it.length() } <= MAX_TOTAL)
    ZipOutputStream(output).use { zip ->
      fun text(
        name: String,
        value: String,
      ) {
        val bytes = value.encodeToByteArray()
        require(bytes.size <= MAX_JSON)
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
      text("snapshot.json", ExternalBackupBundleCodec.encode(json, exported))
      text("covers.json", json.encodeToString(index))
      index.covers.entries.distinctBy { it.value }.forEach { (id, name) ->
        zip.putNextEntry(ZipEntry(name))
        files.getValue(id).inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
      }
    }
    return exported
  }

  /** Null [coverDirectory] validates without extracting (folder probes and export read-back). */
  fun read(
    json: Json,
    input: InputStream,
    coverDirectory: File?,
  ): ExternalBackupBundleDecodeResult {
    val created = mutableListOf<File>()
    val staged = mutableMapOf<String, File>()
    try {
      ZipInputStream(input).use { zip ->
        fun text(name: String): String {
          require(zip.nextEntry?.name == name)
          return ByteArrayOutputStream().also { copyLimited(zip, it, MAX_JSON) }.toString(Charsets.UTF_8.name())
        }
        val decoded = ExternalBackupBundleCodec.decode(json, text("snapshot.json"))
        if (decoded !is ExternalBackupBundleDecodeResult.Valid) return decoded
        val index = json.decodeFromString<Index>(text("covers.json"))
        if (index.version > 1) return ExternalBackupBundleDecodeResult.NewerFormat
        require(index.version == 1)
        val coveredBooks = decoded.snapshot.books.filter { it.coverPath != null }.mapTo(mutableSetOf()) { it.id }
        require(index.covers.keys == coveredBooks)
        require(index.covers.size <= 10_000)
        require(index.covers.values.all(coverName::matches))
        val expected = index.covers.values.toMutableSet()
        val copies = index.covers.values.groupingBy { it }.eachCount()
        var total = 0L
        if (coverDirectory != null) require(coverDirectory.isDirectory || coverDirectory.mkdirs())
        while (true) {
          val entry = zip.nextEntry ?: break
          require(expected.remove(entry.name)) { "Unexpected or duplicate cover entry" }
          val hash = MessageDigest.getInstance("SHA-256")
          val file = coverDirectory?.let { File.createTempFile("restore-staged-", ".img", it).also(created::add) }
          val output = file?.outputStream()
          try {
            val copyCount = copies.getValue(entry.name)
            total += copyLimited(zip, output, minOf(MAX_COVER, (MAX_TOTAL - total) / copyCount), hash) * copyCount
          } finally {
            output?.close()
          }
          require(hash.digest().toHexString() == entry.name.removePrefix("covers/").removeSuffix(".img"))
          if (file != null) staged[entry.name] = file
        }
        require(expected.isEmpty()) { "Backup is missing artwork" }
        // Each book owns its file: editing one book must not delete another book's shared cover.
        val covers = if (coverDirectory == null) {
          emptyMap()
        } else {
          index.covers.mapValues { (_, name) ->
            File.createTempFile("restored-", ".img", coverDirectory).also { destination ->
              created += destination
              staged.getValue(name).copyTo(destination, overwrite = true)
            }
          }
        }
        staged.values.forEach { it.delete() }
        return decoded.copy(covers = covers)
      }
    } catch (_: Exception) {
      created.forEach { it.delete() }
      return ExternalBackupBundleDecodeResult.Corrupt
    }
  }

  private fun digest(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    copyLimited(input, null, MAX_COVER, digest)
    return digest.digest().toHexString()
  }

  @IgnorableReturnValue
  private fun copyLimited(
    input: InputStream,
    output: OutputStream?,
    limit: Long,
    digest: MessageDigest? = null,
  ): Long {
    var count = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val size = input.read(buffer)
      if (size < 0) return count
      count += size
      require(count <= limit) { "Backup exceeds size limit" }
      digest?.update(buffer, 0, size)
      output?.write(buffer, 0, size)
    }
  }
}
