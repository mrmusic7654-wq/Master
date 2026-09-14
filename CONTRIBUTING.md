# Contributing

Master Control is a working system, not a template. The bar for a change is that
it keeps every documented invariant true and that it can be verified — offline if
necessary. Please read this before opening a pull request.

---

## 1. Ground rules

These are the rules the project was built under; they are enforced by review and,
where possible, by `tools/verify/structure-check.py`:

1. **No placeholders in production paths.** No `TODO`, `FIXME`,
   `NotImplementedError`, "coming soon", stub repositories, hard-coded sample
   data, or fake progress. The check fails on all of them. If a feature is not
   ready, it does not merge — an honest `AppError` and a disabled action beat a
   lie in the UI.
2. **The permanent video ID never changes.** Re-upload, replacement, re-import:
   the ID stays, only the mapping moves. Retired IDs are never reused.
3. **UI never talks to TDLib.** Everything Telegram goes through the
   `Telegram*Repository` / `TelegramUploadManager` / `TelegramAuthManager`
   interfaces in `domain`. A Composable or ViewModel importing from `:telegram`
   internals is a bug.
4. **No hard-coded Telegram credentials**, ever. Runtime BYOK only.
5. **Never log or export** api hashes, verification codes, 2FA passwords, session
   keys or full content URIs. Use the `Logger` façade; register new secret-shaped
   values with `registerSecret`.
6. **No new dependency without a reason.** Every library must be justified in
   [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) (what it replaces, why
   that is not enough) and pinned in `gradle/libs.versions.toml`. "It's popular"
   is not a reason. `./scripts/check-third-party.sh` fails on undocumented groups.
7. **No new module without a reason.** The module list is deliberately small;
   prefer adding to an existing layer over creating `:core:whatever`.
8. **No playback, no Streamer code, no server/CDN/proxy** in this app.
9. **Versions live in the catalog.** A hard-coded version string in a
   `build.gradle.kts` fails the structure check.
10. **Icons are curated.** `Icons.*` usages must appear in
    `tools/verify/icons-allowlist.txt` — the extended icon set is huge and only
    the icons actually used should be reachable.

## 2. Development setup

Follow [`SETUP.md`](SETUP.md). Minimum for Kotlin-only work (no SDK, no network):

```bash
tools/verify/run-offline-checks.sh
```

For real builds: JDK 17, SDK 35, NDK 26.3.11579264, and
`./scripts/build-tdlib.sh` once (see [`BUILD.md`](BUILD.md)).

## 3. Where things live

| Layer | Module | Rule |
| --- | --- | --- |
| Models, errors, ports, use cases | `:domain` | Pure Kotlin + kotlinx.serialization. No Android imports, no framework types |
| Entities, DAOs, database | `:core:database` | Room only; SQL lives in DAOs; schema JSON committed |
| Secrets | `:core:security` | Keystore, PIN hashing, TDLib key provider |
| Logging | `:core:logging` | Façade + redaction; nobody else touches `android.util.Log` |
| Formatting, window classes, components | `:core:common`, `:core:ui` | No business logic, no repositories |
| Repository implementations, SAF, connectivity | `:data` | The only place that knows about Android specifics for domain ports |
| TDLib JNI + JSON protocol | `:telegram` | Behind domain interfaces; `tdjson_bridge` is the only native surface |
| WorkManager workers | `:worker` | Durable work only; never UI logic |
| Screens | `:feature:*` | `Navigation.kt` + `ViewModel` + `Screen.kt`; stateless composables |
| App shell, DI graph, lock gate | `:app` | Single activity, Hilt entry point, `Configuration.Provider` |

Read [`ARCHITECTURE.md`](ARCHITECTURE.md) before moving code between layers.

## 4. Workflow

1. Fork, branch from `main` (`feature/<area>-<short-description>` or
   `fix/<issue>-<short-description>`).
2. Keep the change small and subsystem-shaped. Large work is split in the order
   the project itself was built: foundation → TDLib → channels → import → upload
   → library → management → security/diagnostics → tests → hardening.
3. Write or update tests in the same PR (see §5).
4. Run the checks (§6) locally.
5. Open a PR against `main`: what changed, why, which invariant it touches, how
   you verified it, and screenshots for UI work in both light and dark themes.
6. One reviewer approval plus green CI. Squash-merge; keep the subject line
   imperative and the body explanatory.

## 5. Testing expectations

* **Domain use cases** — plain JUnit tests with fakes; no Android. This is where
  new behaviour should be proven (ID allocation, mapping commits, backoff,
  reconciliation decisions, validation).
* **Formatting / helpers** (`:core:common`) — unit tests, `Locale.ROOT` assumed;
  never assert an exact localized layout.
* **Database** — DAO tests and the migration-chain test; bumping
  `MasterControlDatabase.version` requires a `Migration`, the new schema JSON and
  an instrumented migration test.
* **UI** — stateless composables take state + callbacks so they can be driven
  without a ViewModel; ViewModels are tested through their `StateFlow`.
* **Only tests may generate sample data.** Never add fixtures to a production
  source set.

## 6. Checks every PR must pass

```bash
python3 tools/verify/structure-check.py     # imports, DI wiring, banned patterns, versions, icons, required files
./scripts/check-third-party.sh              # dependency ↔ license documentation
./gradlew test lintDebug                    # unit tests + lint
tools/verify/run-offline-checks.sh          # everything above without Gradle/SDK access
```

`structure-check.py` also has a maintenance mode:
`--fix-unused-imports` removes imports it can prove are unused.

CI (`.github/workflows/ci.yml`) runs the static checks, unit tests, lint and both
assemble variants on every push and PR; TDLib native builds and emulator runs are
nightly/manual because they dominate runtime.

## 7. Code style

* Kotlin official style; 4 spaces; trailing commas in multi-line parameter lists.
* Compose: state hoisting everywhere — `Screen` composables are stateless,
  state comes from a single `StateFlow` in the ViewModel, events go through
  `on<Event>` handlers.
* Errors are `AppError` subclasses with a human `userMessage`; UI shows that
  message, logs carry the cause. Never `throw RuntimeException("…")` at the user.
* Coroutines: repositories own their dispatchers (`Dispatchers.IO`); ViewModels
  use `viewModelScope` + `stateIn(WhileSubscribed(5_000))`.
* Accessibility: every icon-only control has a content description; status is
  conveyed with text, not colour alone; animations collapse when the system asks
  for reduced motion.
* Comments explain *why* (invariants, Telegram quirks, race handling), not *what*.

## 8. Things that will get a PR rejected

* A fake or simulated progress bar, a mocked Telegram response, or demo catalog
  entries in a production source set.
* Swallowing an exception so a failure looks like success.
* Adding a dependency to solve something the stdlib or an existing module does.
* Touching the permanent ID, reusing a retired ID, or writing a mapping without
  the transactional commit path.
* Logging a secret, a code, a password, or a full `content://` URI.
* Copying Telegram's branding, icons or client source; this app must not look
  like an official Telegram client.
* A UI-only change that silently drops a guarantee documented in
  [`README.md`](README.md) (durability, offline-first, real progress).

## 9. License

Contributions are made under the project licence, Apache-2.0
([`LICENSE`](LICENSE)). Third-party obligations are tracked in
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) and [`NOTICE`](NOTICE); if
your change adds a dependency, update both in the same PR.
