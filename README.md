# Toolex — Images → PDF (Android, Kotlin)

<p align="left">
  <a href="https://github.com/BossLeader786/Toolex/actions/workflows/android.yml"><img alt="Build" src="https://github.com/BossLeader786/Toolex/actions/workflows/android.yml/badge.svg"></a>
  <a href="https://github.com/BossLeader786/Toolex/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/BossLeader786/Toolex"></a>
  <img alt="minSdk" src="https://img.shields.io/badge/Android-7.0%2B-3ddc84">
</p>

**📲 Phone par install karo:** [**latest APK download**](https://github.com/BossLeader786/Toolex/releases/latest) — ya ye QR scan karo:

<img src="docs/download_qr.png" width="180" alt="Download QR">

Native Android port of the **PaperLite** Flask/Pillow tool you shared. Same logic, same
options, same honest "target size is a maximum, not a promise" behaviour — but it runs
**fully offline on the phone**, with no server, no upload and no paid API.

<p align="center">
  <i>Kotlin · Jetpack Compose · Material 3 · zero third-party PDF/image libraries</i>
</p>

---

## What it does

| Feature | Detail |
|---|---|
| Merge images into one PDF | Multiple JPG/PNG/WEBP → one multi-page PDF |
| Separate PDFs | One PDF per image, delivered together in a ZIP |
| Reorder before export | Arrows on each thumbnail (page order = strip order) |
| Page sizes | A4, A5, Letter, Original |
| Orientation | Auto (follows the photo), Portrait, Landscape |
| Placement | **Fit** (whole photo, white bars) or **Fill** (cover page, edges cropped) |
| Margins | None, Small (6.3 mm), Medium (12.7 mm), Large (19 mm) |
| Normal quality | 200 DPI / JPEG 88 — the everyday balance |
| Custom max size | KB/MB ceiling with a DPI+quality ladder that keeps the **highest quality that fits** |
| Size transparency | Source bytes **and** estimated PDF bytes shown separately, plus real output size after export |
| Result actions | Open · Share · Save to Downloads |
| Limits | 100 images per job, 200 MB total, 10 KB min target |

Everything happens in the app's private storage; nothing is uploaded. The app has **no
INTERNET permission on purpose**.

## Install (GitHub se, laptop ki zarurat nahi)

1. Phone me link kholo: **https://github.com/BossLeader786/Toolex/releases/latest**
2. `Toolex-*-debug.apk` download karo
3. Install karte waqt "unknown sources / Install anyway" allow karo
4. App kholo → images chuno → **PDF banao**

Har naya version `v*` tag par banti hai; har push par bhi APK Actions me
(`Actions → Toolex build → Artifacts`) mil jata hai.

## Build & run

Requirements: Android Studio (Ladybug+) or a local JDK 17 + Android SDK 34.

```bash
./gradlew :app:assembleDebug          # APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # JVM unit tests (page math, parsing, naming)
./gradlew installDebug                # install on a connected device/emulator
```

Or open the folder in Android Studio and hit ▶.

* `minSdk 24` (Android 7.0) · `targetSdk 34`
* No runtime permissions needed on Android 10+ (system photo picker + MediaStore).
* On Android 7–9 the "Save to Downloads" button uses `WRITE_EXTERNAL_STORAGE`
  (declared with `maxSdkVersion="28"`).

## Project layout

```
app/src/main/java/com/toolex/
├── MainActivity.kt                  entry point + "share images to Toolex"
└── pdf/
    ├── model/
    │   ├── PdfOptions.kt            enums + page_points() port
    │   └── Models.kt                SourceImage, ConversionResult, Progress...
    ├── engine/                      ← the whole brain, no UI imports
    │   ├── PageGeometry.kt          page box + placement math (unit tested)
    │   ├── PageEncoder.kt           bitmap render → JPEG page
    │   ├── PdfBuilder.kt            hand-written PDF writer (exact byte count)
    │   ├── PdfConverter.kt          merged / separate / retry ladder
    │   ├── SizeEstimator.kt         "estimated PDF size" pass
    │   ├── ZipWriter.kt             stored ZIP for separate mode
    │   ├── ImageLoader.kt           decode + EXIF + white background
    │   └── Rungs.kt                 DPI/quality ladder
    ├── ui/                          Compose screen, ViewModel, sheets
    └── util/                        FileStore, ExportHelper, MathHeuristics
```

`docs/PDF_ENGINE.md` explains the conversion logic line by line, including how it maps
onto the original Python functions.

## GitHub-only workflow

Yeh repo **puri tarah GitHub par** chalta hai — koi local setup zaroori nahi:

| Trigger | Kya hota hai |
|---|---|
| koi bhi push / PR | tests + engine verification + APK build, diagnostics commit comments me |
| `v1.0.0` jaise tag | **GitHub Release** + installable APK asset |
| `main` push / tag | install page (QR + APK) GitHub Pages par, jab Pages enable ho |
| Actions tab → Run workflow | manual build |

```bash
# naya version release karna
git tag -a v1.1.0 -m "Toolex 1.1.0"
git push origin v1.1.0
```

Release signing (optional): repo secrets me `TOOLEX_KEYSTORE` (base64-decoded
path), `TOOLEX_STORE_PASSWORD`, `TOOLEX_KEY_ALIAS`, `TOOLEX_KEY_PASSWORD` daal
do — CI release build ko usi key se sign karega. Bina secrets ke debug-signed
APK milta hai (install aur test ke liye kaafi).

## Verifying the engine without Android

`tools/verify_pdf.py` re-implements the Kotlin engine 1:1 in Python and checks it with an
independent PDF reader:

```bash
python3 -m venv .venv && .venv/bin/pip install pypdf Pillow
.venv/bin/python tools/verify_pdf.py
```

It proves three things:
1. **288 layout cases** (4 page sizes × 3 orientations × 2 fits × 4 margins × 3 photos)
   match the original Flask `page_points()` numbers exactly.
2. The generated PDFs are structurally valid — pages, `MediaBox`, xref offsets and the
   embedded JPEGs all parse in `pypdf`.
3. The size ladder actually hits targets, and honestly reports `met=False` when a target
   is impossible.

## Honest limits (same as PaperLite)

* A target size is a **maximum goal**. JPEG can't always reach an extreme ceiling without
  destroying readability, so at the smallest rung the app gives you the smallest file it
  can make and labels it clearly instead of lying.
* In separate mode the chosen ceiling applies to **each** PDF.
* Transparent PNG backgrounds become white.
* Very large originals are downsampled to what the chosen DPI needs — that is exactly why
  the output stays small.

## Credits

Original web module: **PaperLite** (`reference/paperlite_app.py`, Flask + Pillow).
This repo is the Android/Kotlin reimplementation.
