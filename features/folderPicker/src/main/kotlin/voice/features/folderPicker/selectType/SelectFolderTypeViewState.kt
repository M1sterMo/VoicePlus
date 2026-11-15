package voice.features.folderPicker.selectType

internal data class SelectFolderTypeViewState(
  val folderName: String,
  val selectedMode: FolderMode,
  val guessedMode: FolderMode?,
  // the books each mode would lead to, null while the folder is looked through
  val booksByMode: Map<FolderMode, List<Book>>?,
) {

  val loading: Boolean get() = booksByMode == null
  val books: List<Book> get() = booksByMode?.get(selectedMode).orEmpty()

  data class Book(
    val name: String,
    val author: String?,
    val fileCount: Int,
  )
}
