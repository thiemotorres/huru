#!/usr/bin/env python3
"""Generates fictional artwork (posters and a video still) for the screenshot mockups.

Usage: python3 tools/mock_assets.py   (needs rsvg-convert)
Output: app/src/test/resources/mock/*.png – everything is made up, no real channels or titles.
"""
import os, random, subprocess, tempfile

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "test", "resources", "mock")
PALETTES = [  # sky top, sky bottom, accent, far, near
    ("#2B1B3D", "#FF7A59", "#FFC857", "#5B2A4A", "#2A1530"),
    ("#0F2A44", "#3E7CB1", "#F2F5EA", "#1F4E79", "#0B1D2E"),
    ("#1E3A2F", "#8CC084", "#F6E27F", "#3E6B48", "#16291F"),
    ("#3D1C02", "#E8985E", "#FFF1D0", "#A0522D", "#4A2511"),
    ("#120E2B", "#5F4B8B", "#E6D5FF", "#3A2E6B", "#0D0A1F"),
    ("#073B4C", "#06D6A0", "#FFD166", "#118AB2", "#05293A"),
    ("#4A1942", "#F4A261", "#FFE8D6", "#893168", "#2E0F2A"),
    ("#22223B", "#9A8C98", "#F2E9E4", "#4A4E69", "#14142A"),
]

def render(svg, path, w, h):
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg)
    subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h), f.name, "-o", path], check=True)
    os.unlink(f.name)

def ridge(rnd, w, base, amp, n, color):
    pts = [(0, base)]
    for i in range(1, n):
        pts.append((w * i / (n - 1), base - rnd.uniform(0.2, 1.0) * amp))
    d = "M0 " + str(base) + "".join(f" L{x:.0f} {y:.0f}" for x, y in pts) + f" L{w} {base} V9999 H0 Z"
    return f'<path d="{d}" fill="{color}"/>'

def scene(seed, w, h, motif):
    rnd = random.Random(seed)
    top, bottom, accent, far, near = PALETTES[seed % len(PALETTES)]
    s = [f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{top}"/>'
         f'<stop offset="1" stop-color="{bottom}"/></linearGradient></defs><rect width="{w}" height="{h}" fill="url(#g)"/>']
    cx, cy, r = w * rnd.uniform(0.3, 0.7), h * rnd.uniform(0.3, 0.5), min(w, h) * rnd.uniform(0.12, 0.2)
    if motif == "stars":
        s += [f'<circle cx="{rnd.uniform(0, w):.0f}" cy="{rnd.uniform(0, h * 0.6):.0f}" r="{rnd.uniform(1, 3):.1f}" fill="{accent}"/>' for _ in range(60)]
    s.append(f'<circle cx="{cx:.0f}" cy="{cy:.0f}" r="{r:.0f}" fill="{accent}" opacity="0.95"/>')
    if motif == "mountains":
        s += [ridge(rnd, w, h * 0.75, h * 0.35, 6, far), ridge(rnd, w, h * 0.95, h * 0.3, 8, near)]
    elif motif == "waves":
        for i, c in enumerate((far, near)):
            y = h * (0.68 + 0.14 * i)
            s.append(f'<path d="M0 {y:.0f} Q{w*.25:.0f} {y-h*.06:.0f} {w*.5:.0f} {y:.0f} T{w} {y:.0f} V{h} H0Z" fill="{c}"/>')
    elif motif == "city":
        x = 0
        while x < w:
            bw, bh = rnd.uniform(w * .06, w * .14), rnd.uniform(h * .2, h * .55)
            s.append(f'<rect x="{x:.0f}" y="{h - bh:.0f}" width="{bw:.0f}" height="{bh:.0f}" fill="{rnd.choice((far, near))}"/>')
            x += bw + 2
    elif motif == "dunes":
        s += [f'<path d="M0 {h*.7:.0f} C{w*.3:.0f} {h*.55:.0f} {w*.6:.0f} {h*.85:.0f} {w} {h*.65:.0f} V{h} H0Z" fill="{far}"/>',
              f'<path d="M0 {h*.85:.0f} C{w*.4:.0f} {h*.7:.0f} {w*.7:.0f} {h*.95:.0f} {w} {h*.8:.0f} V{h} H0Z" fill="{near}"/>']
    elif motif in ("forest", "stars"):
        s.append(ridge(rnd, w, h * 0.85, h * 0.1, 5, far))
        for _ in range(14):
            tx, th = rnd.uniform(0, w), rnd.uniform(h * .15, h * .32)
            s.append(f'<path d="M{tx:.0f} {h - th - h*.1:.0f} L{tx + th*.25:.0f} {h:.0f} H{tx - th*.25:.0f}Z" fill="{near}"/>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">{"".join(s)}</svg>'

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    motifs = ["mountains", "waves", "city", "dunes", "forest", "stars"]
    for i in range(20):
        render(scene(i, 300, 450, motifs[i % len(motifs)]), f"{OUT}/poster{i}.png", 300, 450)
    render(scene(3, 1920, 1080, "mountains"), f"{OUT}/still.png", 1920, 1080)
    print("ok", OUT)
