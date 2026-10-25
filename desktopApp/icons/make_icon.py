"""Draws the Pool Print app icon and builds PoolPrint.icns (macOS only: needs iconutil).

Run from this folder: python3 make_icon.py
"""
import os
import shutil
import subprocess
import tempfile

from PIL import Image, ImageDraw

S = 1024
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# macOS icon grid: the rounded square fills 824 of 1024 px.
m = 100
d.rounded_rectangle((m, m, S - m, S - m), radius=185, fill=(24, 107, 160))
d.rounded_rectangle((m, m, S - m, S // 2), radius=185, fill=(34, 129, 186))
d.rectangle((m, 300, S - m, S // 2), fill=(34, 129, 186))

# paper going in (top), printer body, paper coming out (bottom)
d.rounded_rectangle((330, 190, 694, 420), radius=18, fill=(255, 255, 255))
d.rounded_rectangle((200, 380, 824, 670), radius=60, fill=(236, 240, 243))
d.rounded_rectangle((200, 560, 824, 670), radius=40, fill=(205, 212, 218))
d.rounded_rectangle((300, 600, 724, 850), radius=18, fill=(255, 255, 255), outline=(190, 198, 205), width=6)
for y in (660, 715, 770):
    d.rounded_rectangle((350, y, 674, y + 22), radius=11, fill=(160, 170, 178))
d.ellipse((730, 440, 780, 490), fill=(70, 200, 120))

here = os.path.dirname(os.path.abspath(__file__))
img.save(os.path.join(here, "PoolPrint.png"))

with tempfile.TemporaryDirectory() as tmp:
    iconset = os.path.join(tmp, "PoolPrint.iconset")
    os.mkdir(iconset)
    for size in (16, 32, 128, 256, 512):
        img.resize((size, size), Image.LANCZOS).save(os.path.join(iconset, f"icon_{size}x{size}.png"))
        img.resize((size * 2, size * 2), Image.LANCZOS).save(os.path.join(iconset, f"icon_{size}x{size}@2x.png"))
    subprocess.run(["iconutil", "-c", "icns", iconset, "-o", os.path.join(here, "PoolPrint.icns")], check=True)
