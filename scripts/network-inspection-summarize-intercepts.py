#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path


def load_session(directory: Path):
    summary = directory / "session.json"
    if not summary.exists():
        return None
    data = json.loads(summary.read_text())
    kinds = Counter()
    samples = []
    events = directory / "events.jsonl"
    if events.exists():
        for line in events.read_text(errors="replace").splitlines():
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            kinds[event.get("kind", "unknown")] += 1
            if event.get("kind") in {"http_req", "http_rep", "tls_err"} and len(samples) < 12:
                # `preview` is already redacted by RootTools. Raw payload blobs are never read here.
                samples.append((event.get("kind"), event.get("preview", "").splitlines()[0][:240]))
    return data, kinds, samples


def main():
    parser = argparse.ArgumentParser(
        description="Summarize RootTools decrypted sessions without reading raw payload blobs"
    )
    parser.add_argument("directory", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    rows = []
    details = []
    if not args.directory.exists():
        raise SystemExit(f"Directory not found: {args.directory}")

    for directory in sorted((p for p in args.directory.iterdir() if p.is_dir()), reverse=True):
        loaded = load_session(directory)
        if not loaded:
            continue
        data, kinds, samples = loaded
        rows.append(
            f"| {data.get('appLabel', '')} | `{data.get('packageName', '')}` | "
            f"{data.get('decryptedEvents', 0)} | {data.get('httpRequests', 0)} | "
            f"{data.get('httpResponses', 0)} | {data.get('tlsErrors', 0)} |"
        )
        section = [f"### {data.get('appLabel', data.get('packageName', directory.name))}", ""]
        section.append(
            "Event kinds: " + (", ".join(f"{k}={v}" for k, v in kinds.most_common()) or "none")
        )
        if samples:
            section += ["", "Redacted samples:"] + [f"- `{kind}` {text}" for kind, text in samples]
        details.append("\n".join(section))

    report = "\n".join(
        [
            "# RootTools Network Inspection decryption summary",
            "",
            "This report reads only `session.json` and already-redacted `events.jsonl`; raw payload blobs are deliberately excluded.",
            "",
            "| App | Package | Events | HTTP req | HTTP resp | TLS errors |",
            "|---|---|---:|---:|---:|---:|",
            *rows,
            "",
            *details,
            "",
        ]
    )
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report)
    else:
        print(report)


if __name__ == "__main__":
    main()
