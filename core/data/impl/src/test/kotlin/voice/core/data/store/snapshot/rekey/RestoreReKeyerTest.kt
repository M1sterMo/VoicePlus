package voice.core.data.store.snapshot.rekey

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.store.snapshot.BookCharacterDto
import voice.core.data.store.snapshot.BookContentDto
import voice.core.data.store.snapshot.BookmarkDto
import voice.core.data.store.snapshot.ChapterNameOverrideDto
import voice.core.data.store.snapshot.ListeningSessionDto

/**
 * Corruption-critical pure re-keyer. The overriding invariant under test: book A's position / bookmarks /
 * sessions must NEVER land on book B. Every gate failure must surface (not silently drop, not guess).
 */
class RestoreReKeyerTest {

  // ---- id schemes (old = pre-wipe URI, new = post-re-grant URI) ----

  private fun oldCid(
    relPath: String,
    rel: String,
  ) = "oldc://$relPath/$rel"
  private fun newCid(
    relPath: String,
    rel: String,
  ) = "newc://$relPath/$rel"

  // ---- builders ----

  private fun stamp(
    relPath: String,
    folder: String,
    children: List<String>,
    authority: String = EXTERNAL_STORAGE_AUTHORITY,
    singleFile: Boolean = false,
  ) = BookIdentityStamp(
    authority = authority,
    isSingleFile = singleFile,
    relPath = relPath,
    folderName = folder,
    children = children,
  )

  private fun snapBook(
    relPath: String,
    chapterRelNames: List<String>,
    folder: String = "Folder",
    currentRel: String = chapterRelNames.first(),
    position: Long = 0,
    lastPlayed: Long = 0,
    bookmarks: List<BookmarkDto> = emptyList(),
    overrides: List<ChapterNameOverrideDto> = emptyList(),
    sessions: List<ListeningSessionDto> = emptyList(),
    characters: List<BookCharacterDto> = emptyList(),
    snapDuration: Long = 1_000,
    authority: String = EXTERNAL_STORAGE_AUTHORITY,
    singleFile: Boolean = false,
    bookId: String = "old://$relPath",
  ): SnapshotBook = SnapshotBook(
    stamp = stamp(relPath, folder, chapterRelNames, authority, singleFile),
    content = BookContentDto(
      id = bookId, playbackSpeed = 1.5f, skipSilence = true, isActive = true,
      lastPlayedAtEpochMillis = lastPlayed, author = "Author", name = "Book", addedAtEpochMillis = 10,
      chapters = chapterRelNames.map { oldCid(relPath, it) }, currentChapter = oldCid(relPath, currentRel),
      positionInChapter = position, coverPath = "/dead/cover.jpg", gain = 2f, genre = "Genre",
      narrator = "Narrator", series = "Series", part = "Part", chapterNameOffset = 3, seriesGroup = "My collection", seriesOrder = 2,
    ),
    chapters = chapterRelNames.map { SnapChapter(ChapterId(oldCid(relPath, it)), it, snapDuration) },
    bookmarks = bookmarks,
    overrides = overrides,
    sessions = sessions,
    characters = characters,
  )

  private fun scanBook(
    relPath: String,
    chapterRelNames: List<String>,
    folder: String = "Folder",
    durations: List<Long> = chapterRelNames.map { 1_000L },
    newBookId: String = "new://$relPath",
  ): ScannedBook = ScannedBook(
    newBookId = BookId(newBookId),
    stamp = stamp(relPath, folder, chapterRelNames),
    chapters = chapterRelNames.mapIndexed { i, rel -> ScannedChapter(ChapterId(newCid(relPath, rel)), rel, durations[i]) },
  )

  private fun bookmarkDto(
    relPath: String,
    rel: String,
    time: Long,
    id: String,
  ) = BookmarkDto(
    bookId = "old://$relPath",
    chapterId = oldCid(relPath, rel),
    title = "bm",
    time = time,
    addedAtEpochMillis = 5,
    setBySleepTimer = false,
    id = id,
  )

  // ---- scenarios ----

