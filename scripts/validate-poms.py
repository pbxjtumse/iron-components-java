#!/usr/bin/env python3
"""Validate repository POM structure without resolving Maven dependencies.

This script is intentionally limited to facts that can be established from the
checked-in XML: aggregator modules, local parent chains, dependency-management
boundaries and duplicate declarations. Source import checks live in
``check-source-dependencies.py`` and actual dependency resolution belongs to
Maven.
"""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
COMPONENT_ROOT = ROOT / "component"
COMPONENT_PARENT = COMPONENT_ROOT / "pom.xml"
COMPONENT_BOM = COMPONENT_ROOT / "component-bom" / "pom.xml"
PROJECT_GROUP = "com.xjtu.iron"
PROJECT_VERSION = "${project.version}"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

errors: list[str] = []
warnings: list[str] = []
models: dict[Path, ET.Element] = {}


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def node_text(element: ET.Element | None, path: str, default: str = "") -> str:
    if element is None:
        return default
    node = element.find(path, NS)
    if node is None or node.text is None:
        return default
    return node.text.strip()


def model(path: Path) -> ET.Element | None:
    return models.get(path.resolve())


def coordinates(path: Path) -> tuple[str, str, str]:
    project = model(path)
    if project is None:
        return "", "", ""
    parent = project.find("m:parent", NS)
    group = node_text(project, "m:groupId") or node_text(parent, "m:groupId")
    version = node_text(project, "m:version") or node_text(parent, "m:version")
    return group, node_text(project, "m:artifactId"), version


def local_parent_path(path: Path) -> Path | None:
    project = model(path)
    if project is None:
        return None
    parent = project.find("m:parent", NS)
    if parent is None:
        return None
    relative_node = parent.find("m:relativePath", NS)
    if relative_node is not None and not (relative_node.text or "").strip():
        return None
    relative_path = node_text(parent, "m:relativePath", "../pom.xml")
    return (path.parent / relative_path).resolve()


def inherits_from(path: Path, artifact_id: str) -> bool:
    current = path.resolve()
    visited: set[Path] = set()
    while current not in visited:
        visited.add(current)
        parent_path = local_parent_path(current)
        if parent_path is None or model(parent_path) is None:
            return False
        if coordinates(parent_path)[1] == artifact_id:
            return True
        current = parent_path
    return False


def dependencies(project: ET.Element) -> list[ET.Element]:
    return project.findall("m:dependencies/m:dependency", NS)


def managed_dependencies(project: ET.Element) -> list[ET.Element]:
    return project.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)


def dependency_key(dependency: ET.Element) -> tuple[str, str, str, str]:
    return (
        node_text(dependency, "m:groupId"),
        node_text(dependency, "m:artifactId"),
        node_text(dependency, "m:type", "jar"),
        node_text(dependency, "m:classifier"),
    )


def validate_unique_dependencies(path: Path, entries: list[ET.Element], label: str) -> None:
    seen: set[tuple[str, str, str, str]] = set()
    for dependency in entries:
        key = dependency_key(dependency)
        if key in seen:
            errors.append(f"{relative(path)}: duplicate {label} dependency {key}")
        seen.add(key)


def validate_modules_and_parent(path: Path, project: ET.Element) -> None:
    seen_modules: set[str] = set()
    for module_node in project.findall("m:modules/m:module", NS):
        module_name = (module_node.text or "").strip()
        if not module_name:
            errors.append(f"{relative(path)}: empty module declaration")
            continue
        if module_name in seen_modules:
            errors.append(f"{relative(path)}: duplicate module {module_name}")
        seen_modules.add(module_name)
        module_pom = path.parent / module_name / "pom.xml"
        if not module_pom.exists():
            errors.append(f"{relative(path)}: missing module {module_name}")

    parent = project.find("m:parent", NS)
    if parent is None:
        return
    parent_path = local_parent_path(path)
    if parent_path is None:
        return
    if model(parent_path) is None:
        errors.append(
            f"{relative(path)}: local parent path does not exist: "
            f"{parent_path.as_posix()}"
        )
        return
    expected = (
        node_text(parent, "m:groupId"),
        node_text(parent, "m:artifactId"),
        node_text(parent, "m:version"),
    )
    actual = coordinates(parent_path)
    if actual != expected:
        errors.append(
            f"{relative(path)}: parent coordinate mismatch: "
            f"declared={expected}, local={actual}"
        )


