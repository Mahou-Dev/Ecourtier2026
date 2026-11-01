#!/usr/bin/env python3
import json
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("/home/mahou-dev/Documents/Ecourtier2026")


def count_lines(p: Path) -> int:
    try:
        with p.open("r", encoding="utf-8", errors="replace") as f:
            return sum(1 for _ in f)
    except Exception:
        return 0


def count_non_blank_non_comment_java_ts(p: Path) -> int:
    lines = p.read_text(encoding="utf-8", errors="replace").splitlines()
    n = 0
    in_block = False
    for raw in lines:
        s = raw.strip()
        if not s:
            continue
        if in_block:
            if "*/" in s:
                in_block = False
            continue
        if s.startswith("/*"):
            if "*/" not in s[2:]:
                in_block = True
            continue
        if s.startswith("//"):
            continue
        n += 1
    return n


def main():
    out = {}

    # Backend Java main (excluding tests)
    java_main_dir = ROOT / "backend/src/main/java"
    java_files_all = sorted(java_main_dir.rglob("*.java"))
    java_files_non_pkg = [p for p in java_files_all if p.name != "package-info.java"]
    pkg_info_files = [p for p in java_files_all if p.name == "package-info.java"]

    entities = []
    rest_controllers = []
    value_vars = []
    getenv_vars = []

    for p in java_files_non_pkg:
        txt = p.read_text(encoding="utf-8", errors="replace")
        rel = str(p.relative_to(ROOT))
        if re.search(r"@Entity\b", txt):
            entities.append(rel)
        if re.search(r"@RestController\b|@Controller\b", txt):
            rest_controllers.append(rel)
        for m in re.finditer(r'@Value\(\s*"([^"]+)"\s*\)', txt):
            value_vars.append({"file": rel, "expr": m.group(1)})
        for m in re.finditer(r'System\.getenv\(\s*"([^"]+)"\s*\)', txt):
            getenv_vars.append({"file": rel, "var": m.group(1)})

    mig_dir = ROOT / "backend/src/main/resources/db/migration"
    migrations = sorted(mig_dir.glob("*.sql"))

    yml_files = sorted((ROOT / "backend/src/main/resources").glob("*.yml"))
    yml_env_vars = []
    for y in yml_files:
        txt = y.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r"\$\{([A-Z0-9_]+)(?::[^}]*)?\}", txt):
            yml_env_vars.append({"file": str(y.relative_to(ROOT)), "var": m.group(1), "raw": m.group(0)})

    java_test_dir = ROOT / "backend/src/test/java"
    java_test_files = sorted(java_test_dir.rglob("*.java"))

    out["backend"] = {
        "java_main_files_total": len(java_files_all),
        "java_main_files_excluding_package_info": len(java_files_non_pkg),
        "package_info_files": len(pkg_info_files),
        "java_main_total_lines": sum(count_lines(p) for p in java_files_non_pkg),
        "java_main_sloc": sum(count_non_blank_non_comment_java_ts(p) for p in java_files_non_pkg),
        "sql_migrations_count": len(migrations),
        "sql_migrations_files": [p.name for p in migrations],
        "sql_migrations_lines": sum(count_lines(p) for p in migrations),
        "yml_config_files": [p.name for p in yml_files],
        "yml_config_lines": sum(count_lines(p) for p in yml_files),
        "entities_count": len(entities),
        "entities": entities,
        "controllers_count": len(rest_controllers),
        "controllers": rest_controllers,
        "test_files_count": len(java_test_files),
        "test_total_lines": sum(count_lines(p) for p in java_test_files),
        "at_value_usages": value_vars,
        "system_getenv_usages": getenv_vars,
        "yml_env_placeholders": yml_env_vars,
    }

    # Frontend src (excluding node_modules, dist, .d.ts)
    fe_src = ROOT / "frontend/src"
    fe_code_files = [
        p for p in sorted(fe_src.rglob("*"))
        if p.is_file() and p.suffix in {".ts", ".tsx", ".css"} and not p.name.endswith(".d.ts")
    ]
    fe_pages = sorted((fe_src / "pages").glob("*.tsx"))
    fe_components = sorted((fe_src / "components").rglob("*.tsx"))
    fe_env_vars = []
    for p in fe_code_files:
        txt = p.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r"import\.meta\.env\.([A-Z0-9_]+)", txt):
            fe_env_vars.append({"file": str(p.relative_to(ROOT)), "var": m.group(1)})

    out["frontend"] = {
        "src_code_files_count": len(fe_code_files),
        "src_total_lines": sum(count_lines(p) for p in fe_code_files),
        "src_sloc": sum(count_non_blank_non_comment_java_ts(p) for p in fe_code_files),
        "pages_count": len(fe_pages),
        "pages": [p.name for p in fe_pages],
        "components_count": len(fe_components),
        "components": [str(p.relative_to(fe_src / "components")) for p in fe_components],
        "import_meta_env_usages": fe_env_vars,
    }

    # Dependencies from pom.xml & package.json
    pom = ET.parse(ROOT / "backend/pom.xml").getroot()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    parent_ver = pom.findtext("m:parent/m:version", default="", namespaces=ns)
    props = {el.tag.split("}")[-1]: el.text for el in pom.findall("m:properties/*", ns)}
    deps = []
    for d in pom.findall("m:dependencies/m:dependency", ns):
        g = d.findtext("m:groupId", default="", namespaces=ns)
        a = d.findtext("m:artifactId", default="", namespaces=ns)
        v = d.findtext("m:version", default="(Spring Boot BOM)", namespaces=ns)
        sc = d.findtext("m:scope", default="compile", namespaces=ns)
        deps.append({"groupId": g, "artifactId": a, "version": v, "scope": sc})

    pkg_json = json.loads((ROOT / "frontend/package.json").read_text(encoding="utf-8"))
    out["dependencies"] = {
        "spring_boot_parent": parent_ver,
        "maven_properties": props,
        "backend_dependencies": deps,
        "frontend_dependencies": pkg_json.get("dependencies", {}),
        "frontend_devDependencies": pkg_json.get("devDependencies", {}),
    }

    (ROOT / "docs/audit/metrics.json").write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Metrics saved -> docs/audit/metrics.json")


if __name__ == "__main__":
    main()
