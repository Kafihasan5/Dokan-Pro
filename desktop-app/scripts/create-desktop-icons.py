#!/usr/bin/env python3
"""Create the small Dokan Pro shopping-bag icon using only Python's standard library."""

import math
import os
import struct
import subprocess
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCES = os.path.join(ROOT, "build-resources")
ICONSET = os.path.join(RESOURCES, "DokanPro.iconset")
os.makedirs(ICONSET, exist_ok=True)


def png_bytes(size):
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            # Emerald gradient background.
            t = x / max(1, size - 1)
            bg = (int(16 + 21 * t), int(75 + 91 * t), int(63 + 39 * t))
            nx, ny = x / size, y / size
            # Rounded shopping bag body.
            bx, by, bw, bh, radius = 0.245, 0.34, 0.51, 0.47, 0.075
            cx = min(max(nx, bx + radius), bx + bw - radius)
            cy = min(max(ny, by + radius), by + bh - radius)
            bag = (nx - cx) ** 2 + (ny - cy) ** 2 <= radius ** 2
            # White handle arc, with the lower half tucked behind the bag.
            hx, hy, outer, inner = 0.5, 0.36, 0.147, 0.105
            dist = math.hypot(nx - hx, ny - hy)
            handle = inner <= dist <= outer and ny < hy + 0.005
            if handle or bag:
                color = (250, 255, 252)
                # Two mint details on the bag front.
                if bag and 0.37 <= ny <= 0.405 and 0.39 <= nx <= 0.61:
                    color = (22, 153, 112)
                if bag and 0.44 <= ny <= 0.465 and 0.42 <= nx <= 0.58:
                    color = (22, 153, 112)
            else:
                color = bg
            row.extend((*color, 255))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", size, size, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def write_png(size, filename):
    with open(filename, "wb") as output:
        output.write(png_bytes(size))


base_png = os.path.join(RESOURCES, "icon.png")
write_png(1024, base_png)

# macOS .iconset images; sips provides high-quality downsampling on the build Mac.
mac_sizes = [("icon_16x16.png", 16), ("icon_16x16@2x.png", 32),
             ("icon_32x32.png", 32), ("icon_32x32@2x.png", 64),
             ("icon_128x128.png", 128), ("icon_128x128@2x.png", 256),
             ("icon_256x256.png", 256), ("icon_256x256@2x.png", 512),
             ("icon_512x512.png", 512), ("icon_512x512@2x.png", 1024)]
for name, size in mac_sizes:
    subprocess.run(["sips", "-z", str(size), str(size), base_png, "--out", os.path.join(ICONSET, name)], check=True, stdout=subprocess.DEVNULL)
subprocess.run(["iconutil", "-c", "icns", ICONSET, "-o", os.path.join(RESOURCES, "icon.icns")], check=True)

# Windows ICO with PNG-compressed frames at common shell/icon sizes.
sizes = [16, 24, 32, 48, 64, 128, 256]
frames = [png_bytes(size) for size in sizes]
header = struct.pack("<HHH", 0, 1, len(frames))
offset = 6 + 16 * len(frames)
entries = []
for size, frame in zip(sizes, frames):
    dim = 0 if size == 256 else size
    entries.append(struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(frame), offset))
    offset += len(frame)
with open(os.path.join(RESOURCES, "icon.ico"), "wb") as output:
    output.write(header + b"".join(entries) + b"".join(frames))
