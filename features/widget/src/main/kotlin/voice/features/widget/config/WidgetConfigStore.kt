package voice.features.widget.config

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.GridMode
import voice.core.data.store.GridModeStore

enum class WidgetCoverShape(val aspectRatio: Float) {
  Square(1f),
  Book(2f / 3f),
}

@SingleIn(AppScope::class)
@Inject
class WidgetConfigStore(
  private val context: Context,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
) {
  private val prefs = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

  fun getAlpha(widgetId: Int): Int = prefs.getInt("alpha_$widgetId", 255)

  fun setAlpha(
    widgetId: Int,
    alpha: Int,
  ) {
    prefs.edit { putInt("alpha_$widgetId", alpha) }
  }

  fun getTextScale(widgetId: Int): Float = prefs.getFloat("scale_$widgetId", 1.0f)

  fun setTextScale(
    widgetId: Int,
    scale: Float,
  ) {
    prefs.edit { putFloat("scale_$widgetId", scale) }
  }

  /** Until one is chosen, the shape follows the library layout: book covers for the Books layout. */
  suspend fun getCoverShape(widgetId: Int): WidgetCoverShape {
    val saved = prefs.getString("cover_$widgetId", null)
    return WidgetCoverShape.entries.find { it.name == saved }
      ?: if (gridModeStore.data.first() == GridMode.BOOKS) WidgetCoverShape.Book else WidgetCoverShape.Square
  }

  fun setCoverShape(
    widgetId: Int,
    shape: WidgetCoverShape,
  ) {
    prefs.edit { putString("cover_$widgetId", shape.name) }
  }

  fun getChapterButtons(widgetId: Int): Boolean = prefs.getBoolean("chapter_buttons_$widgetId", false)

  fun setChapterButtons(
    widgetId: Int,
    show: Boolean,
  ) {
    prefs.edit { putBoolean("chapter_buttons_$widgetId", show) }
  }

  fun clear(widgetId: Int) {
    prefs.edit {
      remove("alpha_$widgetId")
      remove("scale_$widgetId")
      remove("cover_$widgetId")
      remove("chapter_buttons_$widgetId")
    }
  }
}