  @Test
  fun `no path match surfaces NO_PATH_MATCH`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/Dune", listOf("01.mp3", "02.mp3"))),
      scanned = listOf(scanBook("primary:Books/Other", listOf("01.mp3"))),
    )
    r.matched.shouldBe(emptyList())
    r.unmatched.single().reason shouldBe UnmatchedReason.NO_PATH_MATCH
  }

  @Test
  fun `happy path re-keys onto the new book id and chapter ids`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/Dune", listOf("01.mp3", "02.mp3"), currentRel = "02.mp3", position = 400)),
      scanned = listOf(scanBook("primary:Books/Dune", listOf("01.mp3", "02.mp3"))),
    )
    r.unmatched.shouldBe(emptyList())
    val m = r.matched.single()
    m.content.id shouldBe BookId("new://primary:Books/Dune")
    m.content.chapters shouldBe listOf(
      ChapterId(newCid("primary:Books/Dune", "01.mp3")),
      ChapterId(newCid("primary:Books/Dune", "02.mp3")),
    )
    m.content.currentChapter shouldBe ChapterId(newCid("primary:Books/Dune", "02.mp3"))
    m.content.positionInChapter shouldBe 400L
    m.content.seriesGroup shouldBe "My collection"
    m.content.seriesOrder shouldBe 2
    // carried settings survive
    m.content.playbackSpeed shouldBe 1.5f
    m.content.chapterNameOffset shouldBe 3
    m.content.cover shouldBe null
    m.content.isActive shouldBe true
  }

  @Test
  fun `content changed (relName multiset differs) refuses cross-attach`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/Dune", listOf("01.mp3", "02.mp3"))),
      scanned = listOf(scanBook("primary:Books/Dune", listOf("01.mp3", "99-different.mp3"))),
    )
    r.matched.shouldBe(emptyList())
    r.unmatched.single().reason shouldBe UnmatchedReason.CONTENT_CHANGED
  }

  @Test
  fun `duplicate relPath - exactly one passes the confirmer attaches to it`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/X", listOf("01.mp3", "02.mp3"))),
      scanned = listOf(
        scanBook("primary:Books/X", listOf("01.mp3", "02.mp3"), newBookId = "new://match"),
        scanBook("primary:Books/X", listOf("aa.mp3", "bb.mp3"), newBookId = "new://nomatch"),
      ),
    )
    r.matched.single().content.id shouldBe BookId("new://match")
  }

  @Test
  fun `duplicate relPath - two pass the confirmer is AMBIGUOUS`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/X", listOf("01.mp3"))),
      scanned = listOf(
        scanBook("primary:Books/X", listOf("01.mp3"), newBookId = "new://a"),
        scanBook("primary:Books/X", listOf("01.mp3"), newBookId = "new://b"),
      ),
    )
    r.matched.shouldBe(emptyList())
    r.unmatched.single().reason shouldBe UnmatchedReason.AMBIGUOUS
  }

  @Test
  fun `duplicate relPath - none passes is AMBIGUOUS`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/X", listOf("01.mp3", "02.mp3"))),
      scanned = listOf(
        scanBook("primary:Books/X", listOf("zz.mp3"), newBookId = "new://a"),
        scanBook("primary:Books/X", listOf("yy.mp3"), newBookId = "new://b"),
      ),
    )
    r.matched.shouldBe(emptyList())
    r.unmatched.single().reason shouldBe UnmatchedReason.AMBIGUOUS
  }

  @Test
  fun `volume-namespaced relPath never swaps two books across volumes`() {
    val primary = "primary:Books/Dune"
    val sd = "ABCD-1234:Books/Dune"
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook(primary, listOf("01.mp3")), snapBook(sd, listOf("01.mp3"))),
      scanned = listOf(scanBook(primary, listOf("01.mp3")), scanBook(sd, listOf("01.mp3"))),
    )
    r.unmatched.shouldBe(emptyList())
    r.matched.map { it.content.id.value }.toSet() shouldBe setOf("new://$primary", "new://$sd")
    // the primary book's chapter re-keyed under the primary volume, never the SD one
    val primaryBook = r.matched.single { it.content.id == BookId("new://$primary") }
    primaryBook.content.chapters.single() shouldBe ChapterId(newCid(primary, "01.mp3"))
  }

  @Test
  fun `permuted scanned order re-keys chapter-scoped data by relName not index`() {
    val relPath = "primary:Books/Dune"
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(
        snapBook(
          relPath,
          listOf("01.mp3", "02.mp3"),
          currentRel = "01.mp3",
          bookmarks = listOf(bookmarkDto(relPath, "02.mp3", time = 50, id = "00000000-0000-0000-0000-000000000001")),
          overrides = listOf(
            ChapterNameOverrideDto(oldCid(relPath, "02.mp3"), markStartMs = 0, bookId = "old://$relPath", name = "Renamed"),
          ),
        ),
      ),
      // scanned in REVERSED order: index 0 is 02.mp3
      scanned = listOf(scanBook(relPath, listOf("02.mp3", "01.mp3"))),
    )
    val m = r.matched.single()
    // chapters preserve scanner order verbatim
    m.content.chapters shouldBe listOf(ChapterId(newCid(relPath, "02.mp3")), ChapterId(newCid(relPath, "01.mp3")))
    // currentChapter follows relName 01.mp3, not index 0
    m.content.currentChapter shouldBe ChapterId(newCid(relPath, "01.mp3"))
    // bookmark + override follow relName 02.mp3
    m.bookmarks.single().chapterId shouldBe ChapterId(newCid(relPath, "02.mp3"))
    m.bookmarks.single().bookId shouldBe BookId("new://$relPath")
    m.overrides.single().chapterId shouldBe newCid(relPath, "02.mp3")
    m.overrides.single().bookId shouldBe "new://$relPath"
    m.overrides.single().name shouldBe "Renamed"
  }

  @Test
  fun `subfolder filename collision is disambiguated by sub-path relName`() {
    val relPath = "primary:Books/Box"
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(
        snapBook(
          relPath,
          listOf("Disc1/01.mp3", "Disc2/01.mp3"),
          bookmarks = listOf(bookmarkDto(relPath, "Disc2/01.mp3", time = 10, id = "00000000-0000-0000-0000-000000000002")),
        ),
      ),
      scanned = listOf(scanBook(relPath, listOf("Disc1/01.mp3", "Disc2/01.mp3"))),
    )
    r.matched.single().bookmarks.single().chapterId shouldBe ChapterId(newCid(relPath, "Disc2/01.mp3"))
  }

  @Test
  fun `currentChapter anchor missing falls back to first chapter at position zero`() {
    val relPath = "primary:Books/Dune"
    val r = RestoreReKeyer.reKey(
      // currentRel "ghost.mp3" is not among the stamped chapters/children
      snapshot = listOf(snapBook(relPath, listOf("01.mp3", "02.mp3"), currentRel = "ghost.mp3", position = 500)),
      scanned = listOf(scanBook(relPath, listOf("01.mp3", "02.mp3"))),
    )
    val m = r.matched.single()
    m.content.currentChapter shouldBe ChapterId(newCid(relPath, "01.mp3"))
    m.content.positionInChapter shouldBe 0L
  }

  @Test
  fun `positions clamp to the fresh scanned duration`() {
    val relPath = "primary:Books/Dune"
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(
        snapBook(
          relPath,
          listOf("01.mp3"),
          currentRel = "01.mp3",
          position = 9_000,
          bookmarks = listOf(bookmarkDto(relPath, "01.mp3", time = 9_000, id = "00000000-0000-0000-0000-000000000003")),
        ),
      ),
      scanned = listOf(scanBook(relPath, listOf("01.mp3"), durations = listOf(1_000))),
    )
    val m = r.matched.single()
    m.content.positionInChapter shouldBe 1_000L
    m.bookmarks.single().time shouldBe 1_000L
  }

  @Test
  fun `opaque provider is gated out as OPAQUE_PROVIDER`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("doc-id-abc", listOf("01.mp3"), authority = "com.google.android.apps.docs.storage")),
      scanned = listOf(scanBook("doc-id-abc", listOf("01.mp3"))),
    )
    r.matched.shouldBe(emptyList())
    r.unmatched.single().reason shouldBe UnmatchedReason.OPAQUE_PROVIDER
  }

  @Test
  fun `single-file book re-keys by its stable document id`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/book.m4b", listOf("book.m4b"), singleFile = true)),
      scanned = listOf(scanBook("primary:Books/book.m4b", listOf("book.m4b"))),
    )
    r.unmatched.shouldBe(emptyList())
    r.matched.single().content.id shouldBe BookId("new://primary:Books/book.m4b")
  }

  @Test
  fun `re-running is idempotent (pure function of inputs)`() {
    val snap = listOf(snapBook("primary:Books/Dune", listOf("01.mp3", "02.mp3")))
    val scan = listOf(scanBook("primary:Books/Dune", listOf("01.mp3", "02.mp3")))
    RestoreReKeyer.reKey(snap, scan) shouldBe RestoreReKeyer.reKey(snap, scan)
  }

  @Test
  fun `character notes are re-keyed onto the new book id`() {
    val relPath = "primary:Books/Dune"
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(
        snapBook(
          relPath,
          listOf("01.mp3"),
          characters = listOf(
            BookCharacterDto(
              id = 42,
              bookId = "old://$relPath",
              name = "Paul",
              description = "the heir",
              sortOrder = 1,
              createdAtEpochMillis = 100,
              updatedAtEpochMillis = 200,
            ),
          ),
        ),
      ),
      scanned = listOf(scanBook(relPath, listOf("01.mp3"))),
    )
    val character = r.matched.single().characters.single()
    character.bookId shouldBe BookId("new://$relPath")
    character.name shouldBe "Paul"
    character.description shouldBe "the heir"
    character.id shouldBe 0L // fresh PK so Room assigns a new one
  }

  @Test
  fun `a materially different chapter duration vetoes the match (in-place re-rip)`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/Dune", listOf("01.mp3"), snapDuration = 1_000)),
      scanned = listOf(scanBook("primary:Books/Dune", listOf("01.mp3"), durations = listOf(60_000))),
    )
    r.matched.shouldBe(emptyList())
    r.unmatched.single().reason shouldBe UnmatchedReason.CONTENT_CHANGED
  }

  @Test
  fun `minor duration jitter still matches (re-encode of the same audio)`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/Dune", listOf("01.mp3"), snapDuration = 60_000)),
      scanned = listOf(scanBook("primary:Books/Dune", listOf("01.mp3"), durations = listOf(61_000))),
    )
    r.unmatched.shouldBe(emptyList())
    r.matched.single().content.id shouldBe BookId("new://primary:Books/Dune")
  }

  @Test
  fun `a candidate with no chapters surfaces INVALID, never a crash`() {
    val r = RestoreReKeyer.reKey(
      snapshot = listOf(snapBook("primary:Books/Empty", chapterRelNames = emptyList(), currentRel = "")),
      scanned = listOf(scanBook("primary:Books/Empty", chapterRelNames = emptyList())),
    )
    r.matched.shouldBe(emptyList())
    r.unmatched.single().reason shouldBe UnmatchedReason.INVALID
  }
}
