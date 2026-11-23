package voice.core.scanner

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import voice.core.initializer.AppInitializer
import kotlin.time.Duration.Companion.seconds

@ContributesIntoSet(AppScope::class)
public class MediaScanInitializer(private val mediaScanTrigger: MediaScanTrigger) : AppInitializer {

  override fun onAppStart(application: android.app.Application) {
    mediaScanTrigger.scan()
    mediaScanTrigger.scanOnFolderChanges()
    // Past the library's 5-minute cooldown, but not within seconds of a scan: switching to a system picker
    // and back, or a restore that has just scanned, doesn't need another walk.
    application.registerActivityLifecycleCallbacks(
      ScanOnReturnToForeground { mediaScanTrigger.scan(skipIfCompletedWithin = 30.seconds) },
    )
  }
}
