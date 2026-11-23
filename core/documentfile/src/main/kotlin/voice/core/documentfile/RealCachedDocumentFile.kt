package voice.core.documentfile

import android.content.Context
import android.net.Uri

internal data class RealCachedDocumentFile(
  val context: Context,
  override val uri: Uri,
  private val preFilledContent: FileContents?,
) : CachedDocumentFile {

  private val listing: List<CachedDocumentFile>? by lazy {
    if (isDirectory) {
      parseContents(uri, context)
    } else {
      emptyList()
    }
  }

  override val children: List<CachedDocumentFile> get() = listing.orEmpty()
  override val childrenListed: Boolean get() = listing != null

  private val content: FileContents? by lazy {
    preFilledContent ?: FileContents.query(context, uri)
  }

  override val name: String? by lazy { content?.name }
  override val isDirectory: Boolean by lazy { content?.isDirectory ?: false }
  override val isFile: Boolean by lazy { content?.isFile ?: false }
  override val length: Long by lazy { content?.length ?: 0L }
  override val lastModified: Long by lazy { content?.lastModified ?: 0L }

  override fun toString(): String = "RealCachedDocumentFile($uri)"
}
