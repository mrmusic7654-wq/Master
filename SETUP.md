# Setup

How to get a working Master Control development environment and a first
successful run on a device.

---

## 1. Requirements

| Tool | Version | Why |
| --- | --- | --- |
| JDK | 17 | Gradle, AGP 8.7 and the Kotlin 2.0 toolchain target JVM 17 |
| Android Studio | Koala 2024.1.2 or newer | AGP 8.7.3 support, Compose tooling |
| Android SDK Platform | 35 | `compileSdk` / `targetSdk` |
| Android SDK Build-Tools | 35.x | Packaging |
| Android NDK | **26.3.11579264** | Compiling TDLib (pinned in `gradle/libs.versions.toml`) |
| CMake | 3.22.1 | TDLib's Android build (the SDK's own CMake is preferred) |
| Ninja | any recent | TDLib build generator |
| Platform tools / adb | latest | Deploying to a device |
| Perl, gperf, php, make, tar, unzip | system | Required by TDLib/OpenSSL build scripts |
| Device | Android 8.0 (API 26)+, `arm64-v8a` or `armeabi-v7a` | `minSdk` 26; no x86 ABI by default |

A physical ARM device is strongly recommended. Emulators need the optional
`x86_64` TDLib build (see [`BUILD.md`](BUILD.md)).

## 2. Get the source

```bash
git clone <your-fork-url> master-control
cd master-control
```

TDLib is vendored in `third_party/tdlib` (version 1.8.67). Verify it is present:

```bash
grep -m1 'project(TDLib VERSION' third_party/tdlib/CMakeLists.txt
# project(TDLib VERSION 1.8.67 LANGUAGES CXX C)
```

## 3. Install the SDK components

Either through Android Studio (**Settings → Languages & Frameworks → Android
SDK → SDK Tools**) or with `sdkmanager`:

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0" \
           "ndk;26.3.11579264" "cmake;3.22.1" "platform-tools"
```

Point the project at the SDK by creating `local.properties` (never committed):

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

## 4. Build the native TDLib libraries

The repository ships **no** prebuilt `.so` files — they are produced from the
vendored source so the binary in your APK is reproducible:

```bash
./scripts/build-tdlib.sh --android-sdk-root "$HOME/Android/Sdk"
```

This writes `telegram/src/main/jniLibs/<abi>/libtdjson.so` plus a
`BUILD-INFO.txt` manifest with checksums. Details, flags and troubleshooting are
in [`BUILD.md`](BUILD.md).

## 5. Signing (release builds only)

Create `keystore.properties` at the repository root (never committed):

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, `assembleRelease` produces an **unsigned** APK — which is the honest
default for CI.

## 6. Build and run

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or press **Run** in Android Studio with a device selected.

## 7. First run

Master Control starts empty — there is no demo data. The onboarding flow is the
only way in:

1. **Credentials** — enter the `api_id` and `api_hash` you registered at
   <https://my.telegram.org> (see [`TELEGRAM_SETUP.md`](TELEGRAM_SETUP.md)).
   They are validated client-side, then encrypted into the Android Keystore-backed
   secret store. *Test connection* starts TDLib and reports the real result.
2. **Authenticate** — phone number, then the Telegram code, then your 2FA
   password if the account has one. Codes and passwords are never stored or
   logged.
3. **Storage channel** — search for a channel you administer. Master Control
   verifies your actual rights; a channel where you cannot post cannot be added.
   Mark one as the default upload target.
4. **Ready** — the dashboard opens. Import a video from the library to see the
   full chain (permanent ID → upload → mapping).

Until all three steps are complete the app returns to onboarding on every start —
the gate is derived from real state, not from a first-launch flag.

## 8. Verify your checkout

```bash
./gradlew test                    # unit tests (domain, core:common, core:database)
./gradlew lintDebug               # Android lint
python3 tools/verify/structure-check.py
./scripts/check-third-party.sh
tools/verify/run-offline-checks.sh   # all of the above without Gradle/SDK access
```

## 9. Troubleshooting

| Symptom | Cause & fix |
| --- | --- |
| `NDK not configured` / `NDK 26.3.11579264 not installed` | Install that exact NDK version; it is pinned on purpose |
| `UnsatisfiedLinkError: tdjson_bridge` | `scripts/build-tdlib.sh` was not run, or the device ABI is not built. Rebuild, or add `-PincludeX86_64=true` for emulators |
| Gradle cannot resolve `androidx.*` | Network/proxy blocking Google Maven. Use `tools/verify/run-offline-checks.sh` for review work |
| App opens straight into onboarding after setup | Expected when credentials, authorization or the default channel are missing. Settings → Diagnostics shows the real state |
| Uploads never start | Check Settings → Uploads (Wi-Fi-only / charging-only) and that the notification permission was granted on Android 13+ |
| `POST_NOTIFICATIONS` dialog never appeared | The permission is requested once per install; grant it in system settings to see queue notifications |

## 10. What is intentionally absent

* No prebuilt TDLib binaries, no downloaded artifacts in git.
* No hard-coded Telegram credentials, no `.env` with secrets.
* No demo catalog, fixtures or sample media in production source sets.
* No player, no Streamer code.
