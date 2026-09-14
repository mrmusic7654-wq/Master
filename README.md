# Master Control

**Master Control** is an Android administration app for a media library whose
storage layer is Telegram. It is the operator's console: it catalogs video files
from the device, uploads them to Telegram channels the operator controls, and
keeps a durable, verifiable record of which permanent video ID lives in which
Telegram channel and message.

It is *not* a player and *not* a Telegram client. Playback is the job of a
separate Streamer app, which consumes the catalog Master Control produces.
Master Control never renders Telegram media and never re-implements Telegram.

---

## What it guarantees

| Guarantee | How it is enforced |
| --- | --- |
| **A permanent video ID** (`VID-000001`) identifies a title forever | IDs come from a single transactional counter (`video_id_counter`); they are never reused, never rewritten, and survive replacement of the underlying media |
| **ID → channel → message → media is always recorded** | `telegram_mappings` holds exactly one active mapping per video; the mapping is committed atomically with the video status and the finished upload task |
| **Telegram work is done by TDLib** | No MTProto, no hand-written Telegram crypto, no fake API. TDLib 1.8.67 is vendored as source and compiled to `libtdjson.so` (`scripts/build-tdlib.sh`); Kotlin code talks to it through one JNI bridge behind interfaces |
| **API credentials are runtime BYOK** | `api_id` / `api_hash` are entered in the app, validated, and stored AES-GCM encrypted with an Android Keystore key. Nothing is hard-coded; no rebuild is needed to change them |
| **Uploads survive everything** | The queue lives in Room, runs in a WorkManager foreground service, reports only real byte counters, retries with bounded exponential backoff, and is re-claimed after process death |
| **No fabricated state** | No demo data, no placeholder screens, no invented progress, no estimated quotas the app cannot read. Unknown values render as `—` |
| **Secrets stay secret** | API hash, verification codes, 2FA passwords, PINs and session keys are never logged, exported or included in backups; log output passes through a secret redactor |

## Features

* **Onboarding** — enter API credentials, test them, authenticate (phone, code,
  2FA), pick a storage channel and verify real posting permissions.
* **Dashboard** — live catalog and storage counters, queue status, recent
  activity, quick actions. Values come from real rows only.
* **Library** — search, faceted filters (status, category, folder, tag), sort
  orders, grid/list layouts driven by window size, SAF import with duplicate
  handling (skip / keep both / replace while keeping the permanent ID).
* **Video details** — permanent ID, metadata editor, Telegram mapping with
  channel/message IDs, upload task history, on-demand mapping verification,
  media replacement, and deletion with an explicit local-only vs
  local-and-Telegram choice.
* **Upload queue** — durable tasks, real progress and transfer rates, cancel and
  requeue. TDLib cannot pause a file mid-transfer, so the app offers no pause
  button that would lie.
* **Channels** — search Telegram for channels you administer, verify live
  permissions, set the default upload target, label, disable and remove.
* **Categories & folders** — local catalog organization, nested folders, honest
  deletion semantics (videos keep their IDs and mappings).
* **Activity log** — every import, upload, mapping change, channel change and
  security event, filterable by subsystem.
* **Settings, security & diagnostics** — theme, upload rules, app lock (PIN /
  biometric / device credential), catalog JSON export and import, reconciliation
  runs, real counters, TDLib version.

## Architecture

Clean architecture with strict module boundaries. UI never touches TDLib, and
TDLib never touches UI.

```
app  ──────────────►  feature:*  ──────────►  domain (pure Kotlin)
 │                        │                        ▲
 │                        └──► core:ui             │ interfaces + use cases
 └──► worker ──────────────────────────────────────┤
      telegram (TDLib/JNI) ──► domain ports ───────┘
      data (Room/DataStore) ─► domain repositories
      core:database · core:datastore · core:security · core:logging · core:common
```

