#!/usr/bin/env python3
"""
Verification harness for the Kotlin PDF engine.

It re-implements — step for step — what the app does:
  * PageGeometry.layout / placement   (Kotlin: engine/PageGeometry.kt)
  * PdfBuilder                         (Kotlin: engine/PdfBuilder.kt, incl. xref)
  * the DPI/quality ladder             (Kotlin: engine/Rungs.kt)

...then writes real PDFs and checks them with an independent reader (pypdf),
plus compares the page-size math against the original Flask/Pillow app so the
port stays honest.

Usage:  python3 tools/verify_pdf.py
"""
from __future__ import annotations

import io
import os
import sys

from PIL import Image, ImageOps

try:
    import pypdf
except ImportError:
    pypdf = None


# --------------------------------------------------------------------------
# 1. Port of the original Flask helpers (reference behaviour)
# --------------------------------------------------------------------------
def page_points(size, orientation, image_size):
    sizes = {"A4": (595.276, 841.89), "A5": (419.528, 595.276), "Letter": (612, 792)}
    if size == "Original":
        return image_size[0] * 72 / 96, image_size[1] * 72 / 96
    w, h = sizes.get(size, sizes["A4"])
    if orientation == "Auto":
        return (max(w, h), min(w, h)) if image_size[0] > image_size[1] else (min(w, h), max(w, h))
    if orientation == "Landscape":
        return max(w, h), min(w, h)
    return min(w, h), max(w, h)


MARGINS = {"None": 0, "Small": 18, "Medium": 36, "Large": 54}
PAGE_SIZES = {"A4": (595.276, 841.89), "A5": (419.528, 595.276), "Letter": (612, 792)}


# --------------------------------------------------------------------------
# 2. Port of the Kotlin engine (what the Android app does)
# --------------------------------------------------------------------------
def kotlin_layout(page_size, orientation, margins, image_w, image_h):
    if page_size == "Original":
        pw, ph = image_w * 72 / 96, image_h * 72 / 96
    else:
        w, h = PAGE_SIZES[page_size]
        long_side, short_side = max(w, h), min(w, h)
        if orientation == "Auto":
            pw, ph = (long_side, short_side) if image_w > image_h else (short_side, long_side)
        elif orientation == "Landscape":
            pw, ph = long_side, short_side
        else:
            pw, ph = short_side, long_side
    m = MARGINS[margins]
    return pw, ph, m, max(1.0, pw - 2 * m), max(1.0, ph - 2 * m)


def kotlin_placement(page_size, orientation, margins, fit, image_w, image_h, dpi):
    pw, ph, m, aw, ah = kotlin_layout(page_size, orientation, margins, image_w, image_h)
    sl, st, sw, sh = 0, 0, image_w, image_h
    if fit == "Fill":
        ratio = aw / ah
        if image_w / image_h > ratio:
            cw = max(1, round(image_h * ratio))
            sl, sw = (image_w - cw) // 2, cw
        else:
            ch = max(1, round(image_w / ratio))
            st, sh = (image_h - ch) // 2, ch
    if fit == "Fill":
        scale = min(aw / sw, ah / sh)
    elif page_size == "Original":
        scale = min(1.0, min(aw / sw, ah / sh))
    else:
        scale = min(aw / sw, ah / sh)
    draw_w = float(max(1, round(sw * scale)))
    draw_h = float(max(1, round(sh * scale)))
    _, _, _, area_w, area_h = kotlin_layout(page_size, orientation, margins, image_w, image_h)
    clamp = min(1.0, min(area_w / draw_w, area_h / draw_h))
    if clamp < 1.0:
        draw_w *= clamp
        draw_h *= clamp
    off_x = max(0.0, (pw - draw_w) / 2)
    off_y = max(0.0, (ph - draw_h) / 2)
    want_w = max(1, round(draw_w * dpi / 72.0))
    want_h = max(1, round(draw_h * dpi / 72.0))
    detail = min(1.0, min(sw / want_w, sh / want_h))
    return dict(page_w=pw, page_h=ph, margin=m, off_x=off_x, off_y=off_y,
                draw_w=draw_w, draw_h=draw_h,
                raster_w=max(1, round(want_w * detail)), raster_h=max(1, round(want_h * detail)),
                src=(sl, st, sw, sh))


