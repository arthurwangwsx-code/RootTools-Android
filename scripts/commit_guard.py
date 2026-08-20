#!/usr/bin/env python3
"""Validate RootTools commit subjects against the repository commit convention."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


SUBJECT = re.compile(
    r"^(feat|fix|refactor|perf|test|docs|build|ci|chore)(\([a-z0-9][a-z0-9-]*\))?(!)?: .{3,72}$"
)
ALLOWED_AUTOMATIC = (
    re.compile(r"^Merge "),
    re.compile(r"^Revert "),
)


def validate(subject: str) -> str | None:
    subject = subject.strip()
    if any(pattern.match(subject) for pattern in ALLOWED_AUTOMATIC):
        return None
    if SUBJECT.match(subject):
        return None
    return (
        "expected Conventional Commit subject: "
        "type(scope): summary, e.g. feat(adb): add wireless endpoint health check"
    )


def subjects_from_range(revision_range: str) -> list[str]:
    result = subprocess.run(
        ["git", "log", "--format=%s", revision_range],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line for line in result.stdout.splitlines() if line.strip()]


def main() -> int:
    args = sys.argv[1:]
    if not args:
        print("usage: commit_guard.py <commit-msg-file> | --subject <text> | --range <git-range>", file=sys.stderr)
        return 2

    if args[0] == "--subject" and len(args) >= 2:
        subjects = [" ".join(args[1:])]
    elif args[0] == "--range" and len(args) == 2:
        subjects = subjects_from_range(args[1])
    else:
        path = Path(args[0])
        if not path.exists():
            print(f"ERROR: commit message file not found: {path}", file=sys.stderr)
            return 2
        first_line = path.read_text(encoding="utf-8").splitlines()
        subjects = [first_line[0]] if first_line else []

    errors: list[str] = []
    for subject in subjects:
        error = validate(subject)
        if error:
            errors.append(f"{subject!r}: {error}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