| Module | Responsibility |
| --- | --- |
| `app` | Composition root: `@HiltAndroidApp`, single activity, NavHost, app lock gate, WorkManager `Configuration.Provider` |
| `domain` | Pure Kotlin/JVM: models, `AppError`, repository & port interfaces, use cases. No Android types |
| `data` | Room/DataStore-backed repository implementations, SAF document store, connectivity monitor, Android media toolkit |
| `core:database` | Room entities, DAOs, schema v1, transactional video ID allocation |
| `core:datastore` | Preferences and `SettingsRepository` |
| `core:security` | Keystore-backed `SecretStore`, PIN hashing, TDLib database encryption key |
| `core:logging` | `Logger`, ring-buffer diagnostics, secret redaction, tags |
| `core:common` | Locale-fixed formatters, relative dates, secret masking, transfer-rate estimator |
| `core:ui` | Material 3 theme, adaptive layout, 15+ shared components, icon discipline |
| `telegram` | TDLib JNI bridge, client core, auth/chat/message/upload sessions, error mapping |
| `worker` | Upload queue worker (foreground), import finalization, reconciliation, notifications |
| `feature:*` | One module per screen group: onboarding, dashboard, library, video, upload, channels, categories, folders, activity, settings |
| `third_party/tdlib` | Vendored TDLib 1.8.67 source (Boost License 1.0) |
| `tools/verify` | Offline verification harness: syntax check, JVM unit tests, structure/policy check |
| `scripts` | `build-tdlib.sh` (native build), `check-third-party.sh` (license hygiene) |

Read [`ARCHITECTURE.md`](ARCHITECTURE.md) for the data flow of an import, an
upload and a reconciliation run, and for the reasoning behind each boundary.

## Getting started

1. **[`SETUP.md`](SETUP.md)** — install the toolchain (JDK 17, Android SDK, NDK,
   CMake, Ninja) and open or build the project.
2. **[`TELEGRAM_SETUP.md`](TELEGRAM_SETUP.md)** — obtain your own `api_id` and
   `api_hash` from Telegram, and prepare a channel you administer.
3. **[`BUILD.md`](BUILD.md)** — build TDLib, assemble debug/release APKs, run
   tests, and verify without network access.
4. **[`SECURITY.md`](SECURITY.md)** — the threat model, what is stored where, and
   what is never stored at all.

Minimum: Android 8.0 (API 26). ABIs: `arm64-v8a`, `armeabi-v7a` (`x86_64`
optional for emulators, via `-PincludeX86_64=true`).

## Verification

The authoritative build is Gradle (`./gradlew build`). Because a sealed review
environment may not reach Google Maven, the repository also ships an offline
harness that runs the checks that do not need the Android SDK:

```bash
tools/verify/run-offline-checks.sh          # syntax + JVM unit tests + structure + licenses
python3 tools/verify/structure-check.py     # module boundaries, Hilt wiring, banned patterns
./scripts/check-third-party.sh              # every dependency has a license entry
```

CI runs both paths — see [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Project layout

```
app/                     activity, NavHost, app lock gate, manifest, launcher icon
core/{common,database,datastore,logging,security,ui}/
data/                    repository implementations, SAF document store, DI
domain/                  models, errors, ports, repositories, use cases
feature/{onboarding,dashboard,library,video,upload,channels,categories,folders,activity,settings}/
telegram/                TDLib JNI bridge and Telegram client implementation
worker/                  upload, import finalization, reconciliation workers
third_party/tdlib/       vendored TDLib 1.8.67 source
scripts/                 native build + third-party hygiene
tools/verify/            offline verification harness
docs are at the repository root: README, ARCHITECTURE, SETUP, TELEGRAM_SETUP,
BUILD, SECURITY, THIRD_PARTY_LICENSES, CONTRIBUTING
```

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Short version: subsystems are built
and verified in order, no placeholder code in production paths, every dependency
needs a reason, and every user-visible state must be reachable from real data.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
TDLib is vendored under the Boost Software License 1.0. Master Control is not
affiliated with Telegram and does not use Telegram branding.
