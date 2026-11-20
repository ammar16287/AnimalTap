"""
Builds the Google Play listing graphics into store/:
  icon-512.png                 512x512 app icon (same paw as the launcher icon)
  feature-graphic-1024x500.png Play Store feature graphic

Usage:  python tools/make_store_graphics.py   (needs Pillow + numpy; Windows fonts for text/emoji)
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "store")
FONTS = "C:/Windows/Fonts/"
YELLOW, BROWN, NAVY = (0xFF, 0xD1, 0x66), (0x6B, 0x3E, 0x26), (0x2B, 0x4C, 0x7E)


def bezier(p0, p1, p2, p3, n=40):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = map(np.array, (p0, p1, p2, p3))
    pts = (1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t ** 2 * p2 + t ** 3 * p3
    return [tuple(p) for p in pts]


# Paw shape from res/drawable/ic_launcher_foreground.xml (108-unit viewport).
PAD = sum((bezier(*s) for s in [
    ((54, 52), (64, 52), (72, 62), (72, 69)), ((72, 69), (72, 76), (66, 78), (61, 77)),
    ((61, 77), (57, 76), (55, 75), (54, 75)), ((54, 75), (53, 75), (51, 76), (47, 77)),
    ((47, 77), (42, 78), (36, 76), (36, 69)), ((36, 69), (36, 62), (44, 52), (54, 52))]), [])
TOES = [(38, 48), (48, 38), (60, 38), (70, 48)]


def draw_paw(d, ox, oy, scale, color):
    d.polygon([(ox + x * scale, oy + y * scale) for x, y in PAD], fill=color)
    for cx, cy in TOES:
        d.ellipse([ox + (cx - 6) * scale, oy + (cy - 7.5) * scale,
                   ox + (cx + 6) * scale, oy + (cy + 7.5) * scale], fill=color)


def icon():
    ss = 4                                    # supersample for smooth edges
    n = 512 * ss
    img = Image.new("RGB", (n, n), YELLOW)
    scale = n / 108 * 1.15
    off = -(108 * scale - n) / 2
    draw_paw(ImageDraw.Draw(img), off, off - 4 * scale, scale, BROWN)
    img.resize((512, 512), Image.LANCZOS).save(os.path.join(OUT, "icon-512.png"))


def feature_graphic():
    w, h = 1024, 500
    y = np.linspace(0, 1, h)[:, None]
    top, mid, bot = (np.array(c, float) for c in ([0xBF, 0xE6, 0xFF], [0xE8, 0xF7, 0xFF], [0x8B, 0xD1, 0x7C]))
    grad = np.where(y < 0.5, top + (mid - top) * (y / 0.5), mid + (bot - mid) * ((y - 0.5) / 0.5))
    img = Image.fromarray(np.repeat(grad[:, None, :], w, axis=1).astype("uint8")).convert("RGBA")

    # Paw badge with a soft drop shadow.
    ss = 4
    badge = Image.new("RGBA", (220 * ss, 220 * ss), (0, 0, 0, 0))
    bd = ImageDraw.Draw(badge)
    bd.ellipse([0, 0, 220 * ss - 1, 220 * ss - 1], fill=YELLOW + (255,), outline=(255, 255, 255, 255), width=8 * ss)
    draw_paw(bd, -30 * ss, -36 * ss, 2.6 * ss, BROWN + (255,))
    badge = badge.resize((220, 220), Image.LANCZOS)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([70, 133, 290, 353], fill=(0, 0, 0, 70))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(10)))
    img.alpha_composite(badge, (70, 120))

    bold = ImageFont.truetype(FONTS + "segoeuib.ttf", 96)
    reg = ImageFont.truetype(FONTS + "segoeui.ttf", 38)
    text_shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(text_shadow).text((337, 127), "Animal Tap", font=bold, fill=(0, 40, 80, 70))
    img.alpha_composite(text_shadow.filter(ImageFilter.GaussianBlur(3)))
    d = ImageDraw.Draw(img)
    d.text((334, 122), "Animal Tap", font=bold, fill=NAVY)
    d.text((340, 250), "Tap, listen & laugh!", font=reg, fill=NAVY)
    emoji = ImageFont.truetype(FONTS + "seguiemj.ttf", 64)
    d.text((340, 320), "🐄🐶🐱🐘🦁🐵🐸", font=emoji, embedded_color=True)
    img.convert("RGB").save(os.path.join(OUT, "feature-graphic-1024x500.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon()
    feature_graphic()
    print("Wrote store/icon-512.png and store/feature-graphic-1024x500.png")
