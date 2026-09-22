# Build

Everything needed to produce a working APK/AAB, including the native TDLib
libraries. There are two independent halves:

* **TDLib (native)** — compiled from `third_party/tdlib` into `libtdjson.so`,
  with the checked-in JNI bridge compiled into `libtdjson_bridge.so`.
  Both are needed on the device, but native libraries are **not** needed to
  compile or unit-test the Kotlin code.
* **Master Control (Kotlin/Gradle)** — the app, its modules and its tests.

---

## 1. Prerequisites

See [`SETUP.md`](SETUP.md) for the full list. In short: JDK 17, Android SDK
platform 35, NDK **26.3.11579264**, CMake 3.22.1, Ninja, plus `perl`, `gperf`,
`php`, `make`, `tar`, `unzip` for the TDLib/OpenSSL build.

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"     # or ANDROID_HOME
java -version                                   # must be 17
```

The Gradle wrapper is committed (`./gradlew`, Gradle 8.9) — do not use a
locally installed Gradle; the wrapper version is what CI uses.

## 2. Build TDLib

```bash
./scripts/build-tdlib.sh --android-sdk-root "$ANDROID_SDK_ROOT"
```

What it does, in order:

1. Verifies the vendored tree is TDLib **1.8.67** (reads
   `third_party/tdlib/CMakeLists.txt`) and that the requested NDK is installed.
2. Builds **OpenSSL 1.1.1w** for each ABI using TDLib's own
   `build-openssl.sh` (the only network access in this step, pinned tag, official
   repository). Pass `--openssl-dir` to reuse a prebuilt OpenSSL and stay fully
   offline.
3. Configures TDLib with `ANDROID_STL=c++_static`, `ANDROID_PLATFORM=android-26`
   (= `minSdk`), `CMAKE_BUILD_TYPE=RelWithDebInfo`, `-DTD_ANDROID_JSON=ON`,
   Ninja generator, pointing `OPENSSL_ROOT_DIR` at the OpenSSL built in step 2.
4. Builds only the **`tdjson`** target — the JSON interface Master Control's
   JNI bridge talks to. No C++ client library is packaged.
5. Strips the TDLib result (`llvm-strip --strip-debug --strip-unneeded`),
   compiles `telegram/src/main/jni/tdjson_bridge.c` with the Android NDK, and
   installs both libraries as `telegram/src/main/jniLibs/<abi>/libtdjson.so`
   and `libtdjson_bridge.so`. If OpenSSL was built as shared libraries,
   `libcrypto.so` and `libssl.so` are copied and stripped into the same directory
   so the loader finds them.
6. Writes `telegram/src/main/jniLibs/BUILD-INFO.txt` recording TDLib version,
   OpenSSL version, NDK version, build date, flags and **sha256** of each `.so`.

### Flags

| Flag | Effect |
| --- | --- |
| `--android-sdk-root <dir>` | SDK location (defaults to `$ANDROID_SDK_ROOT`/`$ANDROID_HOME`) |
| `--include-x86-64` | Also build `x86_64` — needed for emulators; not shipped by default |
| `--abis <list>` | Explicit ABI list, e.g. `--abis arm64-v8a` |
| `--openssl-dir <dir>` | Use a prebuilt OpenSSL instead of downloading/building |
| `--ndk-version <v>` | Override the pinned NDK (not recommended) |
| `--clean` | Delete `build/tdlib` and previous outputs first |
| `-h`, `--help` | Print the script header |

Those are the only accepted arguments — anything else fails fast with the usage
text. Stripping is not configurable: the packaged `.so` is always stripped, and
the unstripped copy used to produce it is deleted after the strip succeeds (keep
it yourself if you want symbols for native crash triage: build once with
`--openssl-dir` and copy `build/tdlib/<abi>/td/libtdjson.so` aside). The JNI
bridge is rebuilt from `telegram/src/main/jni/tdjson_bridge.c` on every run.

Default ABIs are **`arm64-v8a` and `armeabi-v7a`**, matching
`telegram/build.gradle.kts`. `x86_64` is opt-in on both sides:

```bash
./scripts/build-tdlib.sh --include-x86-64
./gradlew :app:assembleDebug -PincludeX86_64=true
```

### Time and caching

A cold TDLib build takes roughly 25–60 minutes depending on the machine; OpenSSL
adds a few minutes per ABI. Intermediates live in `build/tdlib` (git-ignored) —
keep that directory between builds and re-runs are incremental.
`telegram/src/main/jniLibs/` is also git-ignored: binaries are build outputs, not
source.

## 3. Build the app

```bash
./gradlew :app:assembleDebug                 # app-debug.apk (debug-signed)
./gradlew :app:assembleRelease               # unsigned unless keystore.properties exists
./gradlew :app:bundleRelease                 # AAB for Play delivery
```

Outputs land in `app/build/outputs/apk/<variant>/` and `app/build/outputs/bundle/release/`.

`minSdk 26`, `targetSdk 35`, `compileSdk 35` come from the version catalog. The
app version is passed as Gradle properties and defaults to `1.0.0` / `1`: ABI splits are handled
by `abiFilters` in the `telegram` module, so each APK contains only the ABIs you
built.

```bash
./gradlew :app:bundleRelease -PmcVersionName=1.2.0 -PmcVersionCode=12
```

### Signing a release

Create `keystore.properties` (git-ignored) as described in
[`SETUP.md`](SETUP.md#5-signing-release-builds-only). `app/build.gradle.kts`
reads it if present and applies the release signing config; otherwise the release
variant stays unsigned rather than silently using the debug key.

## 4. Tests

```bash
./gradlew test                               # all JVM unit tests
./gradlew :domain:test :core:common:test     # pure-Kotlin, fastest
./gradlew :core:database:testDebugUnitTest   # Room DAO + migration tests
./gradlew :app:testDebugUnitTest             # app-level unit tests
./gradlew lintDebug                          # Android lint, all modules
```

Instrumented tests need a device/emulator with the native libraries present:

```bash
./scripts/build-tdlib.sh --include-x86-64    # for an x86_64 emulator
./gradlew :app:assembleDebug -PincludeX86_64=true
./gradlew connectedDebugAndroidTest
```

Room schema export is configured (`room.schemaLocation` →
`core/database/schemas/`), so the JSON for each schema version is produced at
build time; commit the file for the version you ship. `MigrationChainTest`
(unit test) enforces that the migration chain stays contiguous from version 1 and
that version 1 ships with no destructive fallback. Whenever you bump
`MasterControlDatabase.version`: add the `Migration`, add it to
`MasterControlDatabase.MIGRATIONS`, commit the new schema JSON, and add an
instrumented migration test.

## 5. Verification without Gradle or network

For environments without Android SDK or repository access (code review, offline
CI, air-gapped machines) the project ships its own harness:

```bash
tools/verify/run-offline-checks.sh
```

Six steps: build the verification harness → syntax-check every Kotlin source with
the real Kotlin front-end → compile and execute the pure-JVM test suites
(`:domain` and `:core:common`; files needing artifacts outside the Kotlin
compiler distribution are excluded by an explicit list) → structural/DI/policy
checks (`tools/verify/structure-check.py`) → compile-classpath audit
(`tools/verify/dependency-audit.py`) → dependency & license audit
(`./scripts/check-third-party.sh`). `tools/verify/README.md` documents the
harness, its bootstrap and its limits. Individual pieces:

```bash
python3 tools/verify/structure-check.py            # exit 1 on any violation
python3 tools/verify/structure-check.py --fix-unused-imports
python3 tools/verify/dependency-audit.py           # exit 1 if an import is not on a module's compile classpath
./scripts/check-third-party.sh                     # every dependency documented
```

Neither script is a substitute for `./gradlew build` — nothing offline type
checks Compose. It *does* catch the things that silently rot: unresolved imports
across module boundaries, missing Hilt bindings, unregistered modules, banned
patterns (`TODO`, `FIXME`, `NotImplementedError`, "placeholder"), hard-coded
dependency versions outside the version catalog, undocumented `Icons.*` usage and
missing required files. `dependency-audit.py` models the part of Gradle's semantics that decides what a
module may import: `api` dependencies are visible to consumers, `implementation`
dependencies are not, so a `project(":x")` edge exposes `:x`'s own packages plus
the closure of its `api` edges. It is the offline proxy for the
"cannot access class / unresolved reference" failures that otherwise only appear
in a real Gradle build — and it caught two of them (`:core:database` keeping Room
private while `:data` called `withTransaction`; `core:ui` using
`androidx.compose.animation` without declaring it). All three scripts run in CI on
every push and pull request.

## 6. Continuous integration

`.github/workflows/ci.yml` defines:

| Job | Runs on | What it does |
| --- | --- | --- |
| `static` | every push/PR | `structure-check.py`, `dependency-audit.py`, `check-third-party.sh`, `bash -n` on all scripts, Gradle wrapper checksum validation |
| `unit-tests` | every push/PR | JDK 17 + SDK 35, `./gradlew test lintDebug` |
| `assemble` | every push/PR | `./gradlew :app:assembleDebug :app:assembleRelease` |
| `native-trigger` | every push/PR | diffs the change against the base and decides whether the native path is needed (always yes for nightly/manual runs) |
| `native-tdlib` | only when `native-trigger` says yes | builds TDLib for all three ABIs, caches `build/tdlib`, uploads `jniLibs` as an artifact with checksums |
| `instrumented` | `workflow_dispatch` + nightly, after `native-tdlib` | x86_64 emulator (API 34), `connectedDebugAndroidTest` |

The static job also asserts that no `local.properties`/`keystore.properties` and no
32-hex string that looks like an `api_hash` were ever committed. The Gradle jobs
capture their output and, on failure, post a compiler-error digest as a comment on
the pull request — runner logs are not reachable from every environment, so the
actual `e: …` lines travel with the PR. Native and
instrumented jobs are excluded from the per-PR path because a TDLib
build dominates the runtime; the artifact from `native-tdlib` is what
`instrumented` consumes, so nightly gives real on-device coverage without paying
for it on every commit.

## 7. Release checklist

1. `./scripts/build-tdlib.sh --clean` — reproducible native build; confirm
   `BUILD-INFO.txt` lists TDLib 1.8.67, OpenSSL 1.1.1w, NDK 26.3.11579264.
2. `./gradlew clean test lintDebug` — green.
3. `python3 tools/verify/structure-check.py && ./scripts/check-third-party.sh`.
4. Decide the release version — it is supplied at build time
   (`-PmcVersionName=… -PmcVersionCode=…`), so record it in the tag and in the
   release notes.
5. Confirm the Room schema version and migrations are intentional.
6. `./gradlew :app:bundleRelease -PmcVersionName=<v> -PmcVersionCode=<n>` with
   `keystore.properties` present; verify the artifact contains only the ABIs you
   built (`unzip -l app-release.aab | grep '\.so'` — expect `arm64-v8a` and
   `armeabi-v7a`, plus `libcrypto.so`/`libssl.so` if OpenSSL was built shared).
7. Check the APK carries no secrets: `aapt2 dump strings` / search the DEX for
   your `api_hash` — it must not appear.
8. Smoke-test on a physical device: onboarding → add channel → import → upload →
   rotate mid-upload → force-stop → reopen (queue resumes) → diagnostics export.
9. Tag the release; the TDLib version and OpenSSL version are recorded in
   [`NOTICE`](NOTICE) and [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).
