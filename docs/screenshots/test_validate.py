import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image, PngImagePlugin
from validate import PHONE_SLOTS, SETS, pixels, validate, validate_set


class ScreenshotValidationTest(unittest.TestCase):
    def test_every_retained_slot_has_a_distinct_scene(self):
        self.assertEqual(len(PHONE_SLOTS), 12)
        self.assertEqual(sum(len(slots) for _, _, _, slots in SETS), 22)
        for _, _, _, slots in SETS:
            self.assertEqual(len(slots), len(set(slots.values())))

    def test_duplicates_detected_even_with_different_png_encoding(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            image = Image.new('RGB', (8, 12), 'navy')
            image.save(root / 'a.png', compress_level=0)
            info = PngImagePlugin.PngInfo()
            info.add_text('capture', 'different metadata')
            image.save(root / 'b.png', compress_level=9, pnginfo=info)
            with self.assertRaisesRegex(ValueError, 'duplicate screenshots'):
                validate_set(root, {'a.png': 'a', 'b.png': 'b'}, (8, 12))

    def test_distinct_images_pass_and_wrong_dimensions_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            Image.new('RGB', (8, 12), 'navy').save(root / 'a.png')
            Image.new('RGB', (8, 12), 'white').save(root / 'b.png')
            slots = {'a.png': 'a', 'b.png': 'b'}
            validate_set(root, slots, (8, 12))
            with self.assertRaisesRegex(ValueError, 'expected'):
                validate_set(root, slots, (12, 8))

    def test_missing_and_unmapped_slots_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            Image.new('RGB', (8, 12)).save(root / 'a.png')
            with self.assertRaisesRegex(ValueError, 'missing'):
                validate_set(root, {'a.png': 'a', 'b.png': 'b'}, (8, 12))
            with self.assertRaisesRegex(ValueError, 'unexpected'):
                validate_set(root, {}, (8, 12))

    def test_metadata_must_match_frame_and_frame_must_match_master(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            masters, metadata = root / 'masters', root / 'metadata'
            (masters / 'phone').mkdir(parents=True)
            (masters / 'framed').mkdir()
            (metadata / 'phoneScreenshots').mkdir(parents=True)
            for directory, name in [(masters / 'phone', 'library'), (metadata / 'phoneScreenshots', '1_en-US')]:
                Image.new('RGB', (8, 12), 'navy').save(directory / f'{name}.png')
            info = PngImagePlugin.PngInfo()
            info.add_text('source_sha256', pixels(masters / 'phone/library.png', (8, 12)))
            Image.new('RGB', (8, 12), 'navy').save(masters / 'framed/library.png', pnginfo=info)
            fixture = [('phone', 'phoneScreenshots', (8, 12), {'1_en-US.png': 'library'})]
            with patch('validate.SETS', fixture), patch('validate.FRAME_SIZE', (8, 12)):
                validate(masters, metadata)
                Image.new('RGB', (8, 12), 'white').save(metadata / 'phoneScreenshots/1_en-US.png')
                with self.assertRaisesRegex(ValueError, 'does not match frame'):
                    validate(masters, metadata)
                Image.new('RGB', (8, 12), 'navy').save(metadata / 'phoneScreenshots/1_en-US.png')
                Image.new('RGB', (8, 12), 'white').save(masters / 'phone/library.png')
                with self.assertRaisesRegex(ValueError, 'frame is stale'):
                    validate(masters, metadata)

    def test_duplicate_captures_cannot_be_disguised_by_different_frames(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            masters, metadata = root / 'masters', root / 'metadata'
            for folder in [masters / 'phone', masters / 'framed', metadata / 'phoneScreenshots']:
                folder.mkdir(parents=True)
            for scene, color in [('a', 'navy'), ('b', 'white')]:
                Image.new('RGB', (8, 12), 'navy').save(masters / 'phone' / f'{scene}.png')
                info = PngImagePlugin.PngInfo()
                info.add_text('source_sha256', pixels(masters / 'phone' / f'{scene}.png', (8, 12)))
                for folder in [masters / 'framed', metadata / 'phoneScreenshots']:
                    Image.new('RGB', (8, 12), color).save(folder / f'{scene}.png', pnginfo=info)
            fixture = [('phone', 'phoneScreenshots', (8, 12), {'a.png': 'a', 'b.png': 'b'})]
            with patch('validate.SETS', fixture), patch('validate.FRAME_SIZE', (8, 12)):
                with self.assertRaisesRegex(ValueError, 'duplicate captures'):
                    validate(masters, metadata)

    def test_repeating_a_source_scene_is_rejected(self):
        fixture = [('phone', 'phoneScreenshots', (8, 12), {'a.png': 'library', 'b.png': 'library'})]
        with patch('validate.SETS', fixture):
            with self.assertRaisesRegex(ValueError, 'repeated source scene'):
                validate()


if __name__ == '__main__':
    unittest.main()