def render_page(img: Image.Image, page_size, orientation, margins, fit, dpi, quality):
    """Bitmap a page would contain, mirroring PageEncoder.encode()."""
    p = kotlin_placement(page_size, orientation, margins, fit, img.width, img.height, dpi)
    page = Image.new("RGB", (p["raster_w"], p["raster_h"]), "white")
    crop = img.crop((p["src"][0], p["src"][1], p["src"][0] + p["src"][2], p["src"][1] + p["src"][3]))
    resized = crop.resize((p["raster_w"], p["raster_h"]), Image.Resampling.LANCZOS)
    if crop.size != (p["raster_w"], p["raster_h"]):
        page.paste(resized, (0, 0))
    else:
        page.paste(crop, (0, 0))
    buf = io.BytesIO()
    page.save(buf, format="JPEG", quality=quality, optimize=False)
    return buf.getvalue(), p


# --------------------------------------------------------------------------
# 3. Port of PdfBuilder (structure + xref offsets)
# --------------------------------------------------------------------------
def fmt(v: float) -> str:
    return str(int(v)) if v == int(v) else f"{v:.2f}"


class PdfBuilderPy:
    def __init__(self):
        self.buf = bytearray()
        self.offsets = {}
        self.next_obj = 1
        self.page_objects = []
        self.catalog = self._alloc()
        self.pages = self._alloc()
        self.info = self._alloc()

    def _alloc(self):
        n = self.next_obj
        self.next_obj += 1
        return n

    def _w(self, data):
        self.buf += data if isinstance(data, bytes) else data.encode("latin-1")

    def _obj(self, n):
        self.offsets[n] = len(self.buf)
        self._w(f"{n} 0 obj\n")

    def open(self, title="Document"):
        self._w("%PDF-1.4\n")
        self._w(b"%\xe2\xe3\xcf\xd3\n")
        self.title = title

    def add_page(self, page_w, page_h, img_w, img_h, x, y, draw_w, draw_h, jpeg: bytes):
        page_obj, content_obj, image_obj = self._alloc(), self._alloc(), self._alloc()
        self._obj(page_obj)
        self._w(f"<< /Type /Page /Parent {self.pages} 0 R /MediaBox [0 0 {fmt(page_w)} {fmt(page_h)}] "
                f"/Resources << /XObject << /Im0 {image_obj} 0 R >> /ProcSet [/PDF /ImageC] >> "
                f"/Contents {content_obj} 0 R >>\nendobj\n")
        content = (f"q\n{fmt(draw_w)} 0 0 {fmt(draw_h)} {fmt(x)} {fmt(page_h - y - draw_h)} cm\n/Im0 Do\nQ\n").encode()
        self._obj(content_obj)
        self._w(f"<< /Length {len(content)} >>\nstream\n")
        self._w(content)
        self._w("\nendstream\nendobj\n")
        self._obj(image_obj)
        self._w(f"<< /Type /XObject /Subtype /Image /Width {img_w} /Height {img_h} "
                f"/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length {len(jpeg)} >>\nstream\n")
        self._w(jpeg)
        self._w("\nendstream\nendobj\n")
        self.page_objects.append(page_obj)

    def finish(self) -> bytes:
        self._obj(self.pages)
        kids = " ".join(f"{o} 0 R" for o in self.page_objects)
        self._w(f"<< /Type /Pages /Count {len(self.page_objects)} /Kids [{kids}] >>\nendobj\n")
        self._obj(self.catalog)
        self._w(f"<< /Type /Catalog /Pages {self.pages} 0 R >>\nendobj\n")
        self._obj(self.info)
        self._w(f"<< /Producer (Toolex PDF) /Title ({self.title}) /CreationDate (D:20260101000000) >>\nendobj\n")
        xref = len(self.buf)
        max_obj = self.next_obj - 1
        table = [f"xref\n0 {max_obj + 1}\n", "0000000000 65535 f \n"]
        for i in range(1, max_obj + 1):
            table.append("%010d 00000 n \n" % self.offsets.get(i, 0))
        table.append(f"trailer\n<< /Size {max_obj + 1} /Root {self.catalog} 0 R /Info {self.info} 0 R >>\n")
        table.append(f"startxref\n{xref}\n%%EOF\n")
        self._w("".join(table))
        return bytes(self.buf)


