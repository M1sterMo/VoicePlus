"""Validate screenshot dimensions, retained F-Droid slots, and decoded pixels."""
import hashlib
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
METADATA = ROOT.parents[1] / 'fastlane/metadata/android/en-US/images'
FRAME_SIZE = (1080, 2160)
FRAMED_DIRS = {'phone': 'framed', 'tablet-7': 'framed-tablet-7', 'tablet-10': 'framed-tablet-10'}

# Treat historical filenames as stable slots: never add a second copy of a scene.
PHONE_SLOTS = {
    '1_en-US.png': 'library',
    '1_library.png': 'library-light',
    '2_en-US.png': 'series',
    '2_playback.png': 'playback',
    '3_en-US.png': 'appearance',
    '3_sleep_timer.png': 'sleep-timer',
    '4_character_list.png': 'characters',
    '4_en-US.png': 'bookmarks',
    '5_edit_book.png': 'edit-book',
    '6_settings.png': 'playback-settings',
    '7_listening_log.png': 'listening-log',
    '8_listening_stats.png': 'listening-statistics',
}
TABLET_SCENES = ['library', 'series', 'playback', 'listening-log', 'listening-statistics']
SETS = [('phone', 'phoneScreenshots', (1080, 2400), PHONE_SLOTS)] + [
    (source, target, size, {f'{n}_en-US.png': scene for n, scene in enumerate(TABLET_SCENES, 1)})
    for source, target, size in [
        ('tablet-7', 'sevenInchScreenshots', (800, 1280)),
        ('tablet-10', 'tenInchScreenshots', (1600, 2560)),
    ]
]


def pixels(path, size):
    with Image.open(path) as capture:
        if capture.size != size:
            raise ValueError(f'{path}: expected {size}, got {capture.size}')
        return hashlib.sha256(capture.convert('RGB').tobytes()).hexdigest()


def validate_set(directory, slots, size):
    actual = {p.name for p in directory.iterdir() if p.suffix.lower() in {'.png', '.jpg', '.jpeg'}}
    if actual != set(slots):
        raise ValueError(f'{directory}: missing {set(slots) - actual}; unexpected {actual - set(slots)}')
    seen = {}
    for filename in slots:
        digest = pixels(directory / filename, size)
        if digest in seen:
            raise ValueError(f'{directory}: duplicate screenshots {seen[digest]} and {filename}')
        seen[digest] = filename


def validate(masters=ROOT, metadata=METADATA):
    for source, target, size, slots in SETS:
        if len(set(slots.values())) != len(slots):
            raise ValueError(f'{target}: repeated source scene')
        validate_set(metadata / target, slots, FRAME_SIZE)
        seen = {}
        for filename, scene in slots.items():
            digest = pixels(masters / source / f'{scene}.png', size)
            if digest in seen:
                raise ValueError(f'{source}: duplicate captures {seen[digest]} and {scene}')
            seen[digest] = scene
            framed = masters / FRAMED_DIRS[source] / f'{scene}.png'
            with Image.open(framed) as capture:
                if capture.info.get('source_sha256') != digest:
                    raise ValueError(f'{framed}: frame is stale; re-export master {scene}')
            if pixels(framed, FRAME_SIZE) != pixels(metadata / target / filename, FRAME_SIZE):
                raise ValueError(f'{target}/{filename}: does not match frame {scene}')
        print(f'{target}: {len(slots)} unique frames; dimensions and source captures match.')


if __name__ == '__main__':
    validate()
