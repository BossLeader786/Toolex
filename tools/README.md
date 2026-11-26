# Verification tools

These scripts check the engine **without** Android Studio or an emulator.

| Script | What it proves |
|---|---|
| `verify_pdf.py` | A 1:1 Python re-implementation of the Kotlin engine produces the same page geometry as the original Flask app (288 cases) and writes PDFs that `pypdf` can read, including the size ladder. |
| `verify_engine_kotlin.sh` | Compiles the **real** Kotlin engine (`PageGeometry`, `PdfBuilder`, models) on a plain JVM using `tools/android_stubs*.kt`, runs `EngineHarness.kt` and validates the generated PDFs. |
| `check_generated_pdfs.py` | Independent reader check used by the Kotlin harness: page count, `MediaBox`, content stream, image XObjects, xref/stream lengths. |

```bash
# logic check (no Kotlin toolchain needed)
python3 -m venv .venv && .venv/bin/pip install pypdf Pillow
.venv/bin/python tools/verify_pdf.py

# real Kotlin code, needs `kotlinc` on PATH
./tools/verify_engine_kotlin.sh
```

`android_stubs*.kt` and `EngineHarness.kt` are **not** part of the app — they only
exist so the pure engine can run on a desktop JVM.
