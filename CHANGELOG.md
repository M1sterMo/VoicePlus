# VoicePlus Changelog

## Unreleased

- Titles, authors, narrators and chapter names that were tagged in Windows-1251, or in UTF-8, into a frame declaring ISO-8859-1 now read as they were written instead of turning into "Âàëèñ". Latin names keep their accents, and files whose tags are already correct are left untouched. Books scanned before the update take their names over on the next scan of their folder, which the "Use folder names instead of file tags" setting also forces.

---

## v1.31 — About This Book, Ratings & a New Grid

- About this book: a sheet with the cover, author, narrator, series, genre, length and synopsis, opened from the first item of a book's long-press menu or from an optional player toolbar button ([#39](https://github.com/M1sterMo/VoicePlus/discussions/39)).
- Synopses come from a `desc.txt` or `description.txt` beside the audio, or from the files' description or comment tags; existing books pick theirs up on the next scan. A synopsis typed in Edit book wins, survives rescans and backups, and "Use the file's synopsis" goes back to the file's.
- Ratings: give a book 1–5 stars in its About sheet, when the player reaches its end (asked once per book), or from its page in listening stats. Ratings show as ★ 4 in the library and in the stats Finished list.
- Grid view redesigned after the original Voice: whole square covers without cards, two or three to a row (the books-per-row setting), a thin bar while a book is in progress, and one line under the title: time left while listening, the length when sorted by length, otherwise the author ([#42](https://github.com/M1sterMo/VoicePlus/discussions/42)).
- Series stacks show the next book stepping out up and to the left behind the first, the one behind a tone darker and, in Grid, the front one raised on a soft shadow, with a count badge and a progress bar with one segment per book. An opened series follows the library's view and books per row.
- Smoother scrolling and series animations on phones with slower graphics: book jackets are drawn once into their cover image and reused, instead of being redrawn every frame ([#43](https://github.com/M1sterMo/VoicePlus/issues/43)). Opening a series runs at the display's full rate, and a cover flying to or from the player keeps its shape.
- Renaming or moving a book's folder, or changing a folder's mode, keeps everything: progress and speed, bookmarks, listening history and stats, ratings, chapter names, characters, its shelf and series place, and up next.
- Scanning ported from upstream Voice: adding or removing a folder scans right away, books copied in while the app was away show up when you return, read-only folders work, covers are found more cheaply, and the library opens straight onto your books without a progress bar.
- Folders that lost access show "No access" with an "Allow access" button, the audiobook folders list now lives in Settings, and tips help when a folder adds no books.
- Listening stats: a finished book's page shows the book's length beside your listening time, and tiles in a row share one height.
- Appearance settings, the stats book page and the rating card open fully instead of half-way.
- Backups include synopses and ratings (backup format 9; older backups still restore).

---

## v1.30 — Up Next, Shared Characters & Refreshed Stats

- Up next: queue books to play one after another from the book menu, and reorder them in the queue view or by dragging on the library's Up next shelf.
- Character lists are shared by every book in a series, with a "Book N" tag showing where each character was added.
- Listening stats: refreshed page and a new sheet for each finished book; books marked completed now get a finish date, which can be changed.
- Library: sort by book length, hold and drag to reorder or move books, add and settings buttons on the top bar, and "left" after the time remaining.
- Player: book progress and time left under the title, the position slider above the chapter switcher, a tap on the chapter length for a countdown (remembered), chapter times that follow playback speed, a book-style cover in Books view, and a glow in the cover's colours.
- Restyled widget with square or book covers and optional chapter buttons.
- Simpler adding of books, with a step that finds and counts the books in a folder.
- Appearance: light, dark or system theme, colour choices and pictured layout options.
- M4B files with chapters on a separate track or packed into one chunk now show all chapters — thanks [@lecrackzor](https://github.com/lecrackzor).
- Eight smaller scanner, playback and library fixes.

---

## v1.29 — Shelves, Series & Book View

- Book-inspired covers, personal shelves and series stacks, with two or three books per row.
- Editable shelf and series order, including drag-to-move between shelves.
- Appearance controls for Books, Grid and List layouts.
- Title and cover edits survive rescans and portable backups without changing audio files.
- More stable chapter-number corrections across irregular chapter markers.
- Protection against accidental media-button resume after the sleep timer finishes.
- Optional remembered volume boost across all books.
- Updated dependencies and phone/tablet screenshots, with distinct F-Droid screenshot slots.

---

## v1.28 — Playback Toolbar & Completion Badges

- Customize the now-playing toolbar with up to four shortcuts; choices are included in settings backups.
- Add quick bookmarks alongside named bookmarks.
- Completed books show a completion badge and finish date.
- Refreshed phone and tablet screenshots.

---

## v1.27 — Chapter, Search & Android Auto Fixes

- Later chapter corrections preserve earlier and restored chapter names.
- Long-press menus work in search results — thanks [@JamesDBartlett3](https://github.com/JamesDBartlett3).
- Android Auto forward/rewind keeps the correct direction without changing headset preferences — thanks [@geofgowan](https://github.com/geofgowan) for reporting.

---

## v1.26 — Listening Statistics & Playback Fixes

- Clearer listening records, recent totals, finished books and relisten counts.
- Playback speed changes are remembered — thanks [@tomwhat](https://github.com/tomwhat).
- Improved Android backup restore for listening history and cover art.

---

## v1.25 — Lock Screen, Bookmarks & Logs

- Customize lock-screen progress, secondary text, and chapter/timed-skip controls.
- Clearer, more compact bookmarks and listening logs with chapter positions and timestamps.
- Sleep timers no longer create automatic bookmarks.
- Suggest ideas or report problems directly from Settings.

---

## v1.24 — Lock-Screen Slider, System Backup & Polish

### Playback
- **Lock-screen slider modes:** choose what the lock-screen slider seeks — the current chapter (default), the whole audiobook, or disabled.
- Your live position is preserved across durable saves, and corrected chapter names stay aligned with playback everywhere.

### Backup & Restore
- **Survives reinstall automatically:** encrypted Android system backup now restores playback progress, bookmarks, listening history and stats, character lists, chapter-name fixes, hidden books, and settings on a reinstall or new phone (Android 12+ device transfer included). Audiobook folders must be re-granted; audio files and covers are not uploaded. The folder backup from v1.23 remains the manual, portable option.
- Covers are preserved when restoring a backup over an existing library.

### Library & Scanner
- Faster scans: unchanged files are no longer re-read, and file changes are detected even when the timestamp doesn't change.
- Fixed high memory use when scanning m4b books with many chapters, and chapter positions stay precise on very long files.
- Natural sorting now handles accented titles correctly (é, ö, č…).

### Sleep Timer & Widget
- End-of-chapter timer counts each chapter boundary exactly once, and a timer left over after playback stops is disarmed.
- Widget controls no longer vanish after some updates; dark-mode icon tint restored.

### UI
- Settings redesigned into Material 3 sections.
- Covers animate between library and player, and the library remembers your scroll position.
- Refreshed listening statistics screen and editors.

---

## v1.23.1 — Media Button Hotfix

- Fixed Bluetooth headphones ignoring configured double/triple-click actions when their firmware reports taps as Previous/Next media keys.
- Physical keyboard Previous/Next keys still keep their literal behavior.

---

## v1.23 — Backup & Restore, Reliable Listening Log

### Backup & Restore
- **Back up to a folder you choose:** Pick any folder (SD card, cloud-synced, USB drive) and VoicePlus keeps timestamped backup files there — positions, bookmarks, listening history and stats, character lists, chapter-name fixes, hidden books, and your settings. Choosing a folder is all it takes: the first backup is written immediately.
- **Automatic and manual saves:** An automatic save is written about once a day (the newest 7 are kept); "Back up now" creates a manual save point that is never cleaned up and always captures the state as of that moment — including changes that touch only settings, hidden books, or the listening log.
- **Restore from a list:** Every save in the folder is listed with its date — restore the one you want, or delete saves you no longer need. Backups are never overwritten, so a failed write can't damage an existing good save.
- **Survives reinstall:** After an uninstall or a new phone, re-grant your audiobook folders and restore — books are matched back to their files, with anything unmatchable clearly listed instead of guessed.
- **Safety rails:** Every backup is verified after writing; unreadable files are skipped at restore time; backups from a newer app version are refused rather than half-read.

### Listening Log Reliability
- **Sessions survive being killed:** The in-flight session is checkpointed every 30 seconds, so a crash, force-stop, or reboot mid-listen now records the session (with an "Interrupted" badge) instead of losing it entirely.
- **Fixed listening not being recorded after swiping the app away** — the playback service could end up in a state where nothing was logged until the next reboot.
- **Fixed pauses sometimes not being recorded:** when the pause's auto-rewind made the player buffer for a moment, the session was never closed — it silently spanned the whole paused stretch and inflated listening stats.
- **Fixed stuttering playback fragmenting sessions** into pieces small enough to be discarded.
- **Fixed sleep-timer stops rewinding twice** and leaving a stray "position set" entry in the log; a sleep stop that never played also no longer mislabels the next session.
- **Switching books mid-play** now ends the previous book's session correctly instead of billing the time to the new book.
- **Chapter and bookmark jumps are labeled:** picking a chapter or opening a bookmark now shows as "Went to chapter" with the chapter name, instead of an anonymous "Jumped" entry — so the log no longer shows seeks you don't remember making.
- **"Resumed after sleep" marker:** a session started within an hour of a sleep-timer stop is chipped in the log, so in the morning you can tell where the timer stopped you (the Sleep entry) from listening that happened while dozing — and tap either to jump back.
- **Positions shown as time into the chapter** ("Chapter 6 · 0:15") instead of the raw file position, matching the player screen.
- Listening history, stats, and end-reason badges are now included in backups and survive restore without duplicating.

### Library
- **No more rescan on every visit:** returning to the library from a book or settings no longer re-scans your folders (and no longer flashes the progress bar) — the automatic scan now runs at most every 5 minutes. Adding or removing a folder, deleting a book, and restoring a backup still scan immediately.

### Playback
- **Smoother scrubbing:** fixed the seek slider jittering under your finger — the time label was resizing the track as its digits changed mid-drag — and the thumb no longer snaps back briefly after you release it.

### Bookmarks
- Fixed a crash when a bookmark pointed at a chapter that no longer exists; selecting such a bookmark now reports it as unavailable instead of seeking into nothing.
- Deleting a bookmark now offers Undo.
- Named bookmarks show their chapter and position underneath, so they stay locatable.
- Blank bookmark names are rejected, and the media-button quick bookmark confirms with a toast.

---

## v1.22 — Chapter Name Editor

### Chapter Name Editor
- **One-step number fix:** New "Edit chapter names" entry in the playback screen's overflow menu. A single offset control shifts every chapter number at once, for when an intro or prologue throws the count off by one, or a conversion tool renumbered the chapters.
- **Digits and words:** Recognizes both numerals ("Chapter 5") and English number words ("Chapter Five"), from one to one hundred.
- **Per-chapter overrides:** Rename any individual chapter by hand. Manual names take precedence over the offset.
- **Non-destructive and persistent:** Corrections are stored separately from your files, survive library re-scans, and never modify the audio. Reset everything or clear a single override at any time.
- **Applied everywhere:** Corrected names appear across the playback screen, bookmarks, the listening log, the home-screen widget, and Android Auto for one-file-per-chapter books.
- Available for books with more than one chapter mark.

### Use Folder Names Instead of File Tags (Experimental)
- Toggling this option now re-scans your library and updates existing books immediately, instead of only applying to newly added books.
- Tidied up the settings row layout and confirmation dialog wording.

---

## v1.21 — Bug fixes

- **Open Source Licenses screen:** Fixed crash that prevented the screen from opening on release builds.
- **Sleep Timer skip-reset:** Skipping forward or backward now resets the sleep timer when auto-reset is enabled, matching the existing behavior for volume changes and resuming from pause.

---

## v1.20 — Widget Overhaul, Quick Bookmark, Experimental Playback Persistence

### Widget Overhaul
- Consolidated to a single resizable widget showing cover, title, chapter, and skip controls.
- Configuration screen with live preview lets you adjust opacity and text scale per widget instance.
- Fixed widget icons disappearing in dark mode.

### Media Button Quick Bookmark
- New "Quick bookmark" action assignable to double or triple headset button click — saves a bookmark instantly without interrupting playback.

### Experimental Playback Persistence
- New Settings toggle (off by default). When on, position saves every 5 minutes instead of every second, reducing battery drain at the cost of up to 5 minutes of position loss if the app is force-killed.

---

## v1.19 — Dependency & Platform Updates

- Updated Kotlin, AGP, Compose, Media3, and other core dependencies to current stable versions.
- Improved chapter name detection for books with multiple audio files.
- Various stability and compatibility improvements.

---

## v1.18 — Battery Efficiency Optimization

- **Reduced position save frequency:** Position now saves every 1 second instead of every 400ms, reducing battery drain during playback.
- **Optimized database queries:** Added indexes on listening session queries for faster statistics lookups.
- **Streamlined statistics computation:** Stats calculations debounce updates, reducing CPU load.
- **Consolidated metadata extraction:** Audio files are now parsed once per scan instead of twice.

---

## v1.17 — N-Chapter Sleep Timer

- **Selectable chapter count:** The "End of Chapter" sleep timer now lets you choose how many chapters to play before pausing using `−` / `+` buttons.
- **Inclusive count:** The selected number includes the currently-playing chapter (e.g. `3` plays the current chapter and the next two, then pauses).
- **Persisted preference:** The chosen count is saved and restored when the dialog is reopened.
- **Live countdown badge:** The indicator above cover art shows "End of N Chapter(s)" and counts down as each chapter ends.
- **Scrubber fix:** Resolved a visual stutter when dragging the progress scrubber during playback.

---

## v1.16 — Sleep Timer Enhancements & Media Button Actions

### Sleep Timer Auto-Reset
- Timer resets to full duration when playback is paused and resumed.
- Timer resets to full duration when device volume is changed during an active timer.

### Customizable Media Button Actions
- **Double and triple click support** for headset play/pause button.
- Assignable actions: Skip Forward, Skip Backward, Skip Forward Chapter, Skip Backward Chapter, None.
- Works with both simple one-button headsets and multi-button headsets.

---

## v1.13 — Privacy & Clean Settings

### Analytics & Tracking — Fully Removed
- Removed Firebase Analytics, Crashlytics, and Remote Config entirely.
- No usage data, crash reports, or remote feature flags — zero external network calls.
- Removed analytics consent prompt from onboarding and analytics toggle from Settings.

### Settings Cleanup
- Removed external links to the upstream project's GitHub, support channels, translation platform, and FAQ.

---

## v1.0.0 — Foundation

### Character Lists
- Per-book character roster with reordering and deletion confirmation.

### Listening Logs & Statistics
- Session tracking with start time, end time, and duration logging.
- Statistics dashboard with charts and historical listening trends.

### Library Management
- Hide/remove books from the library with a dedicated Hidden Books management screen in Settings.
- Library sections remember their expanded/collapsed state across sessions.

### Playback & Widget
- Browse the library without interrupting playback.
- Home screen widget with skip labels and chapter indicator.
- Fixed chapter name parsing when the `TIT2` tag matched the album title.

---

*Forked from Voice v26.2.4*
