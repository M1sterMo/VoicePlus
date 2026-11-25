#!/bin/bash

# Produces legacy_cp1251_tags.mp3: an MP3 whose ID3v2.3 text frames declare ISO-8859-1 (encoding byte 0) but
# hold Windows-1251 bytes, the way taggers running under a Cyrillic locale wrote them. Media3 follows the
# declaration and decodes such frames as "Âàëèñ"; LegacyTagText puts them back together.

set -euo pipefail

ffmpeg \
  -y \
  -f lavfi -i anullsrc=r=44100:cl=mono \
  -codec:a libmp3lame -b:a 32k \
  -t 3 \
  -write_id3v1 0 \
  silence.mp3

python3 - <<'PY'
import struct

def frame(frame_id, text):
    data = b'\x00' + text.encode('cp1251')
    return frame_id + struct.pack('>I', len(data)) + b'\x00\x00' + data

body = b''.join([
    frame(b'TIT2', 'Валис'),
    frame(b'TPE1', 'Ф. Дик'),
    frame(b'TALB', 'Миры Филипа Дика'),
    frame(b'TCOM', 'Кирилл Петров'),
])
size = len(body)
header = b'ID3\x03\x00\x00' + bytes([(size >> 21) & 0x7F, (size >> 14) & 0x7F, (size >> 7) & 0x7F, size & 0x7F])

with open('silence.mp3', 'rb') as source:
    audio = source.read()
if audio[:3] == b'ID3':
    tag_size = (audio[6] << 21) | (audio[7] << 14) | (audio[8] << 7) | audio[9]
    audio = audio[10 + tag_size:]

with open('legacy_cp1251_tags.mp3', 'wb') as out:
    out.write(header + body + audio)
PY

rm silence.mp3
