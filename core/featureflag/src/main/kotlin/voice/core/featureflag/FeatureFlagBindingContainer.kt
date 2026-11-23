package voice.core.featureflag

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import voice.core.data.store.ExperimentalPlaybackPersistenceStore

@ContributesTo(AppScope::class)
interface FeatureFlagBindingContainer {

  @Provides
  @SingleIn(AppScope::class)
  @ReviewEnabledFeatureFlagQualifier
  fun reviewEnabledFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "review_enabled",
      description = "Shows the in-app review prompt when the review conditions are met.",
      defaultValue = false,
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @UserAgentFeatureFlagQualifier
  fun userAgentFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<String> {
    return factory.string(
      key = "user_agent",
      description = "Overrides the HTTP user agent used for cover downloads.",
      defaultValue = "Mozilla/5.0",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @FolderPickerInSettingsFeatureFlagQualifier
  fun folderPickerInSettingsFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "folder_picker_in_settings",
      description = "Shows the folder picker entry directly in settings.",
      // the library bar has no folders button, so settings is the only way to manage folders (Voice #3602)
      defaultValue = true,
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @ExperimentalPlaybackPersistenceQualifier
  fun experimentalPlaybackPersistenceFeatureFlag(@ExperimentalPlaybackPersistenceStore store: DataStore<Boolean>): FeatureFlag<Boolean> {
    return object : FeatureFlag<Boolean> {
      override val key: String = "experimental_playback_persistence"
      override val description: String = "Uses the experimental playback persistence implementation."
      override val type = Boolean::class
      override fun get(): Boolean = runBlocking { store.data.first() }
      override val flow: Flow<FeatureFlagValue<Boolean>> = store.data.map { FeatureFlagValue(it, false) }
      override fun overwrite(value: Boolean) {
        runBlocking { store.updateData { value } }
      }
      override fun clearOverwrite() = Unit
    }
  }

  @Provides
  @SingleIn(AppScope::class)
  @Media3AudioOffloadFeatureFlagQualifier
  fun media3AudioOffloadFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "media3_audio_offload",
      description = "Uses Media3 audio offload when the device supports it.",
    )
  }
}

@Qualifier
annotation class ReviewEnabledFeatureFlagQualifier

@Qualifier
annotation class UserAgentFeatureFlagQualifier

@Qualifier
annotation class FolderPickerInSettingsFeatureFlagQualifier

@Qualifier
annotation class ExperimentalPlaybackPersistenceQualifier

@Qualifier
annotation class Media3AudioOffloadFeatureFlagQualifier
