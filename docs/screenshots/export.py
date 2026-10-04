"""Export captioned phone/tablet frames and populate existing F-Droid slots.

Requires agent-browser and its installed Chromium. Run from any directory.
Only documentation and promotional images are written; no app/device data changes.
"""
import base64
import json
from pathlib import Path
import shutil
import subprocess
from PIL import Image, PngImagePlugin
from validate import FRAME_SIZE, FRAMED_DIRS, SETS, pixels, validate

root = Path(__file__).resolve().parent
repo = root.parents[1]
session = subprocess.check_output(
    ['agent-browser', 'session', 'id', '--scope', 'worktree', '--prefix', 'screenshot-export'],
    cwd=repo, text=True,
).strip()
browser = ['agent-browser', '--session', session]

def run(*args):
    return subprocess.check_output(browser + list(args), text=True)

def evaluate(script):
    return run('eval', '-b', base64.b64encode(f'(() => {{ {script} }})()'.encode()).decode())

try:
    run('--allow-file-access', 'open', (root / 'preview.html').as_uri())
    run('set', 'viewport', '1120', '900', '3')
    run('wait', '--fn', 'Array.from(document.images).every(i => i.complete && i.naturalWidth > 0)')
    evaluate('''
      window.exportFigures = Array.from(document.querySelectorAll('main figure'));
      document.querySelector('main').style.display = 'none';
      const box = document.createElement('div'); box.id = 'export';
      box.style.cssText = 'width:360px;height:720px;display:block;';
      document.body.append(box);
    ''')
    for source, _, size, slots in SETS:
        output = root / FRAMED_DIRS[source]
        output.mkdir(exist_ok=True)
        names = [*slots.values(), 'playback-toolbar'] if source == 'phone' else slots.values()
        for name in names:
            path = f'{source}/{name}.png'
            evaluate(f"""
              const box = document.querySelector('#export');
              box.className = {'"gallery"' if source == 'phone' else '"tablets"'};
              box.replaceChildren(window.exportFigures.find(f => f.querySelector('img').getAttribute('src') === {json.dumps(path)}).cloneNode(true));
            """)
            run('wait', '--fn', "document.querySelector('#export img').complete && document.querySelector('#export img').naturalWidth > 0")
            destination = output / (name + '.png')
            run('screenshot', '#export', str(destination))
            # Preserve a source fingerprint so CI also detects stale frames.
            info = PngImagePlugin.PngInfo()
            info.add_text('source_sha256', pixels(root / path, size))
            with Image.open(destination) as capture:
                if capture.size != FRAME_SIZE:
                    raise ValueError(f'Unexpected exported dimensions: {capture.size}')
                capture.convert('RGB').save(destination, pnginfo=info, optimize=True)
            print('Exported', path, flush=True)
finally:
    run('close')

metadata = repo / 'fastlane/metadata/android/en-US/images'
for source_dir, target_dir, size, slots in SETS:
    destination = metadata / target_dir
    destination.mkdir(exist_ok=True)
    for filename, name in slots.items():
        shutil.copyfile(root / FRAMED_DIRS[source_dir] / (name + '.png'), destination / filename)
validate()
print('Updated 12 phone slots and 10 tablet slots with captioned frames.')
