#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "artifacts/validation")
    captures = root / "device-captures" / "captures"
    if not captures.exists():
        captures = root / "device-captures"

    rows = []
    for meta in sorted(captures.glob("*.json")):
        try:
            data = json.loads(meta.read_text())
        except Exception:
            continue
        if not data.get("stoppedAt"):
            continue
        analysis = data.get("analysis") or {}
        package = data.get("packageName", "")
        if package == "__all__":
            continue
        safe = package.replace(".", "_")
        screen = root / safe / "screen.png"
        protocols = analysis.get("protocols") or []
        flows = analysis.get("flows") or []
        rows.append({
            "id": data.get("id"),
            "label": data.get("appLabel"),
            "package": package,
            "packets": analysis.get("packetCount", 0),
            "bytes": analysis.get("byteCount", 0),
            "protocols": protocols,
            "topFlows": flows[:8],
            "pcap": Path(data.get("pcapPath", "")).name,
            "metadata": meta.name,
            "screenshot": str(screen.relative_to(root)) if screen.exists() else None,
        })

    # Keep the latest session for each package.
    latest = {}
    for row in rows:
        latest[row["package"]] = row
    rows = sorted(latest.values(), key=lambda r: r["label"] or r["package"])

    summary = {
        "validatedAppCount": len(rows),
        "apps": rows,
        "notes": [
            "Root raw capture uses PCAPdroid pcapd with the selected Android UID filter when the packaged backend is available.",
            "tcpdump remains a whole-device fallback only when pcapd is unavailable.",
            "Protocol classification is produced locally by Net Tools' PCAP parser.",
        ],
    }
    (root / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))

    lines = [
        "# Net Tools 真机抓包验收报告",
        "",
        f"已形成可审查证据的 App 数量：**{len(rows)}**。",
        "",
        "> 当前真机验收使用 PCAPdroid `pcapd -u <UID>` 作为 Root backend，按所选 App 的 Android UID 过滤；只有 pcapd 不可用时才回退到全设备 tcpdump。",
        "",
        "| App | Package | Packets | Bytes | Main protocols | Screenshot |",
        "|---|---|---:|---:|---|---|",
    ]
    for row in rows:
        protocols = ", ".join(f"{p.get('protocol')}({p.get('packets')})" for p in row["protocols"][:5]) or "-"
        shot = row["screenshot"] or "-"
        lines.append(f"| {row['label']} | `{row['package']}` | {row['packets']} | {row['bytes']} | {protocols} | `{shot}` |")

    lines += ["", "## Top flows", ""]
    for row in rows:
        lines.append(f"### {row['label']} (`{row['package']}`)")
        lines.append("")
        lines.append(f"- PCAP: `{row['pcap']}`")
        lines.append(f"- Metadata: `{row['metadata']}`")
        lines.append(f"- Screenshot: `{row['screenshot'] or 'missing'}`")
        if not row["topFlows"]:
            lines.append("- Top flow: 无可解析 flow")
        else:
            for flow in row["topFlows"]:
                target = flow.get("host") or flow.get("destination") or "?"
                hint = flow.get("hint") or ""
                lines.append(f"- `{flow.get('protocol')}` → `{target}` × {flow.get('packets', 1)} {hint}")
        lines.append("")
    (root / "REPORT.md").write_text("\n".join(lines))
    print(f"summarized {len(rows)} apps -> {root / 'REPORT.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
