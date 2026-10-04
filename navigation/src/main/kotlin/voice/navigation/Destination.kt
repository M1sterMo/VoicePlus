package voice.navigation

import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import voice.core.common.serialization.UriSerializer
import voice.core.data.BookId

sealed interface Destination {

  @Serializable
  data class Playback(val bookId: BookId) : Compose {
    override val trackingName: String get() = "Playback"
  }

  @Serializable
  data class Bookmarks(val bookId: BookId) : Compose {
    override val trackingName: String get() = "Bookmarks"
  }

  @Serializable
  data class ListeningLog(val bookId: BookId) : Compose {
    override val trackingName: String get() = "ListeningLog"
  }

  @Serializable
  data object HiddenBooks : Compose {
    override val trackingName: String get() = "HiddenBooks"
  }

  @Serializable
  data object BackupRestore : Compose {
    override val trackingName: String get() = "BackupRestore"
  }

  @Serializable
  data object ListeningStatistics : Compose {
    override val trackingName: String get() = "ListeningStatistics"
  }

  @Serializable
  data class CharacterList(val bookId: BookId) : Compose {
    override val trackingName: String get() = "CharacterList"
  }

  @Serializable
  data class ChapterEditor(val bookId: BookId) : Compose {
    override val trackingName: String get() = "ChapterEditor"
  }

  @Serializable
  data class CoverFromInternet(
    val bookId: BookId,
    val editSession: String? = null,
  ) : Compose {
    override val trackingName: String get() = "CoverFromInternet"
  }

  data class Website(val url: String) : Destination

  @Serializable
  data class EditCover(
    val bookId: BookId,
    val cover:
    @Serializable(with = UriSerializer::class)
    Uri,
    val editSession: String? = null,
  ) : Compose {
    override val trackingName: String get() = "EditCover"
  }

  data class Activity(val intent: Intent) : Destination

  @Serializable
  sealed interface Compose :
    Destination,
    NavKey {
    val trackingName: String
  }

  @Serializable
  data object Settings : Compose {
    override val trackingName: String get() = "Settings"
  }

  @Serializable
  data object BookOverview : Compose {
    override val trackingName: String get() = "BookOverview"
  }

  @Serializable
  data object FolderPicker : Compose {
    override val trackingName: String get() = "FolderPicker"
  }

  @Serializable
  data class SelectFolderType(
    val uri:
    @Serializable(with = UriSerializer::class)
    Uri,
    val origin: Origin,
  ) : Compose {
    override val trackingName: String = "SelectFolderType"
  }

  @Serializable
  data object OnboardingWelcome : Compose {
    override val trackingName: String get() = "OnboardingWelcome"
  }

  @Serializable
  data object OnboardingCompletion : Compose {
    override val trackingName: String get() = "OnboardingCompletion"
  }

  @Serializable
  data object OnboardingExplanation : Compose {
    override val trackingName: String get() = "OnboardingExplanation"
  }

  @Serializable
  data object OpenSourceLicenses : Compose {
    override val trackingName: String get() = "OpenSourceLicenses"
  }

  data object BatteryOptimization : Destination

  @Serializable
  data class AddContent(val origin: Origin) : Compose {
    override val trackingName: String = "AddContent"
  }
}
