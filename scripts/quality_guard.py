#!/usr/bin/env python3
"""Low-cost repository guardrails for RootTools.

The project intentionally carries legacy debt in DashboardScreen and
DashboardViewModel. This check freezes that debt instead of requiring a risky
whole-app rewrite. New files and new user-visible strings have stricter rules.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "app" / "src" / "main" / "java"

# Existing debt ceilings. Reduce these numbers as the legacy files are split.
LEGACY_LINE_CEILINGS = {
    "app/src/main/java/com/arthur/roottools/ui/DashboardScreen.kt": 2950,
    "app/src/main/java/com/arthur/roottools/ui/DashboardViewModel.kt": 1060,
    "app/src/main/java/com/arthur/roottools/ui/AppControlCenterScreen.kt": 950,
    # Developer Runtime predates the strict feature file-size guard. Keep the current debt frozen
    # until it is split; new feature screens still use NEW_KOTLIN_FILE_LINE_LIMIT.
    "app/src/main/java/com/arthur/roottools/feature/developer/DeveloperRuntimeScreen.kt": 1328,
}

NEW_KOTLIN_FILE_LINE_LIMIT = 900
HARDCODED_UI_STRING_BASELINE = 160

# These files predate the strict no-literal rule. Any other Kotlin UI file must have zero direct
# Text/contentDescription literals under the current detector. Lower the ceilings as each feature
# is localized.
HARDCODED_UI_STRING_CEILINGS = {
    "app/src/main/java/com/arthur/roottools/ui/DashboardScreen.kt": 160,
    "app/src/main/java/com/arthur/roottools/ui/AppControlCenterScreen.kt": 0,
    "app/src/main/java/com/arthur/roottools/ui/integrity/DeviceIntegrityScreen.kt": 0,
}

TEXT_LITERAL_PATTERNS = (
    re.compile(r"\bText\(\s*\""),
    re.compile(r"contentDescription\s*=\s*\""),
)

ENTRYPOINT_ROOTS = (
    "app/src/main/java/com/arthur/roottools/ui",
    "app/src/main/java/com/arthur/roottools/tiles",
    "app/src/main/java/com/arthur/roottools/widget",
    "app/src/main/java/com/arthur/roottools/automation",
    "app/src/main/java/com/arthur/roottools/service",
    "app/src/main/java/com/arthur/roottools/boot",
)
DIRECT_ROOT_SHELL_CONSTRUCTION = re.compile(r"(?<![A-Za-z0-9_])RootShell\s*\(")
CJK = re.compile(r"[\u4e00-\u9fff]")
FEATURE_IMPORT = re.compile(r"^import\s+com\.arthur\.roottools\.feature\.([A-Za-z0-9_]+)\.", re.MULTILINE)
LEGACY_UI_IMPORT = re.compile(r"^import\s+com\.arthur\.roottools\.ui\.", re.MULTILINE)
LEGACY_DATA_POLICY_IMPORT = re.compile(r"^import\s+com\.arthur\.roottools\.(data|policy)\.", re.MULTILINE)
CORE_REVERSE_IMPORT = re.compile(r"^import\s+com\.arthur\.roottools\.(feature|ui)\.", re.MULTILINE)
DIRECT_COMPANION_DEPENDENCY = re.compile(
    r"\b(?:implementation|androidTestImplementation|debugImplementation|testImplementation|"
    r"compileOnly|coreLibraryDesugaring)\(\s*\""
)

REQUIRED_GRADLE_MODULES = (
    ":app",
    ":core:privilege",
    ":feature:network-inspection",
    ":companion:nfc-tools",
    ":companion:background-server",
    ":companion:hyperos-credential-fix",
)
COMPANION_APPLICATION_IDS = {
    "companion/nfc-tools/build.gradle.kts": "com.arthur.nfclab",
    "companion/background-server/build.gradle.kts": "com.aibox.backgroundserver",
    "companion/hyperos-credential-fix/build.gradle.kts": "com.arthur.hyperos.credentialfix",
}

LEGACY_FEATURE_DEPENDENCY_CEILINGS = {
    "app/src/main/java/com/arthur/roottools/feature/integrity/data/IntegrityRepository.kt": 2,
    # Existing Termux bridge composition still depends on two legacy data-layer collaborators.
    # Freeze the current count so future feature work cannot add more cross-layer dependencies.
    "app/src/main/java/com/arthur/roottools/feature/developer/DeveloperRuntimeViewModel.kt": 2,
}


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def count_lines(path: Path) -> int:
    with path.open("r", encoding="utf-8") as handle:
        return sum(1 for _ in handle)


def count_hardcoded_ui_strings() -> tuple[int, list[tuple[int, str]]]:
    total = 0
    files: list[tuple[int, str]] = []
    for path in KOTLIN_ROOT.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        count = sum(len(pattern.findall(text)) for pattern in TEXT_LITERAL_PATTERNS)
        if count:
            total += count
            files.append((count, relative(path)))
    return total, sorted(files, reverse=True)


def translatable_strings(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    root = ET.parse(path).getroot()
    for element in root.findall("string"):
        if element.attrib.get("translatable", "true") == "false":
            continue
        name = element.attrib["name"]
        result[name] = "".join(element.itertext())
    return result


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []

    for rel_path, ceiling in LEGACY_LINE_CEILINGS.items():
        path = ROOT / rel_path
        if not path.exists():
            continue
        lines = count_lines(path)
        if lines > ceiling:
            errors.append(
                f"legacy file grew beyond its ceiling: {rel_path} = {lines} > {ceiling}"
            )
        else:
            warnings.append(f"legacy debt: {rel_path} = {lines}/{ceiling} lines")

    legacy_paths = set(LEGACY_LINE_CEILINGS)
    for path in KOTLIN_ROOT.rglob("*.kt"):
        rel_path = relative(path)
        if rel_path in legacy_paths:
            continue
        lines = count_lines(path)
        if lines > NEW_KOTLIN_FILE_LINE_LIMIT:
            errors.append(
                f"new/non-legacy Kotlin file exceeds {NEW_KOTLIN_FILE_LINE_LIMIT} lines: "
                f"{rel_path} = {lines}"
            )

    hardcoded_count, hardcoded_files = count_hardcoded_ui_strings()
    for count, rel_path in hardcoded_files:
        ceiling = HARDCODED_UI_STRING_CEILINGS.get(rel_path)
        if ceiling is None:
            errors.append(
                f"new hard-coded Compose UI string in {rel_path}: {count}. "
                "Use Android string resources."
            )
        elif count > ceiling:
            errors.append(
                f"hard-coded UI string debt grew in {rel_path}: {count} > {ceiling}"
            )
    if hardcoded_count > HARDCODED_UI_STRING_BASELINE:
        errors.append(
            "hard-coded Compose UI strings increased: "
            f"{hardcoded_count} > baseline {HARDCODED_UI_STRING_BASELINE}. "
            "Use Android string resources for new user-visible text."
        )
    else:
        warnings.append(
            f"hard-coded UI string debt: {hardcoded_count}/{HARDCODED_UI_STRING_BASELINE}"
        )

    for rel_root in ENTRYPOINT_ROOTS:
        root = ROOT / rel_root
        if not root.exists():
            continue
        for path in root.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            if DIRECT_ROOT_SHELL_CONSTRUCTION.search(text):
                errors.append(
                    "Android/UI entrypoint constructs RootShell directly: "
                    f"{relative(path)}. Resolve it through RootToolsApp/AppContainer."
                )

    feature_root = KOTLIN_ROOT / "com" / "arthur" / "roottools" / "feature"
    if feature_root.exists():
        for feature_dir in feature_root.iterdir():
            if not feature_dir.is_dir():
                continue
            owner = feature_dir.name
            for path in feature_dir.rglob("*.kt"):
                text = path.read_text(encoding="utf-8")
                if LEGACY_UI_IMPORT.search(text):
                    errors.append(
                        f"feature implementation imports legacy UI host: {relative(path)}. "
                        "Depend on feature/core state instead."
                    )
                for imported_feature in FEATURE_IMPORT.findall(text):
                    if imported_feature != owner:
                        errors.append(
                            f"cross-feature implementation dependency: {relative(path)} imports "
                            f"feature.{imported_feature}. Move the contract to core or route through app composition."
                        )
                legacy_count = len(LEGACY_DATA_POLICY_IMPORT.findall(text))
                legacy_ceiling = LEGACY_FEATURE_DEPENDENCY_CEILINGS.get(relative(path), 0)
                if legacy_count > legacy_ceiling:
                    errors.append(
                        f"feature added legacy data/policy dependency: {relative(path)} = "
                        f"{legacy_count} > {legacy_ceiling}. Keep feature internals or move shared contracts to core."
                    )

    core_root = KOTLIN_ROOT / "com" / "arthur" / "roottools" / "core"
    if core_root.exists():
        for path in core_root.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            if CORE_REVERSE_IMPORT.search(text):
                errors.append(
                    f"core has reverse dependency on feature/ui: {relative(path)}"
                )

    settings_source = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    for module in REQUIRED_GRADLE_MODULES:
        if f'include("{module}")' not in settings_source:
            errors.append(f"canonical Gradle module missing from settings: {module}")

    source_snapshot_root = ROOT / "consolidation" / "sources"
    if source_snapshot_root.exists():
        snapshots = sorted(relative(path) for path in source_snapshot_root.rglob("*") if path.is_file())
        if snapshots:
            errors.append(
                "retired consolidation source snapshot returned: " + ", ".join(snapshots[:10])
            )

    companion_root = ROOT / "companion"
    forbidden_nested_build_files = {"gradlew", "gradlew.bat", "settings.gradle", "settings.gradle.kts"}
    for path in companion_root.rglob("*"):
        if path.is_file() and path.name in forbidden_nested_build_files:
            errors.append(
                f"companion reintroduced a nested Gradle root: {relative(path)}"
            )

    for rel_path, application_id in COMPANION_APPLICATION_IDS.items():
        build_file = ROOT / rel_path
        source = build_file.read_text(encoding="utf-8")
        if f'applicationId = "{application_id}"' not in source:
            errors.append(
                f"companion application identity drifted: {rel_path} must remain {application_id}"
            )
        if DIRECT_COMPANION_DEPENDENCY.search(source):
            errors.append(
                f"companion dependency bypasses gradle/libs.versions.toml: {rel_path}"
            )

    default_strings_path = ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    zh_strings_path = ROOT / "app" / "src" / "main" / "res" / "values-zh-rCN" / "strings.xml"
    if default_strings_path.exists() and zh_strings_path.exists():
        default_strings = translatable_strings(default_strings_path)
        zh_strings = translatable_strings(zh_strings_path)
        missing_zh = sorted(default_strings.keys() - zh_strings.keys())
        if missing_zh:
            errors.append(
                "Simplified Chinese resources are missing translatable default keys: "
                + ", ".join(missing_zh[:20])
            )
        default_cjk = sorted(name for name, value in default_strings.items() if CJK.search(value))
        if default_cjk:
            errors.append(
                "default values/strings.xml must remain the English fallback; CJK text found in: "
                + ", ".join(default_cjk[:20])
            )
        warnings.append(
            f"i18n resources: {len(default_strings)} translatable default keys / "
            f"{len(missing_zh)} missing zh-rCN"
        )

    print("RootTools quality guard")
    for warning in warnings:
        print(f"WARN: {warning}")
    if hardcoded_files:
        print("Largest hard-coded UI string sources:")
        for count, rel_path in hardcoded_files[:5]:
            print(f"  {count:4d}  {rel_path}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print("PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
