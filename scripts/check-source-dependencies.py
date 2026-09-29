#!/usr/bin/env python3
"""Check that source-level third-party API use has a direct module dependency.

Main code must declare the library it imports. Test code follows the same rule,
while allowing Spring Boot's standard test starter to provide its documented
JUnit, AssertJ, Mockito, Awaitility and Spring Test bundle.
"""

from __future__ import annotations

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
BOOT_TEST_STARTER = ("org.springframework.boot", "spring-boot-starter-test")

# prefix -> (source kind, accepted direct dependencies)
RULES: dict[str, tuple[str, set[tuple[str, str]]]] = {
    "org.assertj.": (
        "test",
        {("org.assertj", "assertj-core"), BOOT_TEST_STARTER},
    ),
    "org.junit.jupiter.": (
        "test",
        {
            ("org.junit.jupiter", "junit-jupiter"),
            ("org.junit.jupiter", "junit-jupiter-api"),
            BOOT_TEST_STARTER,
        },
    ),
    "org.mockito.": (
        "test",
        {("org.mockito", "mockito-core"), BOOT_TEST_STARTER},
    ),
    "org.awaitility.": (
        "test",
        {("org.awaitility", "awaitility"), BOOT_TEST_STARTER},
    ),
    "org.springframework.test.": (
        "test",
        {("org.springframework", "spring-test"), BOOT_TEST_STARTER},
    ),
    "org.springframework.boot.test.": (
        "test",
        {("org.springframework.boot", "spring-boot-test"), BOOT_TEST_STARTER},
    ),
    "com.tngtech.archunit.": (
        "test",
        {("com.tngtech.archunit", "archunit-junit5")},
    ),
    "org.slf4j.": ("main", {("org.slf4j", "slf4j-api")}),
    "lombok.": ("main", {("org.projectlombok", "lombok")}),
    "org.apache.commons.lang3.": (
        "main",
        {("org.apache.commons", "commons-lang3")},
    ),
    "org.apache.commons.collections4.": (
        "main",
        {("org.apache.commons", "commons-collections4")},
    ),
    "org.apache.commons.codec.": ("main", {("commons-codec", "commons-codec")}),
    "org.apache.commons.io.": ("main", {("commons-io", "commons-io")}),
    "com.fasterxml.jackson.databind.": (
        "main",
        {("com.fasterxml.jackson.core", "jackson-databind")},
    ),
    "com.fasterxml.jackson.annotation.": (
        "main",
        {("com.fasterxml.jackson.core", "jackson-annotations")},
    ),
    "com.fasterxml.jackson.core.": (
        "main",
        {("com.fasterxml.jackson.core", "jackson-core")},
    ),
}


def module_poms() -> dict[Path, Path]:
    return {path.parent: path for path in ROOT.rglob("pom.xml")}


def nearest_module(file_path: Path, poms: dict[Path, Path]) -> Path | None:
    current = file_path.parent
    while current != ROOT.parent:
        if current in poms:
            return current
        current = current.parent
    return None


def declared_dependencies(pom: Path) -> dict[tuple[str, str], str]:
    project = ET.parse(pom).getroot()
    result: dict[tuple[str, str], str] = {}
    for dependency in project.findall("m:dependencies/m:dependency", NS):
        group = dependency.findtext("m:groupId", default="", namespaces=NS)
        artifact = dependency.findtext("m:artifactId", default="", namespaces=NS)
        scope = dependency.findtext("m:scope", default="compile", namespaces=NS)
        result[(group, artifact)] = scope
    return result


def imports_of(file_path: Path) -> list[str]:
    content = file_path.read_text(encoding="utf-8", errors="ignore")
    return [
        match.group(1)
        for match in re.finditer(
            r"^import\s+(?:static\s+)?([\w.]+)", content, re.MULTILINE
        )
    ]


def dependency_is_usable(
    declared: dict[tuple[str, str], str],
    accepted: set[tuple[str, str]],
    source_kind: str,
) -> bool:
    for coordinate in accepted:
        scope = declared.get(coordinate)
        if scope is None:
            continue
        if source_kind == "main" and scope in {"test", "runtime"}:
            continue
        return True
    return False


def main() -> int:
    poms = module_poms()
    used: dict[Path, set[tuple[str, str, tuple[tuple[str, str], ...]]]] = {}
    for source in ROOT.rglob("*.java"):
        source_path = source.as_posix()
        source_kind = "test" if "/src/test/" in source_path else "main"
        if f"/src/{source_kind}/java/" not in source_path:
            continue
        module = nearest_module(source, poms)
        if module is None:
            continue
        for imported in imports_of(source):
            for prefix, (allowed_kind, accepted) in RULES.items():
                if source_kind == allowed_kind and imported.startswith(prefix):
                    used.setdefault(module, set()).add(
                        (prefix, source_kind, tuple(sorted(accepted)))
                    )

    errors: list[str] = []
    for module, requirements in sorted(used.items()):
        declared = declared_dependencies(poms[module])
        for prefix, source_kind, accepted_tuple in sorted(requirements):
            accepted = set(accepted_tuple)
            if dependency_is_usable(declared, accepted, source_kind):
                continue
            expected = " or ".join(
                f"{group}:{artifact}" for group, artifact in sorted(accepted)
            )
            errors.append(
                f"{module.relative_to(ROOT)}: {source_kind} source imports {prefix}* "
                f"but does not directly declare {expected}"
            )

    print(f"Modules checked: {len(poms)}")
    print(f"Missing declared source dependencies: {len(errors)}")
    for error in errors:
        print(f"ERROR: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