LADDER = [(240, 92), (220, 90), (200, 88), (180, 86), (160, 84),
          (150, 80), (130, 76), (110, 70), (96, 64), (80, 58)]


def build_pdf(images, page_size="A4", orientation="Auto", margins="None", fit="Fit",
              target=None, quality_override=None):
    ladder = LADDER if target is not None else [(200, 88)]
    if quality_override:
        ladder = [quality_override]
    best = None
    for dpi, quality in ladder:
        b = PdfBuilderPy()
        b.open("test")
        for img in images:
            jpeg, p = render_page(img, page_size, orientation, margins, fit, dpi, quality)
            b.add_page(p["page_w"], p["page_h"], p["raster_w"], p["raster_h"],
                       p["off_x"], p["off_y"], p["draw_w"], p["draw_h"], jpeg)
        data = b.finish()
        if best is None or len(data) < len(best[0]):
            best = (data, dpi, quality)
        if target is None or len(data) <= target:
            return data, dpi, quality, (target is None or len(data) <= target)
    return best[0], best[1], best[2], False


# --------------------------------------------------------------------------
# 4. Checks
# --------------------------------------------------------------------------
def sample_images():
    out = []
    base = os.path.join(os.path.dirname(__file__), "..", "docs", "sample")
    os.makedirs(base, exist_ok=True)
    specs = [
        ("portrait.jpg", (1200, 1600), (240, 240, 240)),
        ("landscape.jpg", (2000, 1000), (200, 60, 60)),
        ("small.png", (600, 600), (30, 120, 200)),
    ]
    for name, size, color in specs:
        img = Image.new("RGB", size, color)
        # add some noise/structure so JPEG does not compress to ~nothing
        px = img.load()
        for y in range(0, size[1], 7):
            for x in range(0, size[0], 7):
                v = (x * y) % 255
                px[x, y] = (v, (v * 2) % 255, (v * 3) % 255)
        path = os.path.join(base, name)
        img.save(path, quality=92)
        out.append(Image.open(path).convert("RGB"))
    return out


def check_geometry(images):
    print("geometry: Kotlin port vs Flask reference")
    cases = 0
    for img in images:
        for ps in ["A4", "A5", "Letter", "Original"]:
            for orient in ["Auto", "Portrait", "Landscape"]:
                for fit in ["Fit", "Fill"]:
                    for margins in ["None", "Small", "Medium", "Large"]:
                        ref_w, ref_h = page_points(ps, orient, img.size)
                        got = kotlin_placement(ps, orient, margins, fit, img.width, img.height, 200)
                        assert abs(got["page_w"] - ref_w) < 1e-6, (ps, orient, img.size, got["page_w"], ref_w)
                        assert abs(got["page_h"] - ref_h) < 1e-6, (ps, orient, img.size, got["page_h"], ref_h)
                        # nothing may stick out of the page
                        assert got["off_x"] >= 0 and got["off_y"] >= 0
                        assert got["off_x"] + got["draw_w"] <= got["page_w"] + 0.01
                        assert got["off_y"] + got["draw_h"] <= got["page_h"] + 0.01
                        if margins != "None":
                            m = MARGINS[margins]
                            # the picture must respect the margin box on all sides
                            assert got["off_x"] >= m - 0.01, (ps, orient, fit, margins, got["off_x"], m)
                            assert got["off_y"] >= m - 0.01, (ps, orient, fit, margins, got["off_y"], m)
                            assert got["off_x"] + got["draw_w"] <= got["page_w"] - m + 0.01
                            assert got["off_y"] + got["draw_h"] <= got["page_h"] - m + 0.01
                        cases += 1
    print(f"  OK {cases} layout cases match the Flask app")


