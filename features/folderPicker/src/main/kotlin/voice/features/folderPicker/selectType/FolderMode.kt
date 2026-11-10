package voice.features.folderPicker.selectType

import voice.core.data.folders.FolderType

internal enum class FolderMode {
  SingleBook,
  Audiobooks,
  Authors,
  ;

  fun toFolderType(): FolderType = when (this) {
    SingleBook -> FolderType.SingleFolder
    Audiobooks -> FolderType.Root
    Authors -> FolderType.Author
  }
}