def validate_compiler_overrides(path: Path, project: ET.Element) -> None:
    if path == (ROOT / "pom.xml").resolve():
        return
    for plugin in project.findall(".//m:plugin", NS):
        if node_text(plugin, "m:artifactId") != "maven-compiler-plugin":
            continue
        configuration = plugin.find("m:configuration", NS)
        if configuration is None:
            continue
        for setting in ("source", "target", "release"):
            if configuration.find(f"m:{setting}", NS) is not None:
                errors.append(
                    f"{relative(path)}: child POM overrides compiler {setting}; "
                    "inherit the repository Java baseline"
                )


def validate_boot_app(path: Path, project: ET.Element) -> None:
    source_root = path.parent / "src" / "main" / "java"
    if not source_root.exists():
        return
    has_application = any(
        "@SpringBootApplication" in java_file.read_text(encoding="utf-8", errors="ignore")
        for java_file in source_root.rglob("*.java")
    )
    if not has_application:
        return
    plugins = {
        node_text(plugin, "m:artifactId")
        for plugin in project.findall("m:build/m:plugins/m:plugin", NS)
    }
    if "spring-boot-maven-plugin" not in plugins:
        warnings.append(
            f"{relative(path)}: contains @SpringBootApplication but does not activate "
            "spring-boot-maven-plugin; the jar will not be repackaged as an executable Boot jar"
        )


