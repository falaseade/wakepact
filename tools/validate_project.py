#!/usr/bin/env python3
"""
validate_project.py — static sanity checks for android-app-factory projects.

Catches the most common "won't compile / crashes at launch" mistakes without
needing Gradle or the Android SDK, so it works in restricted sandboxes.

Usage:  python3 validate_project.py <project-root> [--allow-prerelease]
Exit:   0 = no errors (warnings allowed), 1 = errors found, 2 = bad invocation
"""
import re
import sys
import tomllib
from pathlib import Path

ERRORS: list[str] = []
WARNINGS: list[str] = []


def err(msg: str) -> None:
    ERRORS.append(msg)


def warn(msg: str) -> None:
    WARNINGS.append(msg)


def norm(key: str) -> str:
    """Catalog keys map to accessors with -/_ becoming '.'; normalise both sides."""
    return key.replace("-", ".").replace("_", ".")


def read(p: Path) -> str:
    try:
        return p.read_text(encoding="utf-8", errors="replace")
    except OSError as e:
        err(f"Cannot read {p}: {e}")
        return ""


def find_kts_files(root: Path) -> list[Path]:
    return [p for p in root.rglob("*.gradle.kts") if "build" not in p.parts]


def check_catalog(root: Path, kts_files: list[Path], allow_pre: bool) -> dict:
    cat_path = root / "gradle" / "libs.versions.toml"
    if not cat_path.exists():
        err("gradle/libs.versions.toml missing — the factory requires a version catalog.")
        return {}
    try:
        cat = tomllib.loads(read(cat_path))
    except tomllib.TOMLDecodeError as e:
        err(f"libs.versions.toml does not parse: {e}")
        return {}

    versions = cat.get("versions", {})
    libraries = cat.get("libraries", {})
    plugins = cat.get("plugins", {})

    # Placeholders left behind
    raw = read(cat_path)
    for m in re.finditer(r"\{\{[A-Z_]+\}\}", raw):
        err(f"Catalog still contains template placeholder {m.group(0)} — Phase 3 incomplete.")

    # Pre-release and dynamic versions
    for name, v in versions.items():
        if not isinstance(v, str):
            continue
        if "+" in v or "[" in v or v.startswith("latest"):
            err(f"versions.{name} = '{v}' is dynamic — exact versions only (matrix rule 11; repeatability).")
        elif not allow_pre and re.search(r"(alpha|beta|rc|snapshot|dev|eap)", v, re.I):
            err(f"versions.{name} = '{v}' is pre-release; stable-only is the factory default.")

    # Compose artifacts must not pin versions (BOM governs)
    for name, spec in libraries.items():
        if isinstance(spec, dict) and str(spec.get("group", "")).startswith("androidx.compose.") \
                and ("version" in spec or "version.ref" in spec or "version" in {k.split(".")[0] for k in spec}):
            if spec.get("group") != "androidx.compose":  # the BOM itself is allowed a version
                warn(f"libraries.{name}: androidx.compose.* artifact pins a version — let the BOM govern it.")

    # Kotlin <-> compose plugin coherence
    kotlin_v = versions.get("kotlin")
    comp = plugins.get("kotlin-compose") or plugins.get("compose-compiler") or {}
    if isinstance(comp, dict) and kotlin_v:
        ref = comp.get("version", {}).get("ref") if isinstance(comp.get("version"), dict) else None
        ver = comp.get("version") if isinstance(comp.get("version"), str) else (versions.get(ref) if ref else None)
        if ver and ver != kotlin_v:
            err(f"Compose plugin version ({ver}) != Kotlin ({kotlin_v}) — they must match (matrix rule 3).")

    # Old-scheme KSP prefix rule
    ksp_v = versions.get("ksp", "")
    if isinstance(ksp_v, str) and kotlin_v and "-" in ksp_v and not ksp_v.startswith(str(kotlin_v)):
        err(f"KSP '{ksp_v}' uses the <kotlin>-x scheme but prefix != Kotlin {kotlin_v} (matrix rule 4).")

    # Usage scan across .kts files
    all_kts = "\n".join(read(p) for p in kts_files)
    used_libs = {norm(m) for m in re.findall(r"\blibs\.((?!plugins\b)[A-Za-z0-9.]+)", all_kts)}
    used_plugins = {norm(m) for m in re.findall(r"\blibs\.plugins\.([A-Za-z0-9.]+)", all_kts)}

    def_libs = {norm(k): k for k in libraries}
    def_plugins = {norm(k): k for k in plugins}

    def covered(used: str, defined: dict) -> bool:
        # accessor may be a prefix path of a longer key only if exact match exists; require exact
        return used in defined

    for u in sorted(used_libs):
        if not covered(u, def_libs) and u not in ("versions",):
            err(f"Build files reference libs.{u} but no matching [libraries] entry exists.")
    for u in sorted(used_plugins):
        if not covered(u, def_plugins):
            err(f"Build files reference libs.plugins.{u} but no matching [plugins] entry exists.")
    for d in sorted(def_libs):
        if d not in used_libs:
            warn(f"Catalog library '{def_libs[d]}' is defined but never used — delete it (unused deps are risk).")
    for d in sorted(def_plugins):
        if d not in used_plugins:
            warn(f"Catalog plugin '{def_plugins[d]}' is defined but never used.")

    return {"libraries": libraries, "used_libs": used_libs, "kts": all_kts}


