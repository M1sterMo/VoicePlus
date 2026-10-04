# Book editing and portable artwork backups

## Implemented

Long-press a book and choose **Edit book**. Change its title, search for a cover,
choose an image, or restore an embedded cover. Cover search and cropping belong
to the same draft: **Save** applies the changes; **Cancel** leaves the saved book
alone. A draft survives activity recreation, but is not saved across process death.

Changes affect VoicePlus, not the source audiobook. Playback position and chapter
names are unchanged. Backups must be configured separately.

New external backups are ZIP archives containing the existing library snapshot
and the actual cover images. Restoring an archive can recover edited titles and
artwork even when the old app-private cover directory no longer exists. Each
restored book owns its own image file, so changing one cover cannot delete another.

Legacy JSON backups remain readable. They contain cover paths, not image bytes,
so they cannot recover artwork deleted with the old installation. Older app
versions do not understand the new ZIP backups. External archives are not
encrypted by VoicePlus; choose a trusted destination.

Archive import checks the snapshot checksum, artwork hashes, expected entries,
safe entry names and size limits. Unmatched images are removed after import;
existing saved backups are not overwritten if creating a new backup fails.

## Not implemented: writing into audio files

There is no automatic metadata-write toggle or **Save to audio files** action yet.
The original M4B, M4A and MP3 files remain untouched. The exploratory tag-library
probe is local only: no metadata-writing dependency was added to the app.

Before exposing file writes, verification must cover:

- Unchanged audio samples, chapter timestamps/names, unknown metadata and embedded
  chapter tracks after replacing a title and artwork.
- Multi-file books: update the book/album title without replacing individual track
  titles or changing file order.
- Durable original-file recovery after interruption, storage exhaustion, lost
  permissions, or failure midway through a multi-file book.
- Coordination with scanning and playback so neither reads a partially replaced
  file; F-Droid-compatible dependencies and release shrinking.

## Tests

Unit tests cover draft ownership, cancellation during commit, stale cover results,
failed saves, legacy backup compatibility, portable artwork after deleting its
source, damaged archive rejection, and both direct and re-keyed restores.
Instrumentation tests cover the cover-crop workflow and library recreation.
See [development instructions](development.md) to run them.
