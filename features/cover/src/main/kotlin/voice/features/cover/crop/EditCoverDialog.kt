package voice.features.cover.crop

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.DialogSceneStrategy
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.store.GridModeStore
import voice.core.scanner.CoverSaver
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import kotlin.coroutines.cancellation.CancellationException
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface EditCoverComponent {
  val coverSaver: CoverSaver

  @get:GridModeStore val gridModeStore: DataStore<GridMode>
}

@ContributesTo(AppScope::class)
interface EditCoverDialogProvider {

  @Provides
  @IntoSet
  fun editCoverDialogNavEntryProvider(navigator: Navigator): NavEntryProvider<*> = NavEntryProvider<Destination.EditCover> { key ->
    NavEntry(key, metadata = DialogSceneStrategy.dialog()) {
      val gridMode by remember { rootGraphAs<EditCoverComponent>().gridModeStore.data }.collectAsState(initial = null)
      if (gridMode != null) {
        EditCoverDialog(
          coverUri = key.cover,
          bookId = key.bookId,
          onDismiss = navigator::goBack,
          initiallyBookShaped = gridMode == GridMode.BOOKS,
          editSession = key.editSession,
        )
      }
    }
  }
}

@Composable
fun EditCoverDialog(
  coverUri: Uri,
  bookId: BookId,
  onDismiss: () -> Unit,
  initiallyBookShaped: Boolean = false,
  editSession: String? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var cropOverlay: CropOverlay? by remember { mutableStateOf(null) }
  var imageWidth by remember { mutableIntStateOf(0) }
  var imageHeight by remember { mutableIntStateOf(0) }
  var imageAspectRatio by remember(coverUri) { mutableFloatStateOf(1f) }
  var imageLoaded by remember(coverUri) { mutableStateOf(false) }
  var bookShaped by rememberSaveable(coverUri) { mutableStateOf(initiallyBookShaped) }
  var saving by remember { mutableStateOf(false) }
  var saveFailed by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = { Text(text = stringResource(StringsR.string.cover)) },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
          listOf(true, false).forEach { isBook ->
            Row(
              modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .selectable(bookShaped == isBook, role = Role.RadioButton, onClick = { bookShaped = isBook }),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = bookShaped == isBook, onClick = null)
              Text(
                stringResource(if (isBook) StringsR.string.cover_crop_book else StringsR.string.cover_crop_square),
                Modifier.padding(start = 8.dp),
              )
            }
          }
        }
        Box(
          Modifier.heightIn(max = 360.dp).aspectRatio(imageAspectRatio).onSizeChanged {
            imageWidth = it.width
            imageHeight = it.height
          },
        ) {
          AsyncImage(
            model = coverUri,
            contentDescription = stringResource(StringsR.string.content_cover),
            modifier = Modifier.matchParentSize(),
            onSuccess = {
              val size = it.painter.intrinsicSize
              imageAspectRatio = size.width / size.height
              imageLoaded = true
            },
          )
          if (imageLoaded && imageWidth > 0 && imageHeight > 0) {
            AndroidView(
              modifier = Modifier.matchParentSize(),
              factory = { ctx ->
                CropOverlay(ctx).apply {
                  selectionOn = true
                  cropOverlay = this
                }
              },
              update = { it.aspectRatio = if (bookShaped) 2f / 3f else 1f },
            )
          }
        }
        if (saveFailed) Text(stringResource(StringsR.string.library_save_failed), color = MaterialTheme.colorScheme.error)
      }
    },
    confirmButton = {
      TextButton(
        enabled = imageLoaded && cropOverlay != null && !saving,
        onClick = {
          val rect = cropOverlay?.selectedRect
          if (rect != null && !rect.isEmpty) {
            val crop = CropTransformation(rect, sourceWidth = imageWidth, sourceHeight = imageHeight)
            saving = true
            saveFailed = false
            scope.launch {
              try {
                val bitmap = context.imageLoader
                  .execute(
                    ImageRequest.Builder(context)
                      .data(coverUri)
                      .transformations(crop)
                      .build(),
                  )
                  .drawable?.toBitmap()

                if (bitmap != null && rootGraphAs<EditCoverComponent>().coverSaver.save(bookId, bitmap, editSession)) {
                  onDismiss()
                } else {
                  saveFailed = true
                }
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (_: Exception) {
                saveFailed = true
              } finally {
                saving = false
              }
            }
          } else {
            onDismiss()
          }
        },
      ) {
        Text(text = stringResource(StringsR.string.dialog_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = { onDismiss() }, enabled = !saving) {
        Text(text = stringResource(StringsR.string.dialog_cancel))
      }
    },
  )
}
