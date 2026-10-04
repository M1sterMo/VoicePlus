package voice.core.data.store.snapshot

import android.net.Uri
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import java.time.Instant

class BackupFileNamesTest {

  @Test
  fun `file name round-trips through parse`() {
    val at = Instant.parse("2026-08-01T09:30:05Z")

    val auto = BackupFileNames.fileName(manual = false, at = at, unique = "1234abcd")
    auto shouldBe "voiceplus-backup-20260801-093005-000-1234abcd.zip"
    val parsedAuto = BackupFileNames.parse(auto).shouldNotBeNull()
    parsedAuto.savedAt shouldBe at
    parsedAuto.manual shouldBe false
    parsedAuto.legacy shouldBe false

    val manual = BackupFileNames.fileName(manual = true, at = at, unique = "5678abcd")
    manual shouldBe "voiceplus-manual-20260801-093005-000-5678abcd.zip"
    val parsedManual = BackupFileNames.parse(manual).shouldNotBeNull()
    parsedManual.savedAt shouldBe at
    parsedManual.manual shouldBe true
    BackupFileNames.parse("voiceplus-backup-20260801-093005.json").shouldNotBeNull().savedAt shouldBe at
  }

  @Test
  fun `same timestamp produces distinct parseable names`() {
    val at = Instant.parse("2026-08-01T09:30:05.123Z")

    val first = BackupFileNames.fileName(manual = true, at = at, unique = "1234abcd")
    val second = BackupFileNames.fileName(manual = true, at = at, unique = "5678abcd")

    first shouldBe "voiceplus-manual-20260801-093005-123-1234abcd.zip"
    second shouldBe "voiceplus-manual-20260801-093005-123-5678abcd.zip"
    BackupFileNames.parse(first).shouldNotBeNull().savedAt shouldBe at
    BackupFileNames.parse(second).shouldNotBeNull().savedAt shouldBe at
  }

  @Test
  fun `legacy fixed names parse as legacy`() {
    val primary = BackupFileNames.parse("voiceplus-backup.json").shouldNotBeNull()
    primary.legacy shouldBe true
    primary.savedAt.shouldBeNull()
    BackupFileNames.parse("voiceplus-backup.previous.json").shouldNotBeNull().legacy shouldBe true
  }

  @Test
  fun `foreign and mangled names are rejected`() {
    BackupFileNames.parse("holiday-photo.jpg").shouldBeNull()
    BackupFileNames.parse("voiceplus-backup-20260801-093005.json.tmp").shouldBeNull()
    BackupFileNames.parse("voiceplus-backup-20260801-093005 (1).json").shouldBeNull()
    BackupFileNames.parse("voiceplus-backup-20260801-093005-000-1234abcd (1).zip").shouldBeNull()
    BackupFileNames.parse("voiceplus-backup-2026-08-01.json").shouldBeNull()
  }

  private fun entry(
    name: String,
    at: Instant?,
    manual: Boolean = false,
    legacy: Boolean = false,
  ) = BackupEntry(uri = mockk<Uri>(), displayName = name, savedAt = at, manual = manual, legacy = legacy)

  @Test
  fun `newestFirst sorts timestamped saves before legacy, legacy primary before previous`() {
    val old = entry("voiceplus-backup-20260701-000000.json", Instant.parse("2026-07-01T00:00:00Z"))
    val new = entry("voiceplus-manual-20260801-000000.json", Instant.parse("2026-08-01T00:00:00Z"), manual = true)
    val legacyPrimary = entry("voiceplus-backup.json", null, legacy = true)
    val legacyPrevious = entry("voiceplus-backup.previous.json", null, legacy = true)

    val sorted = listOf(legacyPrevious, old, legacyPrimary, new).sortedWith(newestFirst)

    sorted.map { it.displayName } shouldBe listOf(
      "voiceplus-manual-20260801-000000.json",
      "voiceplus-backup-20260701-000000.json",
      "voiceplus-backup.json",
      "voiceplus-backup.previous.json",
    )
  }

  @Test
  fun `prune keeps the newest autos and never touches manual or legacy saves`() {
    val autos = (1..9).map { day ->
      entry("voiceplus-backup-2026070$day-000000.json", Instant.parse("2026-07-0${day}T00:00:00Z"))
    }
    val manual = entry("voiceplus-manual-20260601-000000.json", Instant.parse("2026-06-01T00:00:00Z"), manual = true)
    val legacy = entry("voiceplus-backup.json", null, legacy = true)

    val pruned = pruneCandidates(autos + manual + legacy, keep = 7)

    // The two OLDEST autos go; the manual (older than all of them) and legacy stay.
    pruned.map { it.displayName } shouldBe listOf(
      "voiceplus-backup-20260702-000000.json",
      "voiceplus-backup-20260701-000000.json",
    )
  }

  @Test
  fun `prune is a no-op at or under the limit`() {
    val autos = (1..7).map { day ->
      entry("voiceplus-backup-2026070$day-000000.json", Instant.parse("2026-07-0${day}T00:00:00Z"))
    }
    pruneCandidates(autos, keep = 7) shouldBe emptyList()
  }
}
