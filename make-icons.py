#!/usr/bin/env python3
"""Generate the PWA icons without an image library.

Solid dark plate with a lighter centred square; the maskable variant keeps
the square inside the 80% safe zone.
"""
import struct
import zlib

BG = (0x10, 0x10, 0x14)
FG = (0x7e, 0xe7, 0x87)


def png(path, size, mark_ratio):
    half = int(size * mark_ratio / 2)
    lo, hi = size // 2 - half, size // 2 + half
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter type: none
        inside_y = lo <= y < hi
        for x in range(size):
            raw += bytes(FG if inside_y and lo <= x < hi else BG)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)
    print(path, size, len(blob))


png("icon-192.png", 192, 0.55)
png("icon-512.png", 512, 0.55)
png("icon-maskable-512.png", 512, 0.38)
