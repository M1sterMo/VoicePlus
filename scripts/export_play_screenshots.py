"""Export current app captures in Google Play's screenshot dimensions."""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/screenshots"
OUTPUT = ROOT / "play-store-assets"

PHONE = (
    ("library.png", "1_library.png"),
    ("playback.png", "2_playback.png"),
    ("bookmarks.png", "3_bookmarks.png"),
    ("sleep-timer.png", "4_sleep_timer.png"),
    ("listening-statistics.png", "5_listening_stats.png"),
    ("playback-toolbar.png", "6_playback_toolbar.png"),
    ("characters.png", "7_characters.png"),
    ("listening-log.png", "8_listening_log.png"),
)


def save_rgb(image: Image.Image, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(destination, optimize=True)


def export_phone() -> None:
    for source_name, output_name in PHONE:
        # F-Droid's historical slots can change scenes; use named masters here.
        with Image.open(ROOT / "docs/screenshots/phone" / source_name) as capture:
            if capture.size != (1080, 2400):
                raise ValueError(f"Unexpected phone size: {source_name}: {capture.size}")
            # Side padding preserves the app UI and makes the result exactly 9:16.
            background = capture.convert("RGB").getpixel((0, 1200))
            canvas = Image.new("RGB", (1350, 2400), background)
            canvas.paste(capture.convert("RGB"), (135, 0))
            save_rgb(canvas, OUTPUT / "phoneScreenshots" / output_name)


def export_tablets() -> None:
    scenes = (("library", "library"), ("series", "series"), ("playback", "playback"),
              ("listening-log", "listening_log"), ("listening-statistics", "listening_stats"))
    for index in range(1, 6):
        name = f"{index}_en-US.png"
        source_scene, scene = scenes[index - 1]
        with Image.open(SOURCE / "tablet-7" / f"{source_scene}.png") as capture:
            if capture.size != (800, 1280):
                raise ValueError(f"Unexpected 7-inch size: {name}: {capture.size}")
            resized = capture.convert("RGB").resize((1080, 1728), Image.Resampling.LANCZOS)
            canvas = Image.new("RGB", (1080, 1920), resized.getpixel((0, 864)))
            canvas.paste(resized, (0, 96))
            save_rgb(canvas, OUTPUT / "sevenInchScreenshots" / f"7in_{index}_{scene}.png")

        with Image.open(SOURCE / "tablet-10" / f"{source_scene}.png") as capture:
            if capture.size != (1600, 2560):
                raise ValueError(f"Unexpected 10-inch size: {name}: {capture.size}")
            resized = capture.convert("RGB").resize((1440, 2304), Image.Resampling.LANCZOS)
            canvas = Image.new("RGB", (1440, 2560), resized.getpixel((0, 1152)))
            canvas.paste(resized, (0, 128))
            save_rgb(canvas, OUTPUT / "tenInchScreenshots" / f"10in_{index}_{scene}.png")

        (OUTPUT / "sevenInchScreenshots" / name).unlink(missing_ok=True)
        (OUTPUT / "tenInchScreenshots" / name).unlink(missing_ok=True)

    # Retire the previous curated names when a slot changes scenes.
    for folder, prefix in [("sevenInchScreenshots", "7in"), ("tenInchScreenshots", "10in")]:
        for old in ["2_playback", "3_listening_log", "4_listening_stats", "5_bookmarks"]:
            (OUTPUT / folder / f"{prefix}_{old}.png").unlink(missing_ok=True)


if __name__ == "__main__":
    export_phone()
    export_tablets()
