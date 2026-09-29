# Screenshots

Real v1.29 app captures, not mock UI. The phone gallery leads with two-column
Books view (four visible entries), personal shelves and opened series stacks.
README and F-Droid use matching captioned device frames with a matte charcoal backdrop.
Untouched app captures are retained separately as the source masters.

## Demo library

The emulator contains twelve classic books, manually arranged into two shelves
and three series: Sherlock Holmes, Alice’s Adventures and The Oz Books. Display
titles can be shortened; most covers retain their edition titles. Alice uses
the edition’s original `images/cover.source.jpg` artwork, without its title strip,
so both portrait Books view and square playback cropping look clean. Bookmarks,
character notes, sessions and statistics are fictional. Audio is a local test
fixture, not a recording of these editions. No phone database or history is used.

Artwork comes from [Standard Ebooks](https://standardebooks.org/about), which
dedicates its edition work to the public domain:

- Arthur Conan Doyle: [Adventures](https://standardebooks.org/ebooks/arthur-conan-doyle/the-adventures-of-sherlock-holmes),
  [Memoirs](https://standardebooks.org/ebooks/arthur-conan-doyle/the-memoirs-of-sherlock-holmes),
  [The Hound of the Baskervilles](https://standardebooks.org/ebooks/arthur-conan-doyle/the-hound-of-the-baskervilles),
  [Return](https://standardebooks.org/ebooks/arthur-conan-doyle/the-return-of-sherlock-holmes)
- Lewis Carroll: [Alice’s Adventures in Wonderland](https://standardebooks.org/ebooks/lewis-carroll/alices-adventures-in-wonderland/john-tenniel),
  [Through the Looking-Glass](https://standardebooks.org/ebooks/lewis-carroll/through-the-looking-glass/john-tenniel)
- L. Frank Baum: [The Wonderful Wizard of Oz](https://standardebooks.org/ebooks/l-frank-baum/the-wonderful-wizard-of-oz),
  [The Marvelous Land of Oz](https://standardebooks.org/ebooks/l-frank-baum/the-marvelous-land-of-oz)
- [The Time Machine](https://standardebooks.org/ebooks/h-g-wells/the-time-machine)
- [Treasure Island](https://standardebooks.org/ebooks/robert-louis-stevenson/treasure-island)
- [The Secret Garden](https://standardebooks.org/ebooks/frances-hodgson-burnett/the-secret-garden)
- [Pride and Prejudice](https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice)

## Capture and export

Use current `libreDebug` on a test emulator. Back up its database and DataStores
before seeding. Never seed the physical phone. Use default font scale, 09:41 and
a full demo battery; wait for covers and transitions to settle. Render the app
at each display size instead of resizing phone captures.

| Set | Pixels | Density | Theme |
| --- | --- | --- | --- |
| Phone | 1080 × 2400 | 420 dpi | Dark, plus light library |
| 7-inch class | 800 × 1280 | 200 dpi | Dark |
| 10-inch class | 1600 × 2560 | 320 dpi | Light |

These are display/density overrides on `VoicePlusTest`, not separate hardware
profiles. Thirteen phone masters live in `phone/`; five current captures each
live in `tablet-7/` and `tablet-10/`. Superseded tablet bookmark masters were
removed (recoverable in Git). README uses nine framed images; `preview.html` shows
all phone and tablet frames. `fdroid-preview.html` shows the exact 22 exported
PNGs in the current F-Droid website order.

```sh
python3 docs/screenshots/export.py
python3 -m unittest discover -s docs/screenshots -p 'test_*.py'
python3 docs/screenshots/validate.py
```

The exporter requires `agent-browser` and Chromium; validation requires Pillow
(CI pins its version). An isolated browser renders frames at 3× (1080 × 2160),
then copies those frames to the existing F-Droid slots. Phone frames live in
`framed/`; tablet frames in `framed-tablet-7/` and `framed-tablet-10/`. Headlines,
device borders and shadows are rendered around real captures—not generated app
UI. The README uses the same phone frames. No push, tag or publication occurs.

## F-Droid slots: no duplicates

The v1.28 exporter repeated three captures under legacy and newer filenames,
causing duplicate library, playback and toolbar images. F-Droid also has a
documented [deleted-image retention issue](https://gitlab.com/fdroid/fdroidserver/-/issues/490),
so deletion or renaming alone is not a safe cleanup strategy. Overwrite every
published slot without adding new metadata filenames.

| Phone filename | New scene |
| --- | --- |
| `1_en-US.png` | Dark Books library |
| `1_library.png` | Light Books library |
| `2_en-US.png` | Opened series |
| `2_playback.png` | Playback |
| `3_en-US.png` | Appearance |
| `3_sleep_timer.png` | Sleep timer |
| `4_character_list.png` | Character notes |
| `4_en-US.png` | Bookmarks |
| `5_edit_book.png` | Title and cover editor |
| `6_settings.png` | Playback settings |
| `7_listening_log.png` | Listening log |
| `8_listening_stats.png` | Listening statistics |

Both tablet directories retain `1_en-US.png` through `5_en-US.png`, now showing
library, series, playback, listening log and statistics in order. Filenames are
stable slot identifiers, not necessarily their original scene.

`validate.py` is the single mapping used by the exporter. It rejects missing or
unexpected slots, wrong dimensions, mismatches with exported frames, repeated
source scenes and identical decoded pixels, even when PNG encoding differs.
It checks both raw captures and final frames: adding different captions cannot
disguise a duplicate capture. A source-pixel fingerprint stored in each frame
also detects edits to masters that require re-exporting. F-Droid may strip PNG
metadata when publishing; fingerprints are for local/CI validation, not the
public repository. Visually review near-duplicates and framing too.

After F-Droid refreshes, verify the [live listing](https://f-droid.org/en/packages/com.github.mistermo_vibecode.voiceplus/):
12 distinct phone screenshots and five current captures for each tablet size.
Local checks do not prove the public repository refreshed. If extra retired
assets persist, request repository-side cleanup; do not add new filenames.

## Local session recovery

The demo is retained for future captures. The ignored `artifacts/v1.29-screenshots/`
directory holds `emulator-before.tar`, copied/migrated data, fixture generator,
source artwork and the portable demo archive. No secrets or phone data belong
in committed screenshots. Stop the app before restoring original data, and
preserve the screenshot demo separately first.
