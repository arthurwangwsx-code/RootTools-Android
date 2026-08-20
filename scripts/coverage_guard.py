#!/usr/bin/env python3
"""Verify non-regression coverage for pure/high-risk RootTools core classes."""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT = ROOT / "app" / "build" / "reports" / "kover" / "reportDebug.xml"
BASELINE = ROOT / "config" / "coverage-baseline.json"


def line_coverage(class_element: ET.Element) -> tuple[int, int, float]:
    for counter in class_element.findall("counter"):
        if counter.attrib.get("type") != "LINE":
            continue
        covered = int(counter.attrib["covered"])
        missed = int(counter.attrib["missed"])
        total = covered + missed
        percent = 100.0 if total == 0 else covered * 100.0 / total
        return covered, total, percent
    return 0, 0, 100.0


def main() -> int:
    report = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else DEFAULT_REPORT
    if not report.exists():
        print(f"ERROR: Kover XML report not found: {report}", file=sys.stderr)
        return 1

    thresholds: dict[str, float] = json.loads(BASELINE.read_text(encoding="utf-8"))
    xml_root = ET.parse(report).getroot()
    classes: dict[str, ET.Element] = {}
    for package in xml_root.findall("package"):
        for class_element in package.findall("class"):
            classes[class_element.attrib["name"]] = class_element

    errors: list[str] = []
    print("RootTools core coverage guard")
    for class_name, minimum in thresholds.items():
        class_element = classes.get(class_name)
        if class_element is None:
            errors.append(f"coverage target disappeared from report: {class_name}")
            continue
        covered, total, percent = line_coverage(class_element)
        status = "PASS" if percent + 1e-9 >= minimum else "FAIL"
        print(f"{status:4s} {percent:6.2f}% >= {minimum:5.1f}%  {covered:3d}/{total:3d}  {class_name}")
        if status == "FAIL":
            errors.append(
                f"coverage regressed for {class_name}: {percent:.2f}% < baseline {minimum:.1f}%"
            )

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
