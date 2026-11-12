#!/usr/bin/env python3
"""Builds the Amazon Appstore graphics from branding/ and docs/screenshots/ (needs rsvg-convert).

Usage: python3 tools/store_assets.py   → store/amazon/
"""
import os, re, shutil, subprocess, tempfile

ROOT = os.path.join(os.path.dirname(__file__), "..")
B, OUT = f"{ROOT}/branding", f"{ROOT}/store/amazon"
PLUM = "#3B1F3A"
FULL_TILE = "M0 0H256V256H0Z"  # store icons must not be transparent → square instead of rounded tile

def render(svg, name, w, h):
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg)
    subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h), "-b", PLUM, f.name, "-o", f"{OUT}/{name}"], check=True)
    os.unlink(f.name)

def read(name):
    return open(f"{B}/{name}").read()

def inner(svg):
    return re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)

os.makedirs(f"{OUT}/screenshots", exist_ok=True)
square = lambda svg: re.sub(r'd="M56 0H200[^"]*"', f'd="{FULL_TILE}"', svg, count=1)
render(square(read("icon.svg")), "icon-512.png", 512, 512)
render(square(read("icon-small.svg")), "icon-114.png", 114, 114)
render(read("banner.svg"), "firetv-icon-1280x720.png", 1280, 720)
background = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 1080"><rect width="1920" height="1080" fill="{PLUM}"/>'
              f'<g transform="translate(1080 300) scale(3.6) translate(-40 -64)" opacity="0.9">{inner(read("mark.svg"))}</g></svg>')
render(background, "background-1920x1080.png", 1920, 1080)
for i, name in enumerate(["live-guide", "player", "movies", "series-detail", "setup", "splash"], 1):
    shutil.copy(f"{ROOT}/docs/screenshots/{name}.png", f"{OUT}/screenshots/{i:02d}-{name}.png")
print("ok", OUT)
