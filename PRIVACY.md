# Privacy Policy — VoicePlus

**Effective date:** 2026-10-04

VoicePlus is an open-source Android audiobook player maintained by Mistermo. This policy describes what data the app handles, when data can leave your device, and what permissions the app uses and why.

---

## What data we collect

VoicePlus is designed as a local-first app that plays audiobooks offline. It does not have an account system or app-operated cloud sync, and it does not send usage data to VoicePlus servers.

The app stores these records on your device:

- Your audiobook library (titles, authors, chapter positions, cover art)
- Listening history (when you started and stopped listening, and for how long — stored locally for the in-app Statistics screen)
- Character lists you create for individual books
- Bookmarks
- App preferences: playback speed, skip distance, sleep timer settings, media button mappings, library layout

By default, this data is stored on your device in the local database (`autoBookDB`) and Android DataStore. If Android system backup is enabled, an encrypted subset may also be stored by your configured system backup provider as described below.

## Android system backup

VoicePlus allows Android system-managed backup to restore app data after reinstall. Eligible data is the Room database and ordinary DataStore settings, including playback progress, bookmarks, listening history and statistics, character lists, chapter-name corrections, hidden-book state, and normal settings.

Cloud backup excludes audiobook files, cover images, selected audiobook-folder URIs/SAF permissions, internal snapshot-ring files, backup-folder state, and open listening-session checkpoints. Device-to-device transfer may include cached cover images, but never audiobook files or folder permissions.

Cloud backup is permitted only when Android reports client-side encryption capability. Android 12+ device-to-device transfer is also permitted.

Android controls scheduling, retention, and provider, so backup is not immediate or guaranteed. After reinstall, you must grant audiobook-folder access again. VoicePlus external folder backup remains the portable/manual recovery option.

External folder backups include edited book titles and cover images, alongside library data and settings. These ZIP archives are not encrypted by VoicePlus; store them in a trusted location. Older JSON-only backups remain readable but do not contain cover images. Editing a book in VoicePlus does not modify the original audiobook files.

---

## Other data that leaves your device

Apart from the encrypted Android system backup described above, the only data VoicePlus itself sends is an explicitly initiated DuckDuckGo book title/author cover query.

**Details:**

- This only happens when you open the "Search for cover online" screen and initiate a search. It never runs in the background.
- The search query is the book's title and author as you have them stored in the app — for example, `"Harry Potter by J.K. Rowling audiobook cover"`.
- The query is sent to DuckDuckGo's image search API (`https://duckduckgo.com/`).
- When you tap a result, the selected image is downloaded directly from the image host serving that result.
- DuckDuckGo receives the query and your IP address and handles them under its privacy policy at https://duckduckgo.com/privacy. Opening an image result also makes a request to the site hosting that image.

VoicePlus does not attach device identifiers, account tokens, or persistent identifiers to these requests. DuckDuckGo and the selected image host can see your IP address through the HTTPS connection.

**VoicePlus sends no other data itself.** It still has no telemetry, no crash reporting, no analytics, and no advertising network.

---

## What stays on your device (examples)

- Your entire audiobook library and file paths
- Listening session log (for the Statistics screen)
- Character lists
- Bookmarks
- All playback preferences and settings
- Cover art images you select or set manually

Except for the Android system backup described above or a backup you manually export, this data remains on your device.

---

## Permissions and why

| Permission | Why the app needs it |
|------------|---------------------|
| `INTERNET` | Required to fetch cover art images from DuckDuckGo when you use the online cover search feature. |
| `ACCESS_NETWORK_STATE` | Lets the app check whether a network connection is available before attempting a cover art search, so it can show you a useful error instead of silently failing. |
| `FOREGROUND_SERVICE` | Required by Android to keep the playback service running when the app is in the background. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Declares the type of the foreground service to Android (audio playback), which is required by Android 14+. |
| `WAKE_LOCK` | Prevents the CPU from sleeping while audio is actively playing, which would cause playback to stop. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Allows you to exempt VoicePlus from Android's battery optimization so playback is not interrupted during long listening sessions. You are prompted to grant this; it is not assumed. |

**Storage access** is handled through Android's Storage Access Framework (SAF). When you add an audiobook folder, Android's system file picker grants the app permission to read that specific folder. The app does not request broad storage permissions (`READ_EXTERNAL_STORAGE`) and cannot access files outside the folders you explicitly share with it.

**Advertising ID (`AD_ID`):** This permission is explicitly removed in the app's manifest. The app has no advertising network and will never request this permission.

**Location:** The app does not use, request, or have any need for your location. No location permission of any kind is declared.

---

## Third-party services

For optional cover art search, VoicePlus communicates with **DuckDuckGo** and, when you choose a result, the site hosting that image. Android system backup may transfer the eligible app data described above to your configured backup provider.

There are no analytics SDKs, no crash-reporting services, no advertising networks, and no Google/Firebase services included in this app.

---

## No analytics, no tracking, no ads

VoicePlus contains no analytics code, no tracking SDKs, and no advertising networks. It does not transmit usage statistics, error reports, or any behavioral data to any server. The listening log and statistics features are stored locally and are not sent by VoicePlus; when Android system backup is enabled, eligible database records may be included as described above.

---

## Children's privacy

VoicePlus does not knowingly collect personal information from anyone, including children under the age of 13. The app has no account system, no registration, and no user-facing data collection. If you are a parent or guardian and have concerns, you can contact us using the details below.

---

## Retention and deletion

VoicePlus keeps its local library records, listening history, notes, bookmarks, and settings until you delete them in the app or remove the app's data. Uninstalling the app removes local data, subject to any Android system backup or external backup you have enabled. You control external backup copies in the folder you selected. Android and its configured backup provider control the retention and deletion of system backup copies; VoicePlus does not operate that service.

---

## Changes to this policy

If this policy changes in a future version of VoicePlus, the updated policy will be committed to the public repository alongside the source code that motivated the change. The effective date at the top of this document will be updated.

You can always view the current policy at:
https://github.com/mistermo-vibecode/VoicePlus/blob/main/PRIVACY.md

---

## Contact

If you have questions about this privacy policy, please open an issue on the public repository:
https://github.com/mistermo-vibecode/VoicePlus/issues

---

*VoicePlus is a fork of the [Voice Audiobook Player](https://github.com/PaulWoitaschek/Voice) by Paul Woitaschek, licensed under the GNU General Public License v3.0.*
