#!/usr/bin/env python3
"""
CI helper: mirrors build/test diagnostics into a commit comment.

The Actions *log download* endpoint is not reachable from every network, but the
REST API is, so a failing job can still leave readable evidence behind.

Usage (inside a workflow step):
    python3 tools/ci_report.py --log build.log --title "APK build log"

Environment: GITHUB_TOKEN (or GH_TOKEN), GITHUB_REPOSITORY, GITHUB_SHA.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

INTERESTING = re.compile(
    r"("
    r"^e: |error:|^FAILED|What went wrong|Caused by:|Execution failed|> Task .*FAILED|"
    r"Unresolved reference|Type mismatch|None of the following|Cannot access|"
    r"Android resource linking|AAPT|resource .* not found|"
    r"AssertionError|ComparisonFailure|org\.junit|expected:|"
    r"daemon|OutOfMemory|License|not found|No such"
    r")"
)

MAX_BODY = 60_000


def collect_test_failures(pattern: str) -> list[str]:
    failures: list[str] = []
    for path in glob.glob(pattern, recursive=True):
        try:
            for case in ET.parse(path).getroot().iter("testcase"):
                for bad in [*case.findall("failure"), *case.findall("error")]:
                    message = (bad.get("message") or "failed").splitlines()
                    where = f"{case.get('classname')}::{case.get('name')}"
                    failures.append(f"{where}: {message[0] if message else 'failed'}")
        except Exception as exc:  # noqa: BLE001
            failures.append(f"{path}: unreadable ({exc})")
    return failures


def post(body: str, token: str, repo: str, sha: str) -> None:
    payload = json.dumps({"body": body[:MAX_BODY]}).encode()
    request = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/commits/{sha}/comments",
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "toolex-ci",
        },
    )
    urllib.request.urlopen(request, timeout=30).read()


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", action="append", default=[], help="log file(s) to scan")
    parser.add_argument("--title", default="CI diagnostics")
    parser.add_argument("--tests", default="app/build/test-results/**/*.xml")
    parser.add_argument("--full", action="store_true", help="post the whole log tail instead of matches")
    args = parser.parse_args(argv[1:])

    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    sha = os.environ.get("GITHUB_SHA")
    if not (token and repo and sha):
        print("missing token/repo/sha; nothing posted", file=sys.stderr)
        return 0

    raw = ""
    for path in args.log:
        try:
            raw += f"\n===== {path} =====\n" + open(path, errors="replace").read()
        except FileNotFoundError:
            raw += f"\n===== {path} =====\n(not produced)\n"

    if args.full:
        body = raw[-MAX_BODY:]
    else:
        hits = [line for line in raw.splitlines() if INTERESTING.search(line)]
        body = "\n".join(hits)[-MAX_BODY:] or "(no error-pattern lines found)"

    sections = []
    failures = collect_test_failures(args.tests)
    if failures:
        sections.append("### Failing tests\n```\n" + "\n".join(failures) + "\n```")
    sections.append(f"### {args.title}\n```\n{body}\n```")

    try:
        post("\n\n".join(sections), token, repo, sha)
        print("diagnostics posted")
    except Exception as exc:  # noqa: BLE001 - never fail the job because of reporting
        print(f"could not post diagnostics: {exc}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
