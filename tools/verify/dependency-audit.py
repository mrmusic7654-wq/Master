#!/usr/bin/env python3
"""Offline compile-classpath audit for Master Control.

Why this exists
---------------
`structure-check.py` verifies *project-internal* imports against the declared
module graph. It cannot see whether an **external** artifact that a file imports
is actually on that module's compile classpath — and neither can a syntax-only
Kotlin parse. Getting that wrong is a hard compile error in Gradle
("cannot access class …", "unresolved reference"), which is painful to discover
only in CI.

This script models the part of Gradle's dependency semantics that decides what a
module may import:

* `api` dependencies are visible to the module **and** to its consumers;
* `implementation` dependencies are visible to the module only;
* a `project(":x")` edge therefore exposes `:x`'s own packages plus the
  transitive closure of `:x`'s `api` edges — nothing from `:x`'s
  `implementation` edges;
* `ksp`/`annotationProcessor`/`runtimeOnly` are not compile classpath;
* test source sets additionally see `testImplementation` /
  `androidTestImplementation`.

For external artifacts it uses a hand-written table of the packages each catalog
alias puts on the compile classpath, including that artifact's own `api`-scoped
transitive packages (for example `material3` brings `androidx.compose.foundation`
and the core icon set, but **not** `androidx.compose.animation`).

The rule this enforces is Gradle's own advice: *declare what you use directly*.
Relying on a transitive artifact for a direct import compiles until the day the
intermediate dependency changes.

Usage
-----
    python3 tools/verify/dependency-audit.py            # exit 1 on any finding
    python3 tools/verify/dependency-audit.py --verbose  # show resolved classpath
"""

from __future__ import annotations

import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# Packages that come from the JDK or the Android platform, never from a
# dependency. `javax.inject` is deliberately absent: it is a real artifact.
PLATFORM_PREFIXES = (
    "android.", "dalvik.", "java.", "kotlin.", "org.json.", "org.w3c.", "org.xml.",
    "javax.crypto.", "javax.net.", "javax.security.", "javax.sql.", "javax.xml.",
    "javax.annotation.", "javax.imageio.", "javax.naming.", "javax.script.",
    "androidx.annotation.",
)
PROJECT_PACKAGE = "com.mastercontrol.app"

# Compile-classpath configurations, and the test-only additions.
MAIN_CONFIGS = ("api", "implementation", "compileOnly", "debugImplementation",
                "releaseImplementation", "debugCompileOnly")
TEST_CONFIGS = ("testImplementation", "testCompileOnly", "testApi")
ANDROID_TEST_CONFIGS = ("androidTestImplementation", "androidTestApi",
                        "debugImplementation", "debugAndroidTestImplementation")

