#!/usr/bin/env bash
# Type-checks and runs the REAL Kotlin engine on a plain JVM (no Android SDK).
#
#   ./tools/verify_engine_kotlin.sh
#
# Requires: java + kotlinc on PATH (Android Studio ships kotlinc at
#   ~/Android/Sdk/... or via `brew install kotlin` / `sdk install kotlin`).
#
# It compiles the pure engine files together with tools/android_stubs*.kt,
# generates real PDFs, runs the unit-test-style assertion program, and hands the
# PDFs to tools/check_generated_pdfs.py for independent validation.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT="${OUT_DIR:-build/engine-check}"
mkdir -p "$OUT/classes" "$OUT/pdf"

KOTLINC="${KOTLINC:-kotlinc}"
if ! command -v "$KOTLINC" >/dev/null 2>&1; then
  echo "kotlinc not found. Install Kotlin or set KOTLINC=/path/to/kotlinc" >&2
  exit 1
fi

echo "== compiling engine + harness =="
"$KOTLINC" -nowarn \
  app/src/main/java/com/toolex/pdf/model/Models.kt \
  app/src/main/java/com/toolex/pdf/model/PdfOptions.kt \
  app/src/main/java/com/toolex/pdf/engine/PageGeometry.kt \
  app/src/main/java/com/toolex/pdf/engine/EncodedPage.kt \
  app/src/main/java/com/toolex/pdf/engine/PdfBuilder.kt \
  app/src/main/java/com/toolex/pdf/engine/Rungs.kt \
  tools/android_stubs.kt \
  tools/android_stubs_2.kt \
  tools/EngineHarness.kt \
  -include-runtime -d "$OUT/engine.jar"

echo "== running harness (real engine, fake page bytes) =="
java -jar "$OUT/engine.jar" "$OUT/pdf" | tee "$OUT/harness.log"

echo "== validating generated PDFs with an independent reader =="
python3 tools/check_generated_pdfs.py "$OUT/pdf" "$OUT/harness.log"
