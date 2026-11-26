# Toolex

An offline Android photo editor and image-to-PDF converter for Android 7–16 (API 24–36). Built with Kotlin, Jetpack Compose and Android graphics APIs.

## Build and install

Open the repository in Android Studio Meerkat Feature Drop or newer. Install Android SDK Platform 36 and Build Tools 35.0.0. Select JDK 17 for Gradle. Sync, select the app configuration and run on a device. From a terminal:

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest
```

On Windows use gradlew.bat. The Android workflow publishes the debug APK under the run's Artifacts and runs device tests on API 29 and 36. Debug APK signing keys can differ across CI builds. If Android rejects an update due to the signing key, uninstall the prior debug build and reconnect the export folder after installing.

## Export folder

In Settings choose Save location, then select Internal storage → Toolex using Android’s system picker. Create that directory in the picker if this is the first export. Android 7–10 also permit selecting the primary volume root, in which case Toolex creates the directory on the first save. Android 11–16 require selecting the subfolder. The app verifies the Android external-storage provider and document ID. Other folders and SD cards are rejected. An unavailable picker or denied grant leaves the preview available for retry.

Files are ordinary documents in shared storage, survive uninstall, and can be opened by compatible file managers. Reinstalling requires granting access to the existing folder again. Android’s external-storage provider assigns unique names on collision. Only the new incomplete document is removed if a write fails. No all-files permission is requested, so the Google Play all-files-access exception is unnecessary.

## Tools

- **Image to PDF** imports multiple images, allows page ordering and removal, and supports A4, A5, Letter or original size (96 pixels per inch), orientation, margins in points, fit/fill and JPEG quality. Preview any generated page before saving, opening or sharing the PDF.
- **Compression** measures complete PDFs including their cross-reference tables. It tries successively smaller raster sizes and searches JPEG qualities at the chosen size. If none fits, the smallest-detail result remains available with a warning. KB and MB are decimal. Processing is page by page.
- **Photo Editing** supports draggable crop corners, moving a crop area, free or fixed proportions, quarter-turn rotation, horizontal flip, tonal adjustments, filters, a bottom caption, resizing, undo, redo and reset. Crop operates in the original upright image coordinates before rotation. Edits are nondestructive.
- **Output** is encoded in the background after edits settle. The byte count, preview and export use the same encoded temporary file. JPG supports quality control and flattens transparency on white. PNG uses lossless encoding. Processing can downsample large sources and caps output at 8192 pixels per side and up to 8 megapixels depending on available memory. Output dimensions are shown before saving.
- **Settings** stores appearance, PDF and JPG defaults, image format and the folder permission locally. Input and export data stay on the device. The app requests no internet access. Active editing sessions are held in memory; exported files and preferences persist.