# alias (dotted, as written in build files) -> packages it puts on the compile
# classpath, including its own api-scoped transitive packages.
PROVIDES: dict[str, tuple[str, ...]] = {
    "libs.androidx.core.ktx": ("androidx.core.",),
    "libs.androidx.activity.compose": (
        "androidx.activity.", "androidx.compose.runtime.", "androidx.compose.ui.",
        "androidx.core.", "androidx.lifecycle.", "androidx.savedstate.",
    ),
    "libs.androidx.lifecycle.runtime.ktx": ("androidx.lifecycle.", "androidx.core."),
    "libs.androidx.lifecycle.runtime.compose": (
        "androidx.lifecycle.runtime.compose.", "androidx.lifecycle.", "androidx.compose.runtime.",
    ),
    "libs.androidx.lifecycle.viewmodel.compose": (
        "androidx.lifecycle.viewmodel.compose.", "androidx.lifecycle.viewmodel.",
        "androidx.lifecycle.", "androidx.compose.runtime.", "androidx.compose.ui.",
    ),
    "libs.androidx.lifecycle.process": ("androidx.lifecycle.",),
    "libs.androidx.compose.bom": (),
    "libs.androidx.compose.ui": ("androidx.compose.ui.",),
    "libs.androidx.compose.ui.graphics": ("androidx.compose.ui.graphics.", "androidx.compose.ui."),
    "libs.androidx.compose.ui.tooling": ("androidx.compose.ui.tooling.",),
    "libs.androidx.compose.ui.tooling.preview": ("androidx.compose.ui.tooling.preview.",),
    "libs.androidx.compose.ui.test.junit4": ("androidx.compose.ui.test.",),
    "libs.androidx.compose.ui.test.manifest": (),
    "libs.androidx.compose.material3": (
        "androidx.compose.material3.", "androidx.compose.material.icons.",
        "androidx.compose.foundation.", "androidx.compose.ui.", "androidx.compose.runtime.",
        "androidx.compose.animation.core.",
    ),
    "libs.androidx.compose.material.icons.extended": ("androidx.compose.material.icons.",),
    "libs.androidx.compose.foundation": (
        "androidx.compose.foundation.", "androidx.compose.ui.", "androidx.compose.runtime.",
        "androidx.compose.animation.core.",
    ),
    "libs.androidx.compose.runtime": ("androidx.compose.runtime.",),
    "libs.androidx.compose.animation": (
        "androidx.compose.animation.", "androidx.compose.animation.core.",
        "androidx.compose.runtime.", "androidx.compose.ui.",
    ),
    "libs.androidx.compose.animation.core": ("androidx.compose.animation.core.",),
    "libs.androidx.navigation.compose": (
        "androidx.navigation.compose.", "androidx.navigation.", "androidx.compose.runtime.",
        "androidx.compose.ui.", "androidx.lifecycle.",
    ),
    "libs.hilt.android": ("dagger.", "javax.inject."),
    "libs.hilt.compiler": (),
    "libs.hilt.navigation.compose": ("androidx.hilt.navigation.compose.", "androidx.hilt."),
    "libs.hilt.work": ("androidx.hilt.work.", "androidx.hilt.", "dagger.", "androidx.work."),
    "libs.hilt.work.compiler": (),
    "libs.room.runtime": ("androidx.room.", "androidx.sqlite."),
    "libs.room.ktx": ("androidx.room.", "androidx.sqlite.", "kotlinx.coroutines."),
    "libs.room.compiler": (),
    "libs.room.testing": ("androidx.room.testing.", "androidx.room.migration.", "androidx.room."),
    "libs.datastore.preferences": ("androidx.datastore.",),
    "libs.work.runtime.ktx": ("androidx.work.", "kotlinx.coroutines.", "androidx.core."),
    "libs.kotlinx.coroutines.core": ("kotlinx.coroutines.",),
    "libs.kotlinx.coroutines.android": ("kotlinx.coroutines.",),
    "libs.kotlinx.coroutines.test": ("kotlinx.coroutines.",),
    "libs.kotlinx.serialization.json": ("kotlinx.serialization.",),
    "libs.coil.compose": (
        "coil.", "androidx.compose.runtime.", "androidx.compose.ui.", "kotlinx.coroutines.",
    ),
    "libs.androidx.biometric": (
        "androidx.biometric.", "androidx.fragment.", "androidx.appcompat.", "androidx.core.",
    ),
    "libs.androidx.documentfile": ("androidx.documentfile.",),
    "libs.javax.inject": ("javax.inject.",),
    "libs.junit": ("org.junit.", "junit.", "org.hamcrest."),
    "libs.androidx.test.ext.junit": ("androidx.test.ext.",),
    "libs.androidx.test.core": ("androidx.test.",),
    "libs.androidx.test.espresso.core": ("androidx.test.espresso.",),
}

