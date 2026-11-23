package voice.core.scanner

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Calls [scan] when the app comes back from the background. The app usually stays alive there, so books
 * copied in meanwhile would otherwise wait for the library's scan cooldown to run out. A rotation stops
 * and starts the activity too, but doesn't count as leaving.
 */
internal class ScanOnReturnToForeground(private val scan: () -> Unit) : Application.ActivityLifecycleCallbacks {

  private var startedActivities = 0
  private var wentToBackground = false

  override fun onActivityStarted(activity: Activity) {
    if (startedActivities++ == 0 && wentToBackground) {
      wentToBackground = false
      scan()
    }
  }

  override fun onActivityStopped(activity: Activity) {
    if (--startedActivities == 0 && !activity.isChangingConfigurations) wentToBackground = true
  }

  override fun onActivityCreated(
    activity: Activity,
    savedInstanceState: Bundle?,
  ) = Unit

  override fun onActivityResumed(activity: Activity) = Unit

  override fun onActivityPaused(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(
    activity: Activity,
    outState: Bundle,
  ) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit
}
