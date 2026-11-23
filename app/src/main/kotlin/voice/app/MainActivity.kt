package voice.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.filterNotNull
import voice.app.navigation.NavEntryResolver
import voice.app.navigation.StartDestinationProvider
import voice.app.navigation.showCurrentBook
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.ui.LocalSplashScreenHolds
import voice.core.ui.SplashScreenHolds
import voice.core.ui.VoiceTheme
import voice.features.review.ReviewFeature
import voice.navigation.Destination
import voice.navigation.NavigationCommand
import voice.navigation.Navigator

@ContributesTo(AppScope::class)
interface MainActivityGraph {
  fun inject(activity: MainActivity)
}

class MainActivity : AppCompatActivity() {

  @Inject
  private lateinit var navigator: Navigator

  @Inject
  lateinit var navEntryResolver: NavEntryResolver

  @Inject
  private lateinit var startDestinationProvider: StartDestinationProvider

  @Inject
  @CurrentBookStore
  private lateinit var currentBookStore: DataStore<BookId?>

  private val splashScreenHolds = SplashScreenHolds()
  private var contentComposed = false

  override fun onCreate(savedInstanceState: Bundle?) {
    rootGraphAs<MainActivityGraph>().inject(this)
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()

    setContent {
      @Suppress("UNCHECKED_CAST")
      val backStack = rememberNavBackStack(*startDestinationProvider(intent).toTypedArray()) as MutableList<Destination.Compose>
      LaunchedEffect(backStack) { currentBookStore.data.filterNotNull().collect(backStack::showCurrentBook) }
      SideEffect { contentComposed = true }
      CompositionLocalProvider(LocalSplashScreenHolds provides splashScreenHolds) {
        VoiceTheme {
          val dialogStrategy = remember { DialogSceneStrategy<Destination.Compose>() }

          SharedTransitionLayout {
            NavDisplay(
              backStack = backStack,
              entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
              sceneStrategies = listOf(dialogStrategy),
              sharedTransitionScope = this,
              onBack = {
                if (backStack.size > 1) {
                  backStack.removeLastOrNull()
                }
              },
              entryProvider = { key ->
                navEntryResolver.create(
                  key = key,
                  sharedTransitionScope = this@SharedTransitionLayout,
                )
              },
            )
          }

          LaunchedEffect(navigator) {
            navigator.navigationCommands.collect { command ->
              when (command) {
                is NavigationCommand.GoTo -> {
                  when (val destination = command.destination) {
                    is Destination.Compose -> {
                      backStack += destination
                    }
                    is Destination.Activity -> {
                      startActivity(destination.intent)
                    }
                    Destination.BatteryOptimization -> {
                      toBatteryOptimizations()
                    }
                    is Destination.Website -> {
                      try {
                        startActivity(Intent(Intent.ACTION_VIEW, destination.url.toUri()))
                      } catch (exception: ActivityNotFoundException) {
                        Logger.w(exception)
                      }
                    }
                  }
                }
                NavigationCommand.GoBack -> {
                  if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                  }
                }
                is NavigationCommand.SetRoot -> {
                  backStack.clear()
                  backStack.add(command.root)
                }
              }
            }
          }

          ReviewFeature()
        }
      }
    }

    // also with saved state: after process death, the app cold starts with a splash screen too
    keepSplashScreenUntilContentIsReady()
  }

  /**
   * Holds back the first frame, which keeps the splash screen up, until the start screen has its content (see
   * [SplashScreenHolds]). A timeout makes sure a slow load never traps the user on it.
   */
  private fun keepSplashScreenUntilContentIsReady() {
    val content = findViewById<View>(android.R.id.content)
    val releaseAt = SystemClock.uptimeMillis() + SPLASH_SCREEN_TIMEOUT_MS
    content.viewTreeObserver.addOnPreDrawListener(
      object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          val ready = contentComposed && !splashScreenHolds.holding
          if (ready || SystemClock.uptimeMillis() > releaseAt) {
            content.viewTreeObserver.removeOnPreDrawListener(this)
            return true
          }
          return false
        }
      },
    )
  }

  private fun toBatteryOptimizations() {
    val intent = Intent()
      .apply {
        @Suppress("BatteryLife")
        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        data = "package:$packageName".toUri()
      }
    try {
      startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      Logger.w(e, "Can't request ignoring battery optimizations")
    }
  }

  companion object {

    const val NI_GO_TO_BOOK = "niGotoBook"
    private const val SPLASH_SCREEN_TIMEOUT_MS = 2000L

    fun goToBookIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
      putExtra(NI_GO_TO_BOOK, true)
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
    }
  }
}
