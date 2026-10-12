"""Publish a digest of a Gradle log through check annotations and a commit comment.

The Arena sandbox has no JDK, no Android SDK and no route to Maven Central, so
build results have to travel back through the GitHub API. Annotations give the
short version; the commit comment carries the long tail.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys

LOG = "build.log"
MAX_ANNOTATIONS = 40
COMMENT_TAIL_LINES = 220
COMMENT_LIMIT = 55000

MARKERS = (
    "e: ",
    "error:",
    "FAILURE:",
    "FAILED",
    "Execution failed",
    "Caused by:",
    "What went wrong",
    "Unresolved reference",
    "Type mismatch",
    "cannot be applied",
)


def read_lines() -> list[str]:
    try:
        with open(LOG, "r", errors="replace") as handle:
            return handle.read().splitlines()
    except FileNotFoundError:
        return []


def escape(message: str) -> str:
    return message.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def emit_comment(body: str) -> None:
    repo = os.environ.get("GITHUB_REPOSITORY")
    sha = os.environ.get("GITHUB_SHA")
    if not repo or not sha:
        print("no repository/sha in environment; skipping comment")
        return
    payload = json.dumps({"body": body})
    result = subprocess.run(
        ["gh", "api", "-X", "POST", f"repos/{repo}/commits/{sha}/comments", "--input", "-"],
        input=payload,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        print(f"comment failed: {result.returncode} {result.stderr}")
    else:
        print(f"published commit comment: {result.stdout.strip()}")


def main() -> int:
    status = sys.argv[1] if len(sys.argv) > 1 else "unknown"
    lines = read_lines()
    print(f"gradle status: {status}; log lines: {len(lines)}")

    digest: list[str] = []
    seen: set[str] = set()
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped in seen:
            continue
        if any(marker in stripped for marker in MARKERS):
            seen.add(stripped)
            digest.append(stripped)

    for line in digest[:MAX_ANNOTATIONS]:
        print(f"::error ::{escape(line[:900])}")

    body_lines = ["### Arena Verify digest", "", f"Gradle exit status: `{status}`", "", "```"]
    body_lines.extend(line[:1200] for line in lines[-COMMENT_TAIL_LINES:])
    body_lines.append("```")
    emit_comment("\n".join(body_lines)[:COMMENT_LIMIT])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
