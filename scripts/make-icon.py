#!/usr/bin/env python3
"""Build the 128x128 Escape icon from the vanilla iron axe item texture.

Usage:
    scripts/make-icon.py [path/to/minecraft-client.jar]

The default path is the Loom cache used by `./build.sh`. The icon is composed
of the actual Minecraft iron axe sprite (scaled with nearest-neighbour) over a
dark red horror background, so the logo is a real Minecraft item.
"""
import random
import struct
import sys
import zipfile
import zlib
from pathlib import Path

WIDTH = 128
HEIGHT = 128
SPRITE = "assets/minecraft/textures/item/iron_axe.png"


def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def read_png(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos = 8
    width = height = depth = color = None
    compressed = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", payload)
            if depth != 8 or color != 6 or interlace != 0:
                raise ValueError("expected an 8-bit RGBA, non-interlaced PNG")
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
    raw = zlib.decompress(bytes(compressed))
    stride = width * 4
    pixels = []
    previous = bytearray(stride)
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        offset += 1
        line = bytearray(raw[offset:offset + stride])
        offset += stride
        if filter_type == 0:
            pass
        elif filter_type == 1:
            for i in range(4, stride):
                line[i] = (line[i] + line[i - 4]) & 0xFF
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif filter_type == 3:
            for i in range(stride):
                left = line[i - 4] if i >= 4 else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif filter_type == 4:
            for i in range(stride):
                left = line[i - 4] if i >= 4 else 0
                up = previous[i]
                upper_left = previous[i - 4] if i >= 4 else 0
                line[i] = (line[i] + paeth(left, up, upper_left)) & 0xFF
        else:
            raise ValueError(f"unsupported PNG filter {filter_type}")
        previous = line
        row = [tuple(line[i:i + 4]) for i in range(0, stride, 4)]
        pixels.append(row)
    return width, height, pixels


def write_png(path, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0)))
    png.extend(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
    png.extend(chunk(b"IEND", b""))
    path.write_bytes(png)


def main():
    jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else (
        Path.home() / ".gradle/caches/fabric-loom/26.2/minecraft-client.jar")
    with zipfile.ZipFile(jar_path) as jar:
        sprite = read_png(jar.read(SPRITE))
    sw, sh, source = sprite

    rng = random.Random(109)
    canvas = [[(0, 0, 0, 255) for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for y in range(HEIGHT):
        for x in range(WIDTH):
            dx = (x - WIDTH / 2) / (WIDTH / 2)
            dy = (y - HEIGHT / 2) / (HEIGHT / 2)
            vignette = max(0.0, 1.0 - (dx * dx + dy * dy) * 0.85)
            value = int(18 + 42 * vignette) + rng.randrange(0, 10)
            canvas[y][x] = (min(255, value + 18), max(0, value // 4), max(0, value // 5), 255)
    for y in range(0, HEIGHT, 3):
        for x in range(WIDTH):
            r, g, b, a = canvas[y][x]
            canvas[y][x] = (min(255, r + 14), g, b, a)

    scale = 6
    draw_w = sw * scale
    draw_h = sh * scale
    start_x = (WIDTH - draw_w) // 2
    start_y = (HEIGHT - draw_h) // 2

    for y in range(draw_h):
        for x in range(draw_w):
            r, g, b, a = source[y // scale][x // scale]
            if a == 0:
                continue
            px, py = start_x + x, start_y + y
            if 0 <= px < WIDTH and 0 <= py < HEIGHT:
                canvas[py][px] = (r, g, b, 255)

    for _ in range(36):
        x = rng.randrange(WIDTH)
        y = rng.randrange(HEIGHT)
        if source[min(sh - 1, max(0, (y - start_y) // scale))][min(sw - 1, max(0, (x - start_x) // scale))][3]:
            continue
        if 0 <= x < WIDTH and 0 <= y < HEIGHT:
            canvas[y][x] = (200, 20, 20, 255)

    target = Path(__file__).resolve().parent.parent / "src/main/resources/assets/escape/icon.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_png(target, canvas)
    print(target)


if __name__ == "__main__":
    main()