def main() -> int:
    pom_paths = sorted(ROOT.rglob("pom.xml"))
    for path in pom_paths:
        try:
            models[path.resolve()] = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as exc:
            errors.append(f"{relative(path)}: XML parse failed: {exc}")

    valid_paths = sorted(models)
    artifact_locations: dict[str, list[Path]] = defaultdict(list)
    for path in valid_paths:
        artifact_locations[coordinates(path)[1]].append(path)

    for artifact_id, locations in sorted(artifact_locations.items()):
        if artifact_id and len(locations) > 1:
            joined = ", ".join(relative(path) for path in locations)
            errors.append(f"duplicate artifactId {artifact_id}: {joined}")

    for path in valid_paths:
        project = model(path)
        assert project is not None
        validate_modules_and_parent(path, project)
        validate_unique_dependencies(path, dependencies(project), "direct")
        validate_unique_dependencies(path, managed_dependencies(project), "managed")
        validate_compiler_overrides(path, project)
        validate_boot_app(path, project)

    root_pom = (ROOT / "pom.xml").resolve()
    root_model = model(root_pom)
    if root_model is not None:
        if dependencies(root_model):
            errors.append("pom.xml: root POM must not declare inherited direct dependencies")
        allowed_application_artifacts = {
            "client", "domain", "app", "adapter", "infrastructure"
        }
        for dependency in managed_dependencies(root_model):
            if node_text(dependency, "m:groupId") != PROJECT_GROUP:
                continue
            artifact_id = node_text(dependency, "m:artifactId")
            if artifact_id not in allowed_application_artifacts:
                errors.append(
                    f"pom.xml: root dependencyManagement contains component artifact "
                    f"{artifact_id}; manage it in component/pom.xml"
                )
            if not node_text(dependency, "m:version"):
                errors.append(
                    f"pom.xml: internal managed dependency {artifact_id} has no version"
                )

    component_model = model(COMPONENT_PARENT.resolve())
    component_managed: set[str] = set()
    if component_model is not None:
        for dependency in managed_dependencies(component_model):
            if node_text(dependency, "m:groupId") != PROJECT_GROUP:
                continue
            artifact_id = node_text(dependency, "m:artifactId")
            component_managed.add(artifact_id)
            if node_text(dependency, "m:version") != PROJECT_VERSION:
                errors.append(
                    f"component/pom.xml: internal managed dependency {artifact_id} "
                    f"must use {PROJECT_VERSION}"
                )
            targets = artifact_locations.get(artifact_id, [])
            if not targets or not any(path.is_relative_to(COMPONENT_ROOT) for path in targets):
                errors.append(
                    f"component/pom.xml: managed component artifact does not exist: {artifact_id}"
                )

        declared_modules = {
            (node.text or "").strip()
            for node in component_model.findall("m:modules/m:module", NS)
        }
        component_directories = {
            path.parent.name for path in COMPONENT_ROOT.glob("*-component/pom.xml")
        }
        for missing in sorted(component_directories - declared_modules):
            errors.append(f"component/pom.xml: component module is not aggregated: {missing}")

    component_roots = sorted(COMPONENT_ROOT.glob("*-component/pom.xml"))
    for path in component_roots:
        project = model(path.resolve())
        if project is None:
            continue
        parent = project.find("m:parent", NS)
        if node_text(parent, "m:artifactId") != "component":
            errors.append(
                f"{relative(path)}: component root must inherit component/pom.xml"
            )

    component_artifacts = {
        artifact_id
        for artifact_id, locations in artifact_locations.items()
        if artifact_id
        and any(path.is_relative_to(COMPONENT_ROOT) for path in locations)
    }
    missing_management_consumers: dict[str, set[str]] = defaultdict(set)
    for path in valid_paths:
        if not path.is_relative_to(COMPONENT_ROOT):
            continue
        project = model(path)
        assert project is not None
        for dependency in managed_dependencies(project):
            if (
                node_text(dependency, "m:artifactId") == "component-bom"
                and node_text(dependency, "m:scope") == "import"
            ):
                errors.append(
                    f"{relative(path)}: component source POM must not import component-bom"
                )

        for dependency in dependencies(project):
            if node_text(dependency, "m:groupId") != PROJECT_GROUP:
                continue
            artifact_id = node_text(dependency, "m:artifactId")
            if artifact_id not in component_artifacts:
                continue
            if artifact_id not in component_managed:
                missing_management_consumers[artifact_id].add(relative(path))
                continue
            if inherits_from(path, "component") and node_text(dependency, "m:version"):
                errors.append(
                    f"{relative(path)}: redundant version on managed internal dependency "
                    f"{artifact_id}"
                )

    for artifact_id, consumers in sorted(missing_management_consumers.items()):
        joined = ", ".join(sorted(consumers))
        errors.append(
            f"component/pom.xml: missing internal dependencyManagement entry "
            f"{artifact_id}; used by {joined}"
        )

    bom_model = model(COMPONENT_BOM.resolve())
    bom_managed: set[str] = set()
    if bom_model is not None:
        if dependencies(bom_model):
            errors.append("component-bom: must not declare direct dependencies")
        for dependency in managed_dependencies(bom_model):
            artifact_id = node_text(dependency, "m:artifactId")
            bom_managed.add(artifact_id)
            if node_text(dependency, "m:groupId") != PROJECT_GROUP:
                errors.append(
                    f"component-bom: public entry must use groupId {PROJECT_GROUP}: "
                    f"{artifact_id}"
                )
            if node_text(dependency, "m:version") != PROJECT_VERSION:
                errors.append(
                    f"component-bom: {artifact_id} must use {PROJECT_VERSION}"
                )
            targets = artifact_locations.get(artifact_id, [])
            if not targets or not any(path.is_relative_to(COMPONENT_ROOT) for path in targets):
                errors.append(f"component-bom: published artifact does not exist: {artifact_id}")
            if "demo" in artifact_id or "e2e" in artifact_id:
                errors.append(
                    f"component-bom: demo/E2E artifact must not be published: {artifact_id}"
                )

    print(f"POM files checked: {len(pom_paths)}")
    print(f"Component internal managed artifacts: {len(component_managed)}")
    print(f"Component BOM published artifacts: {len(bom_managed)}")
    print(f"Errors: {len(errors)}")
    for error in errors:
        print(f"ERROR: {error}")
    print(f"Warnings: {len(warnings)}")
    for warning in warnings:
        print(f"WARN: {warning}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