# import prefix -> catalog alias to suggest when it is missing.
SUGGEST: dict[str, str] = {
    "androidx.compose.animation.core.": "libs.androidx.compose.animation.core",
    "androidx.compose.animation.": "libs.androidx.compose.animation",
    "androidx.compose.material3.": "libs.androidx.compose.material3",
    "androidx.compose.material.icons.": "libs.androidx.compose.material.icons.extended",
    "androidx.compose.foundation.": "libs.androidx.compose.foundation",
    "androidx.compose.runtime.": "libs.androidx.compose.runtime",
    "androidx.compose.ui.test.": "libs.androidx.compose.ui.test.junit4",
    "androidx.compose.ui.tooling.preview": "libs.androidx.compose.ui.tooling.preview",
    "androidx.compose.ui.tooling.": "libs.androidx.compose.ui.tooling",
    "androidx.compose.ui.graphics.": "libs.androidx.compose.ui.graphics",
    "androidx.compose.ui.": "libs.androidx.compose.ui",
    "androidx.activity.compose.": "libs.androidx.activity.compose",
    "androidx.lifecycle.viewmodel.compose.": "libs.androidx.lifecycle.viewmodel.compose",
    "androidx.lifecycle.runtime.compose.": "libs.androidx.lifecycle.runtime.compose",
    "androidx.lifecycle.": "libs.androidx.lifecycle.runtime.ktx",
    "androidx.hilt.navigation.compose.": "libs.hilt.navigation.compose",
    "androidx.hilt.work.": "libs.hilt.work",
    "androidx.navigation.": "libs.androidx.navigation.compose",
    "androidx.room.": "libs.room.ktx",
    "androidx.sqlite.": "libs.room.ktx",
    "androidx.datastore.": "libs.datastore.preferences",
    "androidx.work.": "libs.work.runtime.ktx",
    "androidx.biometric.": "libs.androidx.biometric",
    "androidx.fragment.": "libs.androidx.biometric",
    "androidx.documentfile.": "libs.androidx.documentfile",
    "androidx.core.": "libs.androidx.core.ktx",
    "coil.": "libs.coil.compose",
    "dagger.": "libs.hilt.android",
    "javax.inject.": "libs.javax.inject",
    "kotlinx.coroutines.": "libs.kotlinx.coroutines.android",
    "kotlinx.serialization.": "libs.kotlinx.serialization.json",
    "org.junit.": "libs.junit",
    "androidx.test.ext.": "libs.androidx.test.ext.junit",
    "androidx.test.espresso.": "libs.androidx.test.espresso.core",
}

INCLUDE_RE = re.compile(r'^\s*include\(\s*"([^"]+)"')
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)")
IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z0-9_.]+)")
# api("x") / implementation("x") / testImplementation(libs.y) / api(project(":z"))
DEP_RE = re.compile(
    r"^\s*(api|implementation|compileOnly|ksp|annotationProcessor|runtimeOnly|"
    r"testImplementation|testCompileOnly|testApi|androidTestImplementation|androidTestApi|"
    r"debugImplementation|releaseImplementation|debugCompileOnly|debugAndroidTestImplementation)"
    r"\s*\(\s*(?:platform\(\s*)?(libs\.[a-z0-9.]+|project\(\s*\"([^\"]+)\")",
)
NAMESPACE_RE = re.compile(r'^\s*namespace\s*=\s*"([^"]+)"')


def modules() -> list[str]:
    out = []
    with open(os.path.join(ROOT, "settings.gradle.kts"), encoding="utf-8") as handle:
        for line in handle:
            match = INCLUDE_RE.match(line)
            if match:
                out.append(match.group(1).lstrip(":"))
    return out


def module_dir(module: str) -> str:
    return os.path.join(ROOT, module.replace(":", os.sep))


def kotlin_files(module: str, source_set: str) -> list[str]:
    root = os.path.join(module_dir(module), "src", source_set)
    found = []
    for dirpath, _dirs, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(".kt"):
                found.append(os.path.join(dirpath, name))
    return sorted(found)


def declared_dependencies(module: str) -> dict[str, list[str]]:
    """config -> list of `libs.alias` or `:project` targets."""
    path = os.path.join(module_dir(module), "build.gradle.kts")
    configs: dict[str, list[str]] = defaultdict(list)
    if not os.path.exists(path):
        return configs
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            match = DEP_RE.match(line)
            if not match:
                continue
            config, alias, project = match.group(1), match.group(2), match.group(3)
            configs[config].append(project if project else alias)
    return configs


def source_packages(module: str) -> set[str]:
    """Every package declared in the module's main sources, plus its namespace."""
    packages: set[str] = set()
    for source_set in ("main",):
        for path in kotlin_files(module, source_set):
            with open(path, encoding="utf-8", errors="replace") as handle:
                for line in handle:
                    match = PACKAGE_RE.match(line)
                    if match:
                        packages.add(match.group(1) + ".")
                        break
    build = os.path.join(module_dir(module), "build.gradle.kts")
    if os.path.exists(build):
        with open(build, encoding="utf-8") as handle:
            match = NAMESPACE_RE.search(handle.read())
        if match:
            packages.add(match.group(1) + ".")
    return packages


