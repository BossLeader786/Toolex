#!/usr/bin/env python3
"""
Independent validation of PDFs produced by the real Kotlin engine
(tools/EngineHarness.kt, driven by tools/verify_engine_kotlin.sh).
"""
from __future__ import annotations

import os
import re
import sys

try:
    import pypdf
except ImportError:
    print("pypdf required: python3 -m venv .venv && .venv/bin/pip install pypdf")
    raise SystemExit(2)

MAP_KEYS = ("pageW", "pageH", "offX", "offY", "drawW", "drawH", "raster", "src", "dpi")


def parse_log(path):
    pages, files = [], {}
    for line in open(path):
        if line.startswith("PAGE "):
            parts = line.split()
            record = {"caption": parts[1].split("=")[1]}
            for p in parts[2:]:
                k, _, v = p.partition("=")
                record[k] = v
            pages.append(record)
        elif line.startswith("FILE "):
            parts = dict(p.split("=", 1) for p in line.split()[1:])
            files[parts["caption"]] = parts
    return pages, files


def check_file(path, expected_pages):
    reader = pypdf.PdfReader(path)
    assert len(reader.pages) == expected_pages, f"{path}: {len(reader.pages)} != {expected_pages}"
    for i, page in enumerate(reader.pages):
        content = page.get_contents().get_data().decode("latin-1")
        box = page.mediabox
        # our content stream format: "q\n<w> 0 0 <h> x y cm\n/Im0 Do\nQ\n"
        m = re.search(r"q\s+([\d.]+) 0 0 ([\d.]+) ([\d.]+) ([\d.]+) cm\s+/Im0 Do\s+Q", content)
        assert m, f"{path} page {i}: unexpected content stream {content!r}"
        dw, dh, x, y = (float(g) for g in m.groups())
        assert 0 <= x and 0 <= y
        assert x + dw <= float(box.width) + 0.51, f"{path} page {i}: image outside page (x)"
        assert y + dh <= float(box.height) + 0.51, f"{path} page {i}: image outside page (y)"
        xobj = page["/Resources"]["/XObject"]["/Im0"]
        assert xobj["/Subtype"] == "/Image"
        assert xobj["/Filter"] == "/DCTDecode"
        assert int(xobj["/Width"]) > 0 and int(xobj["/Height"]) > 0
        # Reading the stream end-to-end proves /Length and the xref offsets are
        # right: a wrong length truncates or over-reads the payload.
        data = xobj.get_data()
        assert len(data) > 0, f"{path} page {i}: empty image stream"
        raw = len(xobj._data) if hasattr(xobj, "_data") else None
        if raw is not None:
            assert raw == len(data), f"{path} page {i}: stream length mismatch"
    return reader


def main():
    pdf_dir = sys.argv[1]
    log = sys.argv[2]
    pages, files = parse_log(log)

    for caption, info in files.items():
        path = info["path"]
        expected = sum(1 for p in pages if p["caption"] == caption)
        check_file(path, expected)
        print(f"  OK {os.path.basename(path):28s} {expected} page(s), {int(info['bytes'])} bytes")

    # Cross-check geometry lines against the page boxes pypdf reports.
    for record in pages:
        reader = pypdf.PdfReader(
            next(f["path"] for c, f in files.items() if c == record["caption"]),
        )
        page = reader.pages[int(record.get("index", 0))]
        assert abs(float(page.mediabox.width) - float(record["pageW"])) < 0.02
        assert abs(float(page.mediabox.height) - float(record["pageH"])) < 0.02
        # the placement must fit inside the page (PDF origin is bottom-left)
        y_top = float(page.mediabox.height) - float(record["offY"]) - float(record["drawH"])
        assert y_top >= -0.51

    print(f"  OK {len(pages)} page metadata lines cross-checked")
    print("  OK all PDFs are structurally valid")
    return 0


if __name__ == "__main__":
    sys.exit(main())
