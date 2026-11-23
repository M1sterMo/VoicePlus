package voice.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

  @Test
  fun bookCoversAreExcludedFromCloudAndIncludedInDeviceTransfer() {
    ApplicationProvider.getApplicationContext<Context>()
      .resources
      .getXml(R.xml.full_backup_content)
      .use { parser ->
        assertEquals(
          1,
          parser.includeCount(
            domain = "file",
            path = "bookCovers/",
            requiredFlags = "deviceToDeviceTransfer",
          ),
        )
      }

    ApplicationProvider.getApplicationContext<Context>()
      .resources
      .getXml(R.xml.data_extraction_rules)
      .use { parser ->
        val includes = parser.dataExtractionIncludes(domain = "file", path = "bookCovers/")
        assertEquals(0, includes.cloudBackup)
        assertEquals(1, includes.deviceTransfer)
      }
  }

  @Test
  fun sensitiveDatastoreFilesAndScratchFilesAreExcluded() {
    val expected = (baseDatastorePaths + baseDatastorePaths.map { "$it.tmp" }).toSet()

    ApplicationProvider.getApplicationContext<Context>()
      .resources
      .getXml(R.xml.full_backup_content)
      .use { parser ->
        assertEquals(expected, parser.fullBackupExcludes())
      }

    ApplicationProvider.getApplicationContext<Context>()
      .resources
      .getXml(R.xml.data_extraction_rules)
      .use { parser ->
        val excludes = parser.dataExtractionExcludes()
        assertEquals(expected, excludes.cloudBackup)
        assertEquals(expected, excludes.deviceTransfer)
      }
  }

  private fun XmlPullParser.fullBackupExcludes(): Set<String> {
    val excludes = mutableSetOf<String>()
    var eventType = next()
    while (eventType != XmlPullParser.END_DOCUMENT) {
      if (
        eventType == XmlPullParser.START_TAG &&
        name == "exclude"
      ) {
        excludes += getAttributeValue(null, "path")
      }
      eventType = next()
    }
    return excludes
  }

  private fun XmlPullParser.includeCount(
    domain: String,
    path: String,
    requiredFlags: String,
  ): Int {
    var count = 0
    var eventType = next()
    while (eventType != XmlPullParser.END_DOCUMENT) {
      if (
        eventType == XmlPullParser.START_TAG &&
        name == "include" &&
        getAttributeValue(null, "domain") == domain &&
        getAttributeValue(null, "path") == path &&
        getAttributeValue(null, "requireFlags") == requiredFlags
      ) {
        count++
      }
      eventType = next()
    }
    return count
  }

  private fun XmlPullParser.dataExtractionIncludes(
    domain: String,
    path: String,
  ): DataExtractionIncludeCounts {
    var cloudBackup = 0
    var deviceTransfer = 0
    var currentSection: String? = null
    var eventType = next()
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> when (name) {
          "cloud-backup", "device-transfer" -> currentSection = name
          "include" -> if (
            getAttributeValue(null, "domain") == domain &&
            getAttributeValue(null, "path") == path
          ) {
            when (currentSection) {
              "cloud-backup" -> cloudBackup++
              "device-transfer" -> deviceTransfer++
            }
          }
        }
        XmlPullParser.END_TAG -> if (name == currentSection) {
          currentSection = null
        }
      }
      eventType = next()
    }
    return DataExtractionIncludeCounts(cloudBackup, deviceTransfer)
  }

  private fun XmlPullParser.dataExtractionExcludes(): DataExtractionExcludes {
    val cloudBackup = mutableSetOf<String>()
    val deviceTransfer = mutableSetOf<String>()
    var currentSection: MutableSet<String>? = null
    var eventType = next()
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> when (name) {
          "cloud-backup" -> currentSection = cloudBackup
          "device-transfer" -> currentSection = deviceTransfer
          "exclude" -> {
            currentSection?.add(getAttributeValue(null, "path"))
          }
        }
        XmlPullParser.END_TAG -> if (name == "cloud-backup" || name == "device-transfer") {
          currentSection = null
        }
      }
      eventType = next()
    }
    return DataExtractionExcludes(cloudBackup, deviceTransfer)
  }

  private data class DataExtractionExcludes(
    val cloudBackup: Set<String>,
    val deviceTransfer: Set<String>,
  )

  private data class DataExtractionIncludeCounts(
    val cloudBackup: Int,
    val deviceTransfer: Int,
  )

  private companion object {
    val baseDatastorePaths = listOf(
      "datastore/audiobookFolders",
      "datastore/SingleFolderAudiobookFolders",
      "datastore/SingleFileAudiobookFolders",
      "datastore/AuthorAudiobookFolders",
      "datastore/librarySnapshot0",
      "datastore/librarySnapshot1",
      "datastore/librarySnapshot2",
      "datastore/snapshotBackupState",
      "datastore/openListeningSession",
      "datastore/noEmbeddedCover",
    )
  }
}
