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

## Features

- **Library**: personal shelves, series stacks with shared character lists, drag to arrange, and sorting by title, author, date added or length
- **Up next**: queue books to play one after another
- **About this book**: synopsis, narrator and details for every book, and your own 1–5 star ratings
- **Listening history**: a log with chapter positions, plus statistics with finish dates and records
- **Sleep timer**: chapter countdowns, and a reset when you interact
- **Your edits**: bookmarks, chapter names, titles and covers, all without changing your audio files
- **Controls your way**: player toolbar, lock screen, media buttons, widget, theme and colors
- **Your folders**: rename or move a book's folder and it keeps its progress, bookmarks and history
- **Backups**: Android system backup, plus automatic and manual saves to a folder you choose
- **Private**: no accounts, ads, analytics or telemetry

See the [changelog](CHANGELOG.md) for what changed in each release.

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