def check_merged(images):
    data, dpi, quality, met = build_pdf(images[:2])
    if pypdf is None:
        print("  pypdf missing, skipping structural checks")
        return data
    reader = pypdf.PdfReader(io.BytesIO(data))
    assert len(reader.pages) == 2, len(reader.pages)
    box = reader.pages[0].mediabox
    print(f"  merged pdf: {len(reader.pages)} pages, page1 {float(box.width):.2f}x{float(box.height):.2f} pt, "
          f"{len(data)/1024:.0f} KB @ {dpi} dpi q{quality}")
    assert abs(float(box.width) - 595.276) < 0.01
    assert abs(float(box.height) - 841.89) < 0.01
    # images must be extractable -> stream lengths/offsets are correct
    for i, page in enumerate(reader.pages):
        imgs = page.images
        assert len(imgs) == 1, f"page {i} images: {len(imgs)}"
        pil = imgs[0].image
        assert pil.width > 100 and pil.height > 100
    print("  every page has exactly one decodable JPEG")
    return data


def check_target(images):
    print("target ladder:")
    natural, _, _, _ = build_pdf(images)
    for factor in (0.9, 0.6, 0.3, 0.05):
        target = int(len(natural) * factor)
        data, dpi, quality, met = build_pdf(images, target=target)
        print(f"  target {target/1024:6.0f} KB -> {len(data)/1024:6.0f} KB  dpi {dpi:3d} q{quality:2d}  met={met}")
        if met:
            assert len(data) <= target
        assert len(data) >= 0
    # impossible target must still produce a valid, smallest-possible file
    data, dpi, quality, met = build_pdf(images, target=10 * 1024)
    print(f"  impossible 10 KB -> {len(data)/1024:.0f} KB dpi {dpi} met={met} (expected met=False)")
    assert met is False
    return natural


def check_sizes(images):
    print("page sizes / directions:")
    for ps in ("A4", "A5", "Letter", "Original"):
        for orient in ("Auto", "Portrait", "Landscape"):
            data, _, _, _ = build_pdf([images[0]], page_size=ps, orientation=orient, quality_override=(120, 70))
            if pypdf is None:
                continue
            box = pypdf.PdfReader(io.BytesIO(data)).pages[0].mediabox
            w, h = float(box.width), float(box.height)
            expect = kotlin_layout(ps, orient, "None", images[0].width, images[0].height)[:2]
            assert abs(w - expect[0]) < 0.01 and abs(h - expect[1]) < 0.01
            portrait = h >= w
            print(f"  {ps:8s} {orient:9s} -> {w:7.1f} x {h:7.1f} pt  {'portrait' if portrait else 'landscape'}")


def check_fill(images):
    print("fill vs fit crop:")
    img = images[1]  # 2:1 landscape
    for fit in ("Fit", "Fill"):
        jpeg, p = render_page(img, "A4", "Portrait", "None", fit, 200, 88)
        print(f"  {fit:4s}: source crop {p['src']} -> raster {p['raster_w']}x{p['raster_h']}")
    _, fit_p = render_page(img, "A4", "Portrait", "None", "Fit", 200, 88)
    _, fill_p = render_page(img, "A4", "Portrait", "None", "Fill", 200, 88)
    assert fill_p["raster_w"] / fill_p["raster_h"] > 0.6  # page shaped
    assert fit_p["src"] == (0, 0, img.width, img.height)  # fit keeps the whole photo
    assert fill_p["src"] != fit_p["src"]                   # fill crops it
    print("  OK")


def main():
    images = sample_images()
    print(f"sample images: {[im.size for im in images]}\n")
    check_geometry(images)
    check_merged(images)
    check_sizes(images)
    check_fill(images)
    check_target(images)
    print("\nall checks passed")


if __name__ == "__main__":
    sys.exit(main())
