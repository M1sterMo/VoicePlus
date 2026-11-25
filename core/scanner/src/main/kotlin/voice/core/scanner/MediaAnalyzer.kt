package voice.core.scanner

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.FileTypes
import androidx.media3.common.MediaItem
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.await
import voice.core.data.MarkData
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import voice.core.logging.api.Logger
import voice.core.scanner.matroska.MatroskaMetaDataExtractor
import voice.core.scanner.matroska.MatroskaParseException
import voice.core.scanner.mp4.Mp4ChapterExtractor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

@Inject
internal class MediaAnalyzer(
  private val context: Context,
  private val mp4ChapterExtractor: Mp4ChapterExtractor,
  private val matroskaExtractorFactory: MatroskaMetaDataExtractor.Factory,
) {

  // we use a custom MediaSourceFactory because the default one for the
  // retriever also extracts the covers
  private val mediaSourceFactory = DefaultMediaSourceFactory(
    context,
    DefaultExtractorsFactory(),
  )

  suspend fun analyze(file: CachedDocumentFile): Metadata? {
    val builder = Metadata.Builder(file.nameWithoutExtension())
    val (trackGroups, duration) = retrieveMetadataAndDuration(file.uri)
      ?: return null
    if (duration <= Duration.ZERO) {
      Logger.w("Duration is zero or negative for file: ${file.uri}")
      return null
    }

    repeat(trackGroups.length) { trackGroupsIndex ->
      val trackGroup = trackGroups[trackGroupsIndex]
      if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
        repeat(trackGroup.length) { formatIndex ->
          val format = trackGroup.getFormat(formatIndex)
          format.metadata?.let { metadata ->
            repeat(metadata.length()) { metadataIndex ->
              when (val entry = metadata.get(metadataIndex)) {
                is TextInformationFrame -> visitText(entry, builder)
                is ChapterFrame -> visitChapter(entry, builder)
                is VorbisComment -> visitVorbis(entry, builder)
                is MdtaMetadataEntry -> visitMdta(entry, builder)
                // ID3 COMM, and the MP4 ©cmt atom, which Media3 also reports as a comment frame.
                // iTunes stores encoder data (iTunNORM, iTunSMPB) in comments too; those are not prose.
                is CommentFrame -> if (!entry.description.startsWith("iTun")) {
                  builder.comment = builder.comment.orIfBlank(repairLegacyTagText(entry.text))
                }
                else -> Logger.d("Unknown metadata entry: $entry")
              }
            }
          }
        }
      }
    }

    val fileType = FileTypes.inferFileTypeFromUri(file.uri)
    val extension = (file.name ?: "").substringAfterLast(delimiter = ".", missingDelimiterValue = "").lowercase()
    if (fileType == FileTypes.MP4 || extension == "mp4" || extension == "m4a" || extension == "m4b") {
      parseMp4Chapters(file, builder)
    }
    if (fileType == FileTypes.MATROSKA || extension == "mka" || extension == "mkv") {
      parseMatroskaMetaData(file, builder)
    }

    return builder.build(duration)
  }

  private fun parseMatroskaMetaData(
    file: CachedDocumentFile,
    builder: Metadata.Builder,
  ) {
    try {
      matroskaExtractorFactory.create(file.uri).use { extractor ->
        val mediaInfo = extractor.readMediaInfo()
        builder.chapters.addAll(mediaInfo.chapters)
        builder.artist = builder.artist ?: mediaInfo.artist
        builder.album = builder.album ?: mediaInfo.album
        builder.title = builder.title ?: mediaInfo.title
      }
    } catch (e: MatroskaParseException) {
      Logger.e(e, "Error parsing Matroska metadata")
    }
  }

  private suspend fun parseMp4Chapters(
    file: CachedDocumentFile,
    builder: Metadata.Builder,
  ) {
    val chapters = mp4ChapterExtractor.extractChapters(file.uri)
    builder.chapters += chapters
  }

  private fun visitMdta(
    entry: MdtaMetadataEntry,
    builder: Metadata.Builder,
  ) {
    when (entry.key) {
      "com.apple.quicktime.title" -> {
        builder.title = entry.value.toString(Charsets.UTF_8)
      }
      "com.apple.quicktime.artist" -> {
        builder.artist = entry.value.toString(Charsets.UTF_8)
      }
      "com.apple.quicktime.album" -> {
        builder.album = entry.value.toString(Charsets.UTF_8)
      }
    }
  }

  private fun visitVorbis(
    entry: VorbisComment,
    builder: Metadata.Builder,
  ) {
    val key = entry.key
    val value = entry.value
    when {
      key == "ARTIST" -> builder.artist = value
      key == "ALBUM" -> builder.album = value
      key == "TITLE" -> builder.title = value
      key.uppercase() in DESCRIPTION_KEYS -> builder.description = builder.description.orIfBlank(value)
      key.equals("COMMENT", ignoreCase = true) -> builder.comment = builder.comment.orIfBlank(value)
      key.startsWith("CHAPTER") -> {
        val withoutPrefix = key.removePrefix("CHAPTER")
        val isName = withoutPrefix.endsWith("NAME")
        if (isName) {
          val index = withoutPrefix.removeSuffix("NAME").toIntOrNull()
          if (index != null) {
            builder.vorbisChapterNames[index] = value
          }
        } else {
          val index = withoutPrefix.toIntOrNull()
          if (index != null) {
            val duration = parseVorbisDuration(value)
            if (duration != null) {
              builder.vorbisChapterStarts[index] = duration.inWholeMilliseconds
            }
          }
        }
      }
      else -> Logger.d("Unknown comment name: ${entry.key}, value: $value")
    }
  }

  private fun visitChapter(
    entry: ChapterFrame,
    builder: Metadata.Builder,
  ) {
    repeat(entry.subFrameCount) { subFrameIndex ->
      val subFrame = entry.getSubFrame(subFrameIndex)
      if (subFrame is TextInformationFrame) {
        builder.chapters.add(
          MarkData(startMs = entry.startTimeMs.toLong(), name = repairLegacyTagText(subFrame.values.first())),
        )
      }
    }
  }

  private fun visitText(
    entry: TextInformationFrame,
    builder: Metadata.Builder,
  ) {
    val value = repairLegacyTagText(entry.values.first())
    when (entry.id) {
      "TIT2" -> builder.title = value
      "TPE1" -> builder.artist = value
      "TALB" -> builder.album = value
      "TCON" -> builder.genre = value
      "TCOM" -> builder.narrator = value
      "TXXX" -> when (entry.description) {
        "MVNM" -> builder.series = value
        "MVIN" -> builder.part = if (builder.part.isNullOrBlank()) value else builder.part
        "TXXX:PART" -> builder.part = value
        "TXXX:NARRATOR" -> builder.narrator = if (builder.narrator.isNullOrBlank()) value else builder.narrator
        else -> if (entry.description?.uppercase() in DESCRIPTION_KEYS) {
          builder.description = builder.description.orIfBlank(value)
        } else {
          Logger.v("Unknown TXXX frame description:  ${entry.description}, value: $value")
        }
      }
      "TRCK", "TYER", "TSSE" -> {}
      else -> Logger.v("Unknown frame ID: ${entry.id}, value: $value")
    }
  }

  private suspend fun retrieveMetadataAndDuration(uri: Uri): Pair<TrackGroupArray, Duration>? {
    return try {
      MetadataRetriever.Builder(context, MediaItem.fromUri(uri))
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .use { retriever ->
          val trackGroups = retriever.retrieveTrackGroups().await()
          val duration = retriever.retrieveDurationUs().await().microseconds
          trackGroups to duration
        }
    } catch (e: Exception) {
      if (e is CancellationException) currentCoroutineContext().ensureActive()
      Logger.w(e, "Error retrieving metadata")
      null
    }
  }

  private suspend fun retrieveMetadata(uri: Uri): TrackGroupArray? {
    return try {
      MetadataRetriever.Builder(context, MediaItem.fromUri(uri))
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .use {
          it.retrieveTrackGroups().await()
        }
    } catch (e: Exception) {
      if (e is CancellationException) currentCoroutineContext().ensureActive()
      Logger.w(e, "Error retrieving metadata")
      null
    }
  }

  private suspend fun retrieveDuration(uri: Uri): Duration? {
    return try {
      MetadataRetriever.Builder(context, MediaItem.fromUri(uri))
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .use {
          it.retrieveDurationUs().await().microseconds
        }
    } catch (e: Exception) {
      if (e is CancellationException) currentCoroutineContext().ensureActive()
      Logger.w(e, "Error retrieving metadata")
      null
    }
  }
}

/** Tag names that hold a book's description, in Vorbis comments and ID3 TXXX frames. */
private val DESCRIPTION_KEYS = setOf("DESCRIPTION", "SYNOPSIS", "SUMMARY")

/** Keeps the first non-blank value a file declares. */
private fun String?.orIfBlank(value: String?): String? = if (isNullOrBlank()) value?.takeIf { it.isNotBlank() } else this
