# How Toolex builds a PDF (Kotlin engine deep-dive)

This document explains the conversion logic and how it maps onto the original
PaperLite Flask/Pillow module (`reference/paperlite_app.py`).

```
               ┌──────────────┐
 image files → │ ImageLoader  │  decode, EXIF-rotate, white background
               └──────┬───────┘
                      │ size + pixels
               ┌──────▼───────┐
               │ PageGeometry │  page box (points) + placement
               └──────┬───────┘
                      │
               ┌──────▼───────┐
               │ PageEncoder  │  bitmap page → JPEG at (dpi, quality)
               └──────┬───────┘
                      │ jpeg bytes
               ┌──────▼───────┐
               │ PdfBuilder   │  PDF 1.4: catalog, pages, content, image XObject
               └──────┬───────┘
                      │ exact byte size
      ┌───────────────┴────────────────┐
      │ fits the target?               │
      │ no → next rung of the ladder ──┘
      │ yes → publish (PDF or ZIP)
      └────────────────────────────────
```

## 1. Page box — `PageGeometry.layout()`

Port of `page_points()`:

| Input | Result |
|---|---|
| `Original` | `image_px * 72 / 96` (photo pixels at 96 DPI) |
| `A4 / A5 / Letter` | fixed points: A4 `595.276×841.89`, A5 `419.528×595.276`, Letter `612×792` |
| `Auto` | landscape photo → page landscape, else portrait |
| `Portrait / Landscape` | forced |

Then the printable area is `max(1, page − 2·margin)` — margins are already in points
(18 / 36 / 54).

## 2. Placement — `PageGeometry.placement()`

Port of `render_page()`, but expressed in points + pixels instead of Pillow canvas ops:

1. **Fill** center-crops the source to the page's aspect ratio
   (`ratio = areaW / areaH`, crop the longer side) — identical math to the Python.
2. `scale = min(areaW/srcW, areaH/srcH)`; on `Original` size it is additionally capped at
   `1.0` so a small photo is never stretched.
3. The picture is clamped to the page (`thumbnail` safety net), then centered.
4. Raster size = `drawPt * dpi / 72`, but **capped by the source detail** — never encode
   more pixels than the photo actually has. `effectiveDpi` reports what was really used.

`PageGeometryTest` pins all of this, including "nothing may leave the page".

## 3. Rendering a page — `PageEncoder`

* decode with `inSampleSize` so a 50 MP photo never inflates the heap,
* rotate by EXIF orientation,
* draw on a white canvas (`transparent → white`, like the Flask version),
* `Bitmap.compress(JPEG, quality)`.

Each page is written to the scratch dir as one JPEG file, so memory stays flat for a
100-page job.

## 4. PDF structure — `PdfBuilder`

A page is:

```
q
<drawW> 0 0 <drawH> <x> <y> cm
/Im0 Do
Q
```

with the photo stored as a `/DCTDecode` (JPEG) image XObject. JPEG is the same thing
Pillow puts into a PDF, so quality/size profile matches the web app.

Why hand-written instead of `android.graphics.pdf.PdfDocument`:
`PdfDocument` re-encodes images (we lose control of quality) and gives no byte count while
writing — and the whole "fit under X MB" feature depends on knowing the exact size before
publishing.

The writer counts every byte it emits, so the xref table offsets are exact;
`tools/verify_pdf.py` validates the produced files with `pypdf`.

## 5. The size ladder — `PdfConverter`

```
240/92 → 220/90 → 200/88 → 180/86 → 160/84 → 150/80 → 130/76 → 110/70 → 96/64 → 80/58
```

* **Normal**: only `200/88` is rendered (fast, good quality).
* **Custom**: rungs are tried top-down; the **first one that fits wins**, i.e. the highest
  detail that satisfies the ceiling. Every attempt is kept if it's smaller than the current
  best, so if nothing fits we still deliver the smallest possible file and flag
  `TargetStatus.MISSED` (this is the Flask `optimize_pdf()` fallback).
* **Separate mode + custom size**: each PDF gets a proportional share of the ceiling
  (`target * fileSize / totalSize`, min 10 KB) — reasoning identical to the Python code.

## 6. Estimation — `SizeEstimator`

The web app built a throwaway PDF for `/api/analyze`. On a phone that doubles the work, so
Toolex *measures* instead: it JPEG-encodes every page at `200/88` and adds per-page PDF
overhead (~900 B) + file overhead. JPEG is ~90 % of a photo PDF's bytes, so the number on
screen lands within a few percent of the real output.

Suggested ceilings in the Custom sheet are `25 / 40 / 55 / 70 / 85 / 100 %` of that
estimate, rounded to nice values, plus free KB/MB entry. Like the original, a value above
the natural size is capped automatically, and the UI says so.

## 7. Export

* Merged → `"<first-image>_merged.pdf"` (or `"<name>.pdf"` for one image).
* Separate → `Toolex_Separate_PDFs.zip`, stored (no recompression: PDFs are already
  compressed), duplicate names get `_2`, `_3` suffixes like the Python dedupe.
* File names are sanitised with the same rule as `safe_name()` (`[^A-Za-z0-9._-]+ → _`,
  trimmed, max 70 chars).
* Output lives in app-private storage; "Save to Downloads" copies it out via MediaStore
  (or legacy file copy on Android 9 and below).

## Mapping table (Python → Kotlin)

| Flask/Pillow | Kotlin |
|---|---|
| `safe_name()` | `FileStore.safeBaseName()` |
| `page_points()` | `PdfOptions.pagePoints()` + `PageGeometry.layout()` |
| `read_image()` | `ImageLoader.inspect()` / `decode()` + `PageEncoder` |
| `render_page()` | `PageGeometry.placement()` + `PageEncoder.encode()` |
| `build_pdf()` | `PdfBuilder` |
| `optimize_pdf()` | `PdfConverter.convertMerged()` (ladder loop) |
| `pdf_response()` | `ConversionResult` + `ExportHelper` |
| `/api/analyze` | `SizeEstimator.estimate()` |
| `/api/convert` | `PdfViewModel.convert()` → `PdfConverter.convert()` |
| `zipfile` block | `ZipWriter` |
| `MAX_CONTENT_LENGTH` / 100-image cap | `PdfViewModel.MAX_TOTAL_BYTES` / `MAX_IMAGES` |

## Testing strategy

* **JVM unit tests** (`./gradlew testDebugUnitTest`): page math for every combination,
  never-leave-the-page invariants, override parsing, name sanitising, ladder ordering.
* **Python harness** (`tools/verify_pdf.py`): 1:1 re-implementation checked with `pypdf`
  against 288 layout cases and real generated PDFs, including an impossible-target case.
* **Manual**: pick 100 photos, merged + separate, custom 200 KB ceiling, then open the
  result and zoom — page edges, margins and crop behaviour are visible immediately.
