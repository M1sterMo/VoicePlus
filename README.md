# VoicePlus

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![F-Droid](https://img.shields.io/f-droid/v/com.github.mistermo_vibecode.voiceplus.svg?logo=f-droid)](https://f-droid.org/packages/com.github.mistermo_vibecode.voiceplus/)
[![Downloads](https://img.shields.io/github/downloads/Mistermo-vibecode/VoicePlus/total.svg)](https://github.com/Mistermo-vibecode/VoicePlus/releases)
[![CI](https://github.com/Mistermo-vibecode/VoicePlus/actions/workflows/ci.yml/badge.svg)](https://github.com/Mistermo-vibecode/VoicePlus/actions/workflows/ci.yml)

An open-source audiobook player for Android, with personal shelves, series grouping, listening history and flexible playback controls. No accounts, ads or analytics.

Built on [Voice](https://github.com/PaulWoitaschek/Voice) by Paul Woitaschek and contributors.

## Screenshots

<p align="center">
  <a href="docs/screenshots/phone/library.png"><img src="docs/screenshots/framed/library.png" width="32%" alt="Two-column Books view with personal shelves and series stacks"></a>
  <a href="docs/screenshots/phone/series.png"><img src="docs/screenshots/framed/series.png" width="32%" alt="An opened Sherlock Holmes series with books in reading order"></a>
  <a href="docs/screenshots/phone/playback.png"><img src="docs/screenshots/framed/playback.png" width="32%" alt="Playback with cover art and chapter controls"></a>
</p>
<p align="center">
  <a href="docs/screenshots/phone/listening-log.png"><img src="docs/screenshots/framed/listening-log.png" width="32%" alt="Listening log with playback events and chapter positions"></a>
  <a href="docs/screenshots/phone/bookmarks.png"><img src="docs/screenshots/framed/bookmarks.png" width="32%" alt="Named bookmarks with chapter and book positions"></a>
  <a href="docs/screenshots/phone/listening-statistics.png"><img src="docs/screenshots/framed/listening-statistics.png" width="32%" alt="Listening statistics, activity chart and records"></a>
</p>

### Make it yours

<p align="center">
  <a href="docs/screenshots/phone/appearance.png"><img src="docs/screenshots/framed/appearance.png" width="32%" alt="Choose Books, Grid or List view and two or three books per row"></a>
  <a href="docs/screenshots/phone/edit-book.png"><img src="docs/screenshots/framed/edit-book.png" width="32%" alt="Edit an audiobook title and cover without changing the audio file"></a>
  <a href="docs/screenshots/phone/playback-settings.png"><img src="docs/screenshots/framed/playback-settings.png" width="32%" alt="Choose playback skip duration, auto rewind and media-button actions"></a>
</p>

---

## What's new in v1.29

- A book-inspired library with cropped portrait covers, subtle page edges, and a choice of two or three books per row
- Personal shelves and series stacks, with editable ordering and drag-to-move between visible shelves; Current keeps your active books within reach
- A focused Appearance editor for Books, Grid and List layouts
- Edit titles and covers without rewriting audio files; edits survive rescans and travel with portable backups
- More reliable chapter-number corrections when navigating backward through irregular chapter markers
- Protection against accidental headphone or media-button resume after the sleep timer finishes
- Optionally remember volume boost across all books
- Updated playback libraries and refreshed phone and tablet screenshots

### Previous additions in v1.28

- Customize the now-playing toolbar with up to four shortcuts; other actions stay in the menu
- Toolbar choices are saved and included in settings backups
- Add a quick bookmark directly from the bookmarks screen, or use the separate named-bookmark button
- Completed books show a completion badge and finish date in the library
- Refreshed dark-mode phone and tablet screenshots

### Previous fixes in v1.27

- Later Chapter Fix corrections preserve earlier chapter names and respect restored names
- Long-press menus work in search results — thanks [@JamesDBartlett3](https://github.com/JamesDBartlett3)
- Android Auto forward/rewind keys keep the correct direction without changing headset preferences — thanks [@geofgowan](https://github.com/geofgowan) for reporting

---

## What's different from Voice

- Listening Log with clear chapter positions and timestamps
- Listening Statistics with trends, records, finished books, and relisten counts
- Character lists for each book
- Smarter sleep timer with interaction reset and chapter countdowns
- Customizable lock-screen progress, secondary text, and playback controls
- Flexible backup and restore — encrypted Android system backup plus automatic and manual saves to a folder you choose
- Editable chapter names and tools to fix out-of-sync numbering
- Hide and restore books from the library
- Resizable widget with configurable opacity and text scale
- Customizable media button actions (double/triple press)
- Personal shelves, ordered series stacks, and a book-inspired library view
- Portable title and cover edits that leave your audio files unchanged
- Firebase removed entirely — no analytics or background telemetry

---

## Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60">](https://f-droid.org/packages/com.github.mistermo_vibecode.voiceplus/)

Or grab and sideload the APK from the [Releases](https://github.com/Mistermo-vibecode/VoicePlus/releases) page.

---

## Build from source

Requires JDK 21 and Android SDK. See [development instructions](docs/development.md) for setup and tests, and `gradle/libs.versions.toml` for exact versions.

```bash
git clone https://github.com/Mistermo-vibecode/VoicePlus.git
cd VoicePlus
./gradlew :app:assembleLibreRelease
```

---

## License

GPL v3 — see [LICENSE.md](LICENSE.md).
Upstream Voice copyright Paul Woitaschek and contributors. VoicePlus modifications copyright Mistermo.
