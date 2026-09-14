# Security

Master Control holds three things worth stealing: Telegram **application
credentials**, a live Telegram **session**, and a **catalog** that maps permanent
video IDs to channel/message IDs. This document states what is protected, how,
and — just as important — what is *not* protected and why.

---

## 1. Threat model

**Assumed attacker**

* Physical access to an unlocked or briefly-attended device.
* A malicious app running on the same device, without root.
* Someone who obtains a backup, an exported catalog file, or a diagnostics export.
* Network observers between the device and Telegram.

**Explicitly out of scope**

* A rooted device or a compromised OS kernel: any app-level protection is void,
  so Master Control does not pretend otherwise and ships no root detection.
* A hostile Telegram server (the API endpoint is Telegram's; transport security
  is TDLib's MTProto implementation, not ours).
* Coercion of the operator (no duress PIN).

**Design rule:** the app never becomes the weakest link. Secrets are either in
the Android Keystore, in TDLib's own encrypted session store, or in the
operator's head — never in a plain file, a log line, an export, or a backup.

## 2. Secrets inventory

| Secret | Where it lives | Protection |
| --- | --- | --- |
| Telegram `api_id` | `SharedPreferences` file `master_control_secrets`, Keystore alias `telegram_api_id_v1` | AES-256-GCM under a non-exportable Keystore key |
| Telegram `api_hash` | same file, alias `telegram_api_hash_v1` | AES-256-GCM, masked in UI, registered as a log-redaction secret |
| TDLib database encryption key | same file, alias `td_database_encryption_key_v1` | AES-256-GCM; 32 random bytes from `SecureRandom`, Base64 |
| Telegram session (auth key, session data) | TDLib's own database under the app's private data dir | Encrypted by TDLib with the key above |
| App-lock PIN | same file, alias `app_lock_pin_hash_v1` | PBKDF2-HMAC-SHA256, 150 000 iterations, 16-byte random salt, 256-bit output |
| Verification codes, 2FA passwords, phone numbers | **nowhere** | Held in memory for the duration of one attempt only; never written, never logged |
| Catalog (videos, mappings, channels, activity) | Room database in app-private storage | Android sandbox; see §8 for the honest caveat |

There are no other credentials: no server, no API keys for analytics or crash
reporting, no OAuth tokens, no signing material in the app.

## 3. Keystore-backed encryption

`core:security` implements the whole scheme:

* **`AndroidKeyStoreCipher`** — one AES key per alias, generated on demand with
  `KeyProperties.PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`,
  `ENCRYPTION_PADDING_NONE`, `setKeySize(256)`. Keys are generated inside the
  Android Keystore and are **not exportable** — the app only ever sees ciphertext
  and plaintext in memory.
* Payload format is `Base64(iv || ciphertext)`; every encryption uses a fresh IV
  from the GCM cipher, so identical secrets never produce identical blobs.
* **`SecretStore`** wraps the cipher with a dedicated `SharedPreferences` file
  (`master_control_secrets`). The file therefore contains only opaque ciphertext.
* **Failure handling is deliberate:** if decryption fails — after a backup
  restore to a different device, a Keystore invalidation, or an OS-level key
  loss — `SecretStore.read` removes the broken entry and returns `null`. The app
  then treats the credentials as absent and routes the operator back to
  onboarding instead of crashing or silently falling back to an unprotected mode.
* `SecretStore.clear(alias)` deletes **both** the ciphertext and the Keystore key,
  so clearing secrets is irreversible by design. Aliases are versioned
  (`_v1`) so a future format change can migrate instead of colliding.

`Settings → Clear every stored secret` wipes the api credentials, the TDLib
database key and the PIN hash, and leaves the catalog intact: the operator loses
access to the session, not the record of what was uploaded.

## 4. Runtime BYOK credentials

* The api_id/api_hash are entered on first run and validated client-side
  (`api_id` positive integer, `api_hash` 32 hex characters) before anything is
  stored.
* Nothing Telegram-related is compiled into the app: rotating credentials is a
  config change (`Settings → Credentials`), not a rebuild.
* The UI shows the hash masked (`core:common` `maskSecret`) with an explicit
  reveal toggle that has its own content description for screen readers; the
  full value is never placed in a state that gets logged or exported.
* Credentials are not part of the catalog backup, the diagnostics export, or any
  intent.

## 5. App lock

* Optional; mode is `NONE`, `PIN` or `BIOMETRIC` (`AppLockMode`, persisted in
  DataStore — the *mode* is not a secret, the PIN hash is).
* **PIN** — `PinHasher` derives the hash with PBKDF2WithHmacSHA256 (150 000
  iterations, 256-bit key, 16-byte `SecureRandom` salt), stores `saltB64:hashB64`
  and compares with `MessageDigest.isEqual` (constant time). `PBEKeySpec` is
  cleared in a `finally` block so the PIN does not linger on the heap.
* **BIOMETRIC** — uses `androidx.biometric` with `BIOMETRIC_WEAK or
  DEVICE_CREDENTIAL`. The device-credential fallback is always offered so a
  broken or re-enrolled fingerprint can never lock the operator out of their own
  library. Master Control stores no biometric template; it only asks the platform
  whether authentication succeeded.
* `AppLockSession` is an in-memory singleton: the unlocked state survives
  rotation and configuration changes but **not** process death, which is the
  intended behaviour — a killed app re-locks.
* "Lock now" is available from the app bar at any time.

## 6. Logging and diagnostics

* Every module logs through the `Logger` façade (`core:logging`); nothing calls
  `android.util.Log` directly.
* `Logger.registerSecret(value)` registers a string that must never appear in
  output. `TelegramCredentialsRepositoryImpl` registers the api hash **at
  construction** (so it is masked from the first log line after a restart), on
  save, and on every read. `MasterLogger` replaces registered values with `***`
  before writing to logcat *and* before buffering. Values shorter than eight
  characters are ignored on purpose: masking them would mangle ordinary IDs.
* `VERBOSE`/`DEBUG` output is gated by `LoggerConfiguration.debugLogsEnabled`,
  which is off in release builds; `WARN`/`ERROR` stay, because silently dropping
  failures makes incidents undiagnosable.
* The diagnostics buffer is a bounded in-memory ring (default 1000 entries). It
  is never written to disk by the app; `Settings → Diagnostics → Export` hands
  the redacted text to a SAF destination the operator picks, so nothing leaves
  the device without an explicit action.
* Never logged, by contract: verification codes, 2FA passwords, session keys,
  full content URIs, file contents, and the TDLib database key. Upload logs carry
  task IDs, byte counts and error text from TDLib.

## 7. Storage access (SAF)

* Media is referenced by the URI the operator picked; Master Control never scans
  shared storage, never writes to it implicitly, and requests no
  `READ_MEDIA_VIDEO`/`MANAGE_EXTERNAL_STORAGE` permission.
* Picked URIs get a **persistable read grant**
  (`ContentResolver.takePersistableUriPermission`) at import and at replacement
  time, because an upload can legitimately happen after a reboot. Providers that
  cannot grant durable access are recorded in the activity log rather than
  failing the import — the operator is told access may be lost.
* When a video row is deleted, its persisted grant is released
  (`releasePersistedAccess`), so the app does not accumulate read access to files
  it no longer references.
* App-generated thumbnails live in the app's own files directory.
* Catalog export/import is SAF-only: the operator picks the destination, the
  export contains metadata (IDs, titles, mappings, statuses) and **no** secrets
  and **no** media bytes, and import is upsert-only — it never deletes an
  existing row, and the destructive conflict mode requires an explicit
  acknowledgement.

## 8. Honest caveats

* **The Room database is not encrypted.** It contains catalog metadata (video
  IDs, titles, channel/message IDs, statuses, activity) — not credentials, not
  media. Encrypting it would add a key-management dependency (SQLCipher) whose
  benefit does not outweigh the cost for this data class, and it would not help
  against the attacker model in §1 (a rooted device reads the key too). If your
  threat model requires it, enable full-disk encryption on the device and rely on
  `allowBackup=false`.
* **No `FLAG_SECURE`.** Screenshots and the task-switcher preview are not blocked.
  Everything sensitive on screen is already masked (api hash, PIN), and blocking
  screenshots harms accessibility tooling and legitimate documentation. This is a
  deliberate trade-off, not an oversight.
* **No certificate pinning of our own** — Master Control makes no HTTP calls;
  transport security is entirely TDLib/MTProto's responsibility.
* **Keystore loss is unrecoverable.** If the OS invalidates the keys (restore to
  another device, some OEM updates), the credentials are gone by design and must
  be re-entered. The catalog survives; the Telegram session does not.
* **TDLib is a large C++ dependency.** Its security is upstream's; we pin a
  released version (1.8.67), build it from vendored source and record checksums
  (see §10) rather than trusting a binary blob.

## 9. Permissions

Requested in `app/src/main/AndroidManifest.xml`, each with a reason:

| Permission | Why |
| --- | --- |
| `INTERNET` | TDLib talks to Telegram directly; there is no server of our own |
| `ACCESS_NETWORK_STATE` | Wi-Fi-only upload rules and the connectivity monitor |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Durable uploads survive the UI being gone |
| `POST_NOTIFICATIONS` | Upload progress notification (runtime-granted on API 33+) |
| `WAKE_LOCK` | Keeps an in-flight upload alive while the screen is off |
| `USE_BIOMETRIC` | Optional app lock |

Not requested: location, contacts, camera, microphone, SMS, phone state,
`READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`,
`REQUEST_INSTALL_PACKAGES`. `android:allowBackup="false"` is set, and no
`dataExtractionRules` are declared — there is nothing this app wants in a cloud
backup.

## 10. Supply chain and build integrity

* All dependency versions are pinned centrally in `gradle/libs.versions.toml`;
  `tools/verify/structure-check.py` fails the build if a module declares a
  hard-coded version or an alias that does not exist in the catalog. No dynamic
  (`+`) versions anywhere.
* TDLib is **vendored source** (`third_party/tdlib`, 1.8.67), never a downloaded
  binary. `scripts/build-tdlib.sh` verifies the pinned version against
  `third_party/tdlib/CMakeLists.txt` before building, and writes
  `BUILD-INFO.txt` with the TDLib/OpenSSL/NDK versions, flags and **sha256** of
  every produced `.so`. The only network fetch in that script is OpenSSL at a
  pinned tag from the official repository (and it can be skipped with
  `--openssl-dir`).
* `jniLibs/` and `build/` are git-ignored: binaries are outputs, not source.
* CI validates the Gradle wrapper checksums on every run
  (`gradle/actions/wrapper-validation`) and audits that every declared dependency
  is documented in [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).
* Release signing material lives in `keystore.properties`, which is git-ignored;
  without it the release build stays unsigned instead of quietly using the debug
  key.

## 11. Reporting a vulnerability

Please do **not** open a public issue for anything you believe is exploitable.

* Email the maintainer address listed in the repository's
  [security advisory page](../../security/advisories) (GitHub → *Security* →
  *Report a vulnerability*), or use the contact in the project README.
* Include: version/commit, Android version and device, reproduction steps, and
  what an attacker gains. A proof-of-concept is welcome but not required.
* We aim to acknowledge within 5 business days and to publish a fix or a
  documented rationale within 30 days. Coordinated disclosure is appreciated;
  default is to credit reporters in the release notes unless they prefer not to
  be named.
* Supported: the latest release on `main`. Older versions get fixes only when the
  issue is severe and the fix backports cleanly.

**In scope:** secret leakage (Keystore, logs, exports, backups), authentication
bypass of the app lock, unintended network endpoints, permission escalation,
database corruption that loses ID↔mapping integrity, TDLib/JNI memory-safety
issues reachable from app input.
**Out of scope:** rooted-device attacks, social engineering of the Telegram
account, Telegram-side policy decisions, denial of service against Telegram.