def check_structure(root: Path) -> list[Path]:
    settings = root / "settings.gradle.kts"
    modules: list[Path] = []
    if not settings.exists():
        err("settings.gradle.kts missing.")
        return modules
    s = read(settings)
    if "google()" not in s:
        err("settings.gradle.kts: dependencyResolutionManagement repositories must include google().")
    if "mavenCentral()" not in s:
        err("settings.gradle.kts: repositories must include mavenCentral().")
    for m in re.findall(r'include\("([^"]+)"\)', s):
        mod_dir = root / m.lstrip(":").replace(":", "/")
        modules.append(mod_dir)
        if not (mod_dir / "build.gradle.kts").exists():
            err(f"settings includes '{m}' but {mod_dir}/build.gradle.kts does not exist.")
    if not modules:
        err("settings.gradle.kts includes no modules.")
    return modules


def check_kotlin_packages(root: Path) -> None:
    for kt in root.rglob("*.kt"):
        if "build" in kt.parts:
            continue
        text = read(kt)
        m = re.search(r"^\s*package\s+([\w.]+)", text, re.M)
        if not m:
            warn(f"{kt.relative_to(root)}: no package declaration.")
            continue
        pkg_path = m.group(1).replace(".", "/")
        posix = kt.parent.as_posix()
        if not posix.endswith(pkg_path):
            err(f"{kt.relative_to(root)}: package '{m.group(1)}' does not match its directory.")


def check_manifest(root: Path, app_dir: Path, cat: dict) -> None:
    manifest = app_dir / "src" / "main" / "AndroidManifest.xml"
    if not manifest.exists():
        err(f"{manifest.relative_to(root)} missing.")
        return
    mx = read(manifest)

    # launcher exported
    for act in re.finditer(r"<activity\b[^>]*>(?:(?!</activity>).)*?LAUNCHER", mx, re.S):
        if 'android:exported="true"' not in act.group(0):
            err("Launcher activity must declare android:exported=\"true\" (API 31+ hard requirement).")
    if "LAUNCHER" not in mx:
        err("Manifest declares no launcher activity.")

    # application name class exists + Hilt pairing
    app_name = re.search(r'<application[^>]*android:name="([^"]+)"', mx)
    src = app_dir / "src" / "main"
    sources = "\n".join(read(p) for p in src.rglob("*.kt"))
    if app_name:
        cls = app_name.group(1).split(".")[-1]
        if not re.search(rf"\bclass\s+{re.escape(cls)}\b", sources):
            err(f"Manifest names application class '{cls}' but no such class exists in sources.")
        if "@HiltAndroidApp" in sources and f"class {cls}" in sources and "@HiltAndroidApp" not in sources.split(f"class {cls}")[0].rsplit("\n", 3)[-1] + "@":
            pass  # positional check too brittle; covered below
    if "@AndroidEntryPoint" in sources and "@HiltAndroidApp" not in sources:
        err("@AndroidEntryPoint used but no @HiltAndroidApp Application class found — crashes at launch.")
    if "@HiltAndroidApp" in sources and not app_name:
        err("@HiltAndroidApp class exists but manifest <application> has no android:name — Hilt won't initialise.")

    # networking permission
    used = cat.get("used_libs", set())
    if any(k.startswith(("retrofit", "okhttp", "ktor")) for k in used) and "android.permission.INTERNET" not in mx:
        err("Networking libraries present but INTERNET permission missing from the manifest.")

    if "screenOrientation" in mx:
        warn("Manifest locks screenOrientation — breaks foldables/tablets; remove it.")

    # compose buildFeatures
    app_build = read(app_dir / "build.gradle.kts")
    if "compose.bom" in cat.get("kts", "") and "compose = true" not in app_build.replace(" ", " "):
        err("Compose dependencies present but buildFeatures { compose = true } missing in app build file.")