def api_closure(module: str, deps: dict[str, dict[str, list[str]]],
                cache: dict[str, set[str]]) -> set[str]:
    """Packages a module exposes to its consumers: own sources + api edges."""
    if module in cache:
        return cache[module]
    exposed = set(source_packages(module))
    for target in deps.get(module, {}).get("api", []):
        if target.startswith(":"):
            exposed |= api_closure(target.lstrip(":"), deps, cache)
        else:
            exposed |= set(PROVIDES.get(target, ()))
    cache[module] = exposed
    return exposed


def compile_classpath(module: str, deps: dict[str, dict[str, list[str]]],
                      configs: dict[str, list[str]],
                      cache: dict[str, set[str]],
                      extra_configs: tuple[str, ...] = ()) -> set[str]:
    """Everything `module` may import in the given source set."""
    visible: set[str] = set()
    names = list(MAIN_CONFIGS) + list(extra_configs)
    for config in names:
        for target in configs.get(config, []):
            if target.startswith(":"):
                dep_module = target.lstrip(":")
                visible |= api_closure(dep_module, deps, cache)
            else:
                visible |= set(PROVIDES.get(target, ()))
    return visible


def suggest(imp: str) -> str:
    for prefix, alias in sorted(SUGGEST.items(), key=lambda kv: -len(kv[0])):
        if imp.startswith(prefix):
            return alias
    return "a catalog alias that provides it"


def audit(verbose: bool) -> int:
    module_list = modules()
    deps = {m: declared_dependencies(m) for m in module_list}
    cache: dict[str, set[str]] = {}
    findings: list[str] = []

    unknown_aliases = set()
    for module, configs in deps.items():
        for targets in configs.values():
            for target in targets:
                if target.startswith("libs.") and target not in PROVIDES:
                    unknown_aliases.add(target)
    for alias in sorted(unknown_aliases):
        findings.append(f"{alias}: catalog alias is not in this audit's PROVIDES table "
                        f"(add its exported packages so the audit stays honest)")

    for module in module_list:
        configs = deps[module]
        main_cp = compile_classpath(module, deps, configs, cache)
        test_cp = main_cp | compile_classpath(module, deps, configs, cache, TEST_CONFIGS)
        android_test_cp = main_cp | compile_classpath(
            module, deps, configs, cache, TEST_CONFIGS + ANDROID_TEST_CONFIGS)
        if verbose:
            print(f"\n{module}: {len(main_cp)} package prefixes on the main compile classpath")

        for source_set, classpath in (("main", main_cp), ("test", test_cp),
                                      ("testDebug", test_cp), ("androidTest", android_test_cp),
                                      ("debug", main_cp), ("release", main_cp)):
            for path in kotlin_files(module, source_set):
                relative = os.path.relpath(path, ROOT)
                with open(path, encoding="utf-8", errors="replace") as handle:
                    for line_number, line in enumerate(handle, start=1):
                        match = IMPORT_RE.match(line)
                        if not match:
                            continue
                        imp = match.group(1)
                        if imp.startswith(PROJECT_PACKAGE + "."):
                            continue  # structure-check.py owns project-internal imports
                        if any(imp.startswith(p) for p in PLATFORM_PREFIXES):
                            continue
                        if any(imp.startswith(p) for p in classpath):
                            continue
                        findings.append(
                            f"{relative}:{line_number}: `{imp}` is not on the "
                            f"{source_set} compile classpath of :{module} "
                            f"(add {suggest(imp)} to its build.gradle.kts)")

    print()
    print("═" * 56)
    print(f" dependency-audit: {len(module_list)} modules, "
          f"{len(PROVIDES)} catalog aliases modelled")
    print("═" * 56)
    for finding in findings:
        print(f"  ✗ {finding}")
    print(f"  → {len(findings)} problem(s)")
    return 1 if findings else 0


def main() -> int:
    return audit("--verbose" in sys.argv)


if __name__ == "__main__":
    sys.exit(main())
