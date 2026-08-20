#!/usr/bin/env python3
"""Static security invariants for a privileged/root Android application."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID = "{" + ANDROID_NS + "}"

MAIN_MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
DEBUG_MANIFEST = ROOT / "app" / "src" / "debug" / "AndroidManifest.xml"
ACTION_ROUTER = ROOT / "app" / "src" / "main" / "java" / "com" / "arthur" / "roottools" / "automation" / "ActionRouterReceiver.kt"

PLATFORM_PROTECTED_EXPORTED_PERMISSIONS = {
    "android.permission.BIND_QUICK_SETTINGS_TILE",
    "android.permission.INTERACT_ACROSS_USERS_FULL",
    "android.permission.DUMP",
}

SENSITIVE_SHARED_PREFS = {
    "automation_api.xml",
    "automation_clients.xml",
}

DANGEROUS_ENTRYPOINT_PATTERNS = (
    re.compile(r"Runtime\.getRuntime\(\)\.exec\("),
    re.compile(r"ProcessBuilder\("),
    re.compile(r"shell\.execute\(\s*intent\.get"),
    re.compile(r"shell\.executeBatch\(\s*intent\.get"),
)


def attr(element: ET.Element, name: str) -> str | None:
    return element.attrib.get(ANDROID + name)


def exported_components(manifest: Path) -> list[ET.Element]:
    root = ET.parse(manifest).getroot()
    application = root.find("application")
    if application is None:
        return []
    return [element for element in application if attr(element, "exported") == "true"]


def is_launcher_activity(element: ET.Element) -> bool:
    if element.tag != "activity":
        return False
    actions = {
        attr(action, "name")
        for intent_filter in element.findall("intent-filter")
        for action in intent_filter.findall("action")
    }
    categories = {
        attr(category, "name")
        for intent_filter in element.findall("intent-filter")
        for category in intent_filter.findall("category")
    }
    return "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories


def validate_exported_component(element: ET.Element, errors: list[str]) -> None:
    name = attr(element, "name") or "<unnamed>"
    permission = attr(element, "permission")
    if is_launcher_activity(element):
        return
    if name == ".automation.ActionRouterReceiver":
        source = ACTION_ROUTER.read_text(encoding="utf-8")
        required_markers = (
            "intent.component ?: return",
            "component.packageName != context.packageName",
            "ActionTokenStore(context).matches(token)",
            "AutomationClientStore(context).authorize",
            "AutomationAuthorizationPolicy.isAllowed",
        )
        missing = [marker for marker in required_markers if marker not in source]
        if missing:
            errors.append(
                "exported ActionRouterReceiver lost explicit-component/credential/scope checks: "
                + ", ".join(missing)
            )
        return
    if permission not in PLATFORM_PROTECTED_EXPORTED_PERMISSIONS:
        errors.append(
            f"exported {element.tag} {name} is not protected by an approved platform permission"
        )


def validate_manifest_labels(errors: list[str]) -> None:
    root = ET.parse(MAIN_MANIFEST).getroot()
    application = root.find("application")
    if application is None:
        return
    for element in [application, *list(application)]:
        label = attr(element, "label")
        if label and not label.startswith("@string/"):
            errors.append(
                f"manifest user-visible label must use a string resource: {attr(element, 'name') or 'application'} = {label!r}"
            )


def collect_excluded_sharedprefs(path: Path) -> set[str]:
    if not path.exists():
        return set()
    root = ET.parse(path).getroot()
    return {
        element.attrib.get("path", "")
        for element in root.iter("exclude")
        if element.attrib.get("domain") == "sharedpref"
    }


def validate_backup_rules(errors: list[str]) -> None:
    root = ET.parse(MAIN_MANIFEST).getroot()
    application = root.find("application")
    if application is None or attr(application, "allowBackup") != "true":
        return
    if attr(application, "fullBackupContent") != "@xml/backup_rules":
        errors.append("allowBackup=true requires @xml/backup_rules for Android 11 and lower")
    if attr(application, "dataExtractionRules") != "@xml/data_extraction_rules":
        errors.append("allowBackup=true requires @xml/data_extraction_rules for Android 12+")

    old_excludes = collect_excluded_sharedprefs(ROOT / "app" / "src" / "main" / "res" / "xml" / "backup_rules.xml")
    missing_old = sorted(SENSITIVE_SHARED_PREFS - old_excludes)
    if missing_old:
        errors.append("legacy backup rules expose automation credentials: " + ", ".join(missing_old))

    extraction_path = ROOT / "app" / "src" / "main" / "res" / "xml" / "data_extraction_rules.xml"
    if not extraction_path.exists():
        errors.append("missing data_extraction_rules.xml")
        return
    extraction = ET.parse(extraction_path).getroot()
    for section_name in ("cloud-backup", "device-transfer"):
        section = extraction.find(section_name)
        excludes = {
            element.attrib.get("path", "")
            for element in section.findall("exclude")
            if element.attrib.get("domain") == "sharedpref"
        } if section is not None else set()
        missing = sorted(SENSITIVE_SHARED_PREFS - excludes)
        if missing:
            errors.append(
                f"{section_name} exposes automation credentials: " + ", ".join(missing)
            )


def validate_entrypoint_process_execution(errors: list[str]) -> None:
    roots = (
        ROOT / "app" / "src" / "main" / "java" / "com" / "arthur" / "roottools" / "automation",
        ROOT / "app" / "src" / "main" / "java" / "com" / "arthur" / "roottools" / "tiles",
        ROOT / "app" / "src" / "main" / "java" / "com" / "arthur" / "roottools" / "widget",
        ROOT / "app" / "src" / "main" / "java" / "com" / "arthur" / "roottools" / "boot",
    )
    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*.kt"):
            source = path.read_text(encoding="utf-8")
            for pattern in DANGEROUS_ENTRYPOINT_PATTERNS:
                if pattern.search(source):
                    errors.append(
                        f"external entrypoint contains arbitrary process/shell execution pattern: {path.relative_to(ROOT)}"
                    )


def main() -> int:
    errors: list[str] = []
    for manifest in (MAIN_MANIFEST, DEBUG_MANIFEST):
        if manifest.exists():
            for component in exported_components(manifest):
                validate_exported_component(component, errors)
    validate_manifest_labels(errors)
    validate_backup_rules(errors)
    validate_entrypoint_process_execution(errors)

    print("RootTools security guard")
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