def check_tests(app_dir: Path, root: Path) -> None:
    main = app_dir / "src" / "main"
    test = app_dir / "src" / "test"
    vms = list(main.rglob("*ViewModel.kt"))
    for vm in vms:
        expected = vm.stem + "Test.kt"
        if not any(t.name == expected for t in test.rglob("*.kt")):
            warn(f"No unit test found for {vm.name} (expected {expected}).")
    for t in test.rglob("*.kt"):
        body = read(t)
        if "Thread.sleep" in body:
            err(f"{t.relative_to(root)}: Thread.sleep in tests — use the coroutine test recipe (Phase 7).")
        if re.search(r"import\s+android\.(?!annotation)", body):
            warn(f"{t.relative_to(root)}: android.* import in a JVM unit test — likely won't run.")


def check_logging(app_dir: Path, root: Path, cat: dict) -> None:
    src = app_dir / "src" / "main"
    sources = {p: read(p) for p in src.rglob("*.kt")}
    all_src = "\n".join(sources.values())
    timber_adopted = any(k.startswith("timber") for k in cat.get("used_libs", set()))

    for p, body in sources.items():
        if re.search(r"import\s+android\.util\.Log\b|\bLog\.[edwiv]\(", body):
            warn(f"{p.relative_to(root)}: android.util.Log used — factory apps log via Timber "
                 f"(observability reference).")
        for i, line in enumerate(body.splitlines(), 1):
            if re.search(r"\b(Timber|Log)\.\w+\(", line) and \
                    re.search(r"password|secret|api[_-]?key|bearer|token", line, re.I):
                warn(f"{p.relative_to(root)}:{i}: possible secret/PII in a log statement — "
                     f"review board must confirm this is safe.")

    if timber_adopted and "Timber.plant" not in all_src:
        warn("Timber is a dependency but Timber.plant(...) is never called — logs will be silent.")


def check_misc(root: Path) -> None:
    if not (root / ".gitignore").exists():
        err(".gitignore missing — local.properties/build dirs would get committed.")
    elif "local.properties" not in read(root / ".gitignore"):
        err(".gitignore does not exclude local.properties (may contain SDK paths/keys).")
    wf = root / ".github" / "workflows"
    if not (wf.exists() and any(wf.glob("*.yml"))):
        warn("No GitHub Actions workflow — CI is the build verification; add it from the template.")
    if not (root / "gradle" / "wrapper" / "gradle-wrapper.jar").exists():
        warn("gradle-wrapper.jar absent — fetch via GitHub mirror (Phase 4 §Wrapper) or rely on CI provisioning.")
    if not (root / "docs").exists():
        warn("docs/ missing — the factory's phase artifacts (brief, ADRs, roadmap...) belong there.")
    if not (root / "CHANGELOG.md").exists():
        warn("CHANGELOG.md missing — every factory app keeps one (repeatability reference).")
    if not (root / ".github" / "dependabot.yml").exists():
        warn("No dependabot.yml — grouped dependency updates keep the app maintainable (template provided).")
    if not (root / "tools" / "validate_project.py").exists():
        warn("Validator not copied into tools/ — the health check should travel with the code.")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    root = Path(sys.argv[1]).resolve()
    allow_pre = "--allow-prerelease" in sys.argv
    if not root.is_dir():
        print(f"Not a directory: {root}")
        return 2

    kts = find_kts_files(root)
    cat = check_catalog(root, kts, allow_pre)
    modules = check_structure(root)
    check_kotlin_packages(root)
    app_dir = next((m for m in modules if m.name == "app"), root / "app")
    if app_dir.exists():
        check_manifest(root, app_dir, cat)
        check_tests(app_dir, root)
        check_logging(app_dir, root, cat)
    check_misc(root)

    print(f"\n=== validate_project: {root.name} ===")
    for e in ERRORS:
        print(f"  ERROR   {e}")
    for w in WARNINGS:
        print(f"  warning {w}")
    print(f"--- {len(ERRORS)} error(s), {len(WARNINGS)} warning(s) ---")
    return 1 if ERRORS else 0


if __name__ == "__main__":
    sys.exit(main())
