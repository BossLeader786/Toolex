#!/usr/bin/env python3
"""
Builds the tiny "install Toolex" page that CI publishes on GitHub Pages:
the APK itself, a QR code pointing at the page, and a friendly download card.

Usage:
    python3 tools/make_install_page.py artifacts/Toolex-x-debug.apk site/

Needs `qrcode` (with the pillow backend) for the QR image.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

PAGE_URL = "https://bossleader786.github.io/Toolex/"
APK_NAME = "toolex-dev.apk"

TEMPLATE = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Toolex — install</title>
  <style>
    :root {{ color-scheme: light dark; }}
    body {{
      margin: 0; min-height: 100vh; display: grid; place-items: center;
      font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
      background: linear-gradient(160deg, #2F6FED, #6C3BF4); color: #fff;
    }}
    .card {{
      background: rgba(255, 255, 255, .96); color: #11151C; border-radius: 24px;
      padding: 32px 28px; max-width: 420px; width: calc(100% - 32px);
      text-align: center; box-shadow: 0 24px 60px rgba(0, 0, 0, .28);
    }}
    h1 {{ margin: 0 0 4px; font-size: 26px; letter-spacing: -.3px; }}
    p {{ margin: 6px 0; color: #4A5468; font-size: 14px; line-height: 1.5; }}
    .qr {{ margin: 18px auto 10px; width: 200px; height: 200px; }}
    a.btn {{
      display: block; margin-top: 16px; padding: 15px 20px; border-radius: 14px;
      background: linear-gradient(90deg, #2F6FED, #6C3BF4); color: #fff;
      text-decoration: none; font-weight: 600;
    }}
    code {{ background: #EDF0F7; border-radius: 6px; padding: 2px 6px; font-size: 12px; }}
    ul {{ text-align: left; font-size: 13px; color: #4A5468; padding-left: 20px; }}
  </style>
</head>
<body>
  <div class="card">
    <h1>Toolex</h1>
    <p>Images → PDF, offline · <b>{size}</b> · build {build} · <code>{sha}</code></p>
    <img class="qr" src="qr.png" alt="QR code">
    <p>Phone se QR scan karo, ya button dabao</p>
    <a class="btn" href="{apk}">Download APK</a>
    <p style="margin-top:14px">Install karte waqt &quot;unknown sources&quot; allow karna hoga.</p>
    <ul>
      <li>Android 7.0 (API 24) ya usse naya</li>
      <li>Koi internet permission nahi — sab phone par</li>
      <li>Debug build: test ke liye</li>
    </ul>
  </div>
</body>
</html>
"""


def human(size: int) -> str:
    mb = size / (1024 * 1024)
    return f"{mb:.1f} MB" if mb >= 1 else f"{size / 1024:.0f} KB"


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__)
        return 2

    apk = Path(argv[1])
    out = Path(argv[2])
    if not apk.exists():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 1

    out.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apk, out / APK_NAME)

    import os

    try:
        import qrcode  # type: ignore

        qrcode.make(PAGE_URL).save(out / "qr.png")
    except Exception as exc:  # noqa: BLE001 - QR is nice to have, never fatal
        print(f"QR skipped: {exc}", file=sys.stderr)

    (out / "index.html").write_text(
        TEMPLATE.format(
            size=human(apk.stat().st_size),
            build=os.environ.get("GITHUB_RUN_NUMBER", "local"),
            sha=os.environ.get("GITHUB_SHA", "local")[:7],
            apk=APK_NAME,
        ),
        encoding="utf-8",
    )
    # Tell GitHub Pages not to run Jekyll on it.
    (out / ".nojekyll").write_text("", encoding="utf-8")

    for path in sorted(out.iterdir()):
        print(f"  {path.name:20s} {path.stat().st_size:>10,} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
