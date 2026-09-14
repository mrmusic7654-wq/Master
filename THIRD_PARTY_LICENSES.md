# Third-party licenses

Master Control keeps its dependency set deliberately small: every entry below
exists because a specific requirement in the app needs it. Versions are pinned
centrally in [`gradle/libs.versions.toml`](gradle/libs.versions.toml); no module
declares a version of its own.

`scripts/check-third-party.sh` verifies mechanically that every dependency group
declared in the version catalog has an entry in this file, that the vendored
TDLib license text is present, and that `NOTICE` mentions TDLib. CI fails when
that check fails.

## Summary

| Component | Version | License | Upstream |
| --- | --- | --- | --- |
| TDLib (vendored source) | 1.8.67 | Boost Software License 1.0 | https://github.com/tdlib/td |
| OpenSSL (built with TDLib) | 1.1.1w | Apache-2.0 | https://www.openssl.org |
| Kotlin (stdlib, Gradle plugin, Compose plugin) | 2.0.20 | Apache-2.0 | https://kotlinlang.org |
| Kotlin Symbol Processing (KSP) | 2.0.20-1.0.25 | Apache-2.0 | https://github.com/google/ksp |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | https://developer.android.com/build |
| org.jetbrains.kotlinx (kotlinx-coroutines-core/-android/-test) | 1.9.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| org.jetbrains.kotlinx (kotlinx-serialization-json) | 1.7.2 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| com.google.dagger (hilt-android, hilt-android-compiler) | 2.52 | Apache-2.0 | https://dagger.dev/hilt/ |
| androidx.hilt (navigation-compose, work, compiler) | 1.2.0 | Apache-2.0 | https://developer.android.com/training/dependency-injection/hilt-integrations |
| androidx.room (runtime, ktx, compiler, testing) | 2.6.1 | Apache-2.0 | https://developer.android.com/training/data-storage/room |
| androidx.work (work-runtime-ktx) | 2.9.1 | Apache-2.0 | https://developer.android.com/topic/libraries/architecture/workmanager |
| androidx.datastore (preferences) | 1.1.1 | Apache-2.0 | https://developer.android.com/topic/libraries/architecture/datastore |
| androidx.navigation (navigation-compose) | 2.8.1 | Apache-2.0 | https://developer.android.com/guide/navigation |
| androidx.lifecycle (runtime-ktx, runtime-compose, viewmodel-compose, process) | 2.8.6 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| androidx.activity (activity-compose) | 1.9.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| androidx.compose (BOM) | 2024.09.00 | Apache-2.0 | https://developer.android.com/jetpack/compose |
| androidx.compose.ui (ui, ui-graphics, tooling, tooling-preview, ui-test-*) | BOM | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-ui |
| androidx.compose.runtime | BOM | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime |
| androidx.compose.foundation | BOM | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-foundation |
| androidx.compose.material3 | BOM | Apache-2.0 | https://m3.material.io |
| androidx.compose.material (material-icons-extended) | BOM | Apache-2.0 | https://fonts.google.com/icons (Material Symbols/Icons, Apache-2.0) |
| androidx.core (core-ktx) | 1.13.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core |
| androidx.biometric | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/biometric |
| androidx.documentfile | 1.0.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/documentfile |
| io.coil-kt (coil-compose) | 2.7.0 | Apache-2.0 | https://coil-kt.github.io/coil/ |
| javax.inject | 1 | Apache-2.0 | https://github.com/javax-inject/javax-inject |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 | https://junit.org/junit4/ |
| androidx.test (core) | 1.6.1 | Apache-2.0 | https://developer.android.com/training/testing |
| androidx.test.ext (junit) | 1.2.1 | Apache-2.0 | https://developer.android.com/training/testing |
| androidx.test.espresso (espresso-core) | 3.6.1 | Apache-2.0 | https://developer.android.com/training/testing |
| SQLite | (bundled with Android / Room) | Public Domain | https://www.sqlite.org |

## Why each dependency exists

**TDLib (Boost Software License 1.0)** — the Telegram engine. Master Control
never implements MTProto, Telegram cryptography or a Telegram API of its own;
all Telegram protocol work is done by TDLib, compiled from the vendored source
in `third_party/tdlib` by `scripts/build-tdlib.sh` into `libtdjson.so`. The app
talks to it through one JNI bridge (`telegram/.../TdJsonJni.kt`) behind Kotlin
interfaces, so no UI or domain code depends on TDLib types.

**OpenSSL (Apache-2.0)** — required by TDLib for TLS. Built from the pinned
upstream tag by TDLib's own `build-openssl.sh`; not vendored in git and never
used directly by Master Control code.

**Kotlin + kotlinx.coroutines + kotlinx.serialization (Apache-2.0)** — language,
structured concurrency for the upload/reconciliation engines, and JSON encoding
for TDLib requests and for catalog backups.

**KSP, Dagger Hilt, androidx.hilt, javax.inject (Apache-2.0)** — compile-time
dependency injection. Every repository, use case and worker is constructed by
Hilt; there is no service locator and no manual wiring in the app module beyond
the composition root.

**androidx.room + SQLite (Apache-2.0 / Public Domain)** — the durable catalog:
videos, permanent ID allocation, Telegram mappings, upload tasks, activity log,
channels, categories, folders. Room's compile-time SQL verification is why the
queue survives process death.

**androidx.work (Apache-2.0)** — durable background execution of uploads,
import finalization and reconciliation, with constraints (network, charging)
that the operator configures.

**androidx.datastore (Apache-2.0)** — non-sensitive preferences (theme, upload
rules, app-lock mode). Secrets never go here; they live in the Keystore-backed
secret store.

**androidx.navigation, androidx.lifecycle, androidx.activity, androidx.compose
(BOM, ui, runtime, foundation, material3, material-icons-extended),
androidx.core (Apache-2.0)** — the Compose UI: navigation between features,
lifecycle-aware state collection, activity result contracts for the SAF file
picker, Material 3 components and the icon set. Icons are restricted by
`tools/verify/icons-allowlist.txt`.

**androidx.biometric (Apache-2.0)** — optional app lock using the platform
biometric/device-credential prompt. Master Control stores no biometric data and
implements no authentication crypto itself.

**androidx.documentfile (Apache-2.0)** — SAF helper for reasoning about
user-picked documents (import source files, backup destinations).

**Coil (Apache-2.0)** — decodes locally generated poster files in the library
grid. Coil is used for local files only; Master Control never loads remote
images and has no image cache of Telegram media.

**JUnit 4 (EPL-1.0), androidx.test / Espresso (Apache-2.0)** — unit, database,
integration and UI tests. Test-only; not shipped in release builds.

## License texts

* Apache License 2.0 — see [`LICENSE`](LICENSE) (the same text applies to every
  Apache-2.0 component listed above).
* Boost Software License 1.0 — [`third_party/tdlib/LICENSE_1_0.txt`](third_party/tdlib/LICENSE_1_0.txt).
* Eclipse Public License 1.0 (JUnit 4) — https://www.eclipse.org/legal/epl-v10.html.
* SQLite is public domain — https://www.sqlite.org/copyright.html.
* Material icons (AndroidX `material-icons-extended`) are Apache-2.0 —
  https://fonts.google.com/icons.

## Trademarks and branding

Telegram is a trademark of Telegram Inc. Master Control is not affiliated with
Telegram, does not use the Telegram logo or name as its own branding, and does
not imitate the official Telegram app's appearance. The launcher icon and app
name are original (see `app/src/main/res/drawable/ic_launcher_foreground.xml`).
