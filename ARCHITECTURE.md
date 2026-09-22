# Architecture

This document describes how Master Control is structured and why. It is the
reference for anyone changing the code: the boundaries here are enforced
mechanically by `tools/verify/structure-check.py`, not by convention.

---

## 1. The one invariant everything else serves

A catalog entry is a chain:

```
VID-000007  ──►  Telegram channel 1001234567890  ──►  message 4211  ──►  media
(permanent)      (chosen by the operator)              (recorded once)     (in Telegram)
```

* The **video ID** is allocated from a single transactional counter
  (`video_id_counter`, `VideoIdAllocatorDao.allocate()`), formatted `VID-%06d`,
  and never reused — not after deletion, not after replacement.
* The **mapping** (`telegram_mappings`, one row per video, unique on
  `(channelId, messageId)`) is the only thing that changes when media is
  replaced or re-uploaded.
* The mapping row, the video status (`COMPLETE`) and the removal of the finished
  upload task are committed in **one Room transaction**
  (`VideoRepository.commitCompletedUpload`). A half-applied upload cannot exist.

Every other design decision — the durable queue, the reconciliation engine, the
"receipt first, then commit" ordering in the upload worker — exists to protect
that chain.

## 2. Layers and modules

```
┌────────────────────────────────────────────────────────────────────────┐
│ app          composition root: Application, MainActivity, NavHost,      │
│              app-lock gate, WorkManager Configuration.Provider          │
├────────────────────────────────────────────────────────────────────────┤
│ feature:*    Compose screens + @HiltViewModel per feature               │
│              (onboarding dashboard library video upload channels        │
│               categories folders activity settings)                     │
├────────────────────────────────────────────────────────────────────────┤
│ domain       pure Kotlin/JVM: models, AppError, repository & port       │
│              interfaces, use cases. No Android, no Compose, no Room.    │
├────────────────────────────────────────────────────────────────────────┤
│ data         Room/DataStore repository implementations, SAF document    │
│              store, connectivity monitor, media toolkit                 │
│ telegram     TDLib: JNI bridge, client core, auth/chat/message/upload   │
│              sessions, error mapping                                    │
│ worker       WorkManager workers: upload queue, import finalize,        │
│              reconciliation, notifications                              │
├────────────────────────────────────────────────────────────────────────┤
│ core:*       database · datastore · security · logging · common · ui    │
└────────────────────────────────────────────────────────────────────────┘
```

Rules that the structure check enforces:

1. **Dependencies point inward.** `feature:*` and `worker` depend on `domain`;
   `data`/`telegram` implement `domain` interfaces; nothing in `domain` depends
   on Android.
2. **Imports must resolve through declared Gradle dependencies.** A file may not
   import from a module its `build.gradle.kts` does not declare (following `api`
   transitively). This kills the "works in the IDE, fails in Gradle" class of bug.
3. **Hilt wiring is checked statically.** Every injected interface must have a
   `@Binds`/`@Provides`; every `@HiltViewModel` dependency must be bound or
   `@Inject`-constructible; a bare `Context` is never injected (always
   `@ApplicationContext`).
4. **Versions live only in `gradle/libs.versions.toml`.** A hard-coded
   `group:name:version` string in any build file is an error.
5. **Icons are allowlisted** (`tools/verify/icons-allowlist.txt`).
6. **Banned patterns** in production sources: `TODO`, `FIXME`,
   `NotImplementedError`, "not implemented", placeholder/mock/sample wiring.

## 3. Data model

Room schema v1 (`core:database`), `exportSchema = true`:

| Table | Purpose |
| --- | --- |
| `videos` | Catalog rows: permanent `videoId`, title, description, file facts, poster URIs, `sha256`, status, category/folder links |
| `telegram_mappings` | One row per video: `channelId`, `messageId`, Telegram file IDs, size, mime, `mappingStatus`, timestamps |
| `video_tags` | Tag rows (`videoId`, lowercase `tag`) |
| `categories` | Name (unique), description, stable icon key, sort order |
| `folders` | Name (unique), optional `parentFolderId` (self-FK, `SET NULL` on delete) |
| `channels` | Telegram chat rows configured as storage: id, title, username, kind, permissions, enabled, default, last verified |
| `upload_tasks` | Durable queue rows: state, byte counters, attempts, last error |
| `activity_log` | Operational history (bounded by trimming on insert) |
| `video_id_counter` | The ID allocator's single row |

Foreign keys express the deletion semantics the UI promises:

* `videos.categoryId` / `videos.folderId` → `SET NULL`: deleting a category or
  folder never deletes a video.
* `folders.parentFolderId` → `SET NULL`: deleting a parent promotes children.
* Deleting a folder with `reassignVideosTo` performs an explicit
  `UPDATE videos SET folderId = …` **inside the same transaction** as the folder
  delete, so the operator's choice is honoured rather than silently ignored.
* Deleting a video removes the local row; Telegram deletion happens **first**
  and only when the operator chose it, and the whole operation aborts if
  Telegram refuses.

### Video status vs mapping status

`VideoStatus` (`IMPORTING`, `READY`, `UPLOADING`, `COMPLETE`, `FAILED`) describes
the catalog row. `MappingStatus` (`NONE`, `PENDING_VERIFY`, `ACTIVE`, `STALE`,
`REPLACED`, `REMOTE_DELETED`) describes what Telegram actually holds. They are
separate because they can disagree — and when they do, the app says so instead
of guessing.

## 4. Import flow

```
Library screen ── SAF OpenDocument("video/*") ──► content URI
      │
      ▼
PrepareVideoImportUseCase
  · MediaToolkit.inspect(uri)            → size, duration, resolution, mime, name
  · duplicate guard (size + file name)   → ImportOutcome.Duplicate
  · VideoIdAllocator.allocate()          → permanent ID (transactional)
  · createVideo(status = IMPORTING, hashPending = true)
  · activity: VIDEO_IMPORTED
  · scheduler.scheduleImportFinalization(videoId)
      │
      ▼ (WorkManager, background)
ImportFinalizeWorker → FinalizeVideoImportUseCase
  · SHA-256 (if enabled)                 → attachHash; content-duplicate check
  · poster frame at the configured offset → generated into app-private storage
  · status IMPORTING → READY             → activity: THUMBNAIL_CHANGED
```

The UI never blocks on decoding or hashing, and the heavy work is interruptible:
if the process dies mid-finalization the worker is retried by WorkManager.

Duplicate handling is an explicit three-way choice, because only the operator
knows whether two identical-looking files are the same title:

* **Skip** — nothing is written.
* **Keep both** — the guard is bypassed deliberately (`allowDuplicate = true`)
  and the new file gets its own permanent ID.
* **Replace** — `ReplaceVideoUseCase` keeps the existing permanent ID and queues
  the new media; the mapping is rewritten only after the new upload commits.

## 5. Upload flow

```
QueueUploadUseCase                UploadQueueWorker (single named work chain)
  · channel must exist, be        1. reclaim in-flight rows older than the grace
    enabled and grant posting        window (process death recovery)
  · one active task per video     2. requeue RETRYING rows whose backoff elapsed
  · enqueue(QUEUED)               3. while budget allows: nextQueued() → runTask
  · status READY → UPLOADING         · claim QUEUED → PREPARING, attempt++
  · activity: UPLOAD_QUEUED          · re-read the row: a cancel may have landed
  · schedule worker                  · promote to foreground (dataSync FGS,
                                        best-effort; falls back to background)
                                     · TelegramUploadExecutor.upload(job): Flow of
                                       UploadProgressEvent
                                         – Transferring(bytes) → persist counters,
                                           throttle notification to 1/s
                                         – Completed(receipt)  → commitReceipt
                                         – Failed(error)       → onFailure
                                     · a watcher polls the row so an operator
                                       cancel reaches the in-flight transfer
```

Key properties:

* **Real progress only.** `bytesUploaded` comes from TDLib `updateFile` events;
  percentages exist only when the total size is known, otherwise the UI shows
  bytes transferred and leaves the bar indeterminate. Rates and ETAs are derived
  by `TransferRateEstimator`, which reports `0 B/s` when the last two samples are
  identical (a stall is shown as a stall, never as stale progress).
* **No pause.** TDLib cannot suspend a partially sent file. The honest controls
  are *cancel* and *requeue*, and the UI says so.
* **Cancellation wins races.** If a cancel lands while the transfer completes,
  the receipt is deliberately *not* committed; reconciliation later reports the
  unmanaged Telegram item rather than the app inventing a mapping.
* **Bounded backoff.** `RetryPolicy` (5 attempts, 30 s base, 1 h ceiling)
  computes the next delay; exhausted attempts become `FAILED` with the real
  `AppError.userMessage` stored in `lastError`. Rate-limit errors respect
  Telegram's `retry_after`.
* **Crash safety.** Nothing about an upload lives in memory: the queue, the
  attempt counter and the byte counters are Room rows. Rotation, backgrounding,
  process death and network loss are all just "the next run continues".

## 6. Reconciliation

`ReconcileUseCase` (run by `ReconciliationWorker`, on schedule and on demand):

1. For every recorded mapping: `TelegramMediaRepository.verifyMessage(channelId,
   messageId)` → `ACTIVE` (found and matching), `STALE` (media changed) or
   `REMOTE_DELETED` (message gone), each written to the activity log.
2. Optionally scan the most recent N messages of the default channel and report
   media the catalog does not know about (`UNMANAGED_TELEGRAM_MEDIA_FOUND`).

It **never deletes anything automatically** — it produces facts and lets the
operator act.

## 7. TDLib integration

* TDLib 1.8.67 is vendored at `third_party/tdlib` and compiled by
  `scripts/build-tdlib.sh` into `libtdjson.so` for `arm64-v8a` and
  `armeabi-v7a` (`x86_64` optional). The checked-in C shim is compiled beside it
  as `libtdjson_bridge.so`; no prebuilt binaries are committed.
* The **JSON interface** is used, reached through one JNI class
  (`TdJsonJni`, loading `libtdjson.so` before `libtdjson_bridge.so`) with methods
  to create the
  client, send a request, receive results on a dedicated thread.
* `TdClientCore` owns the receive loop, request/response correlation and update
  dispatch. Feature code never sees JSON.
* `TelegramClient`, `TelegramAuthManager`, `TelegramChannelRepository`,
  `TelegramMediaRepository`, `TelegramUploadExecutor` are `domain` interfaces;
  `telegram` provides the implementations and `data`/DI binds them.
* TDLib's own database is encrypted with a key from the Keystore-backed
  `SecretStore` (`TdlibEncryptionKeyProvider`).
* Errors from TDLib are mapped into `AppError` (`ErrorMapper`), so rate limits,
  permission problems and file errors surface as user-readable messages.

## 8. UI architecture

* **MVVM.** Each feature has a `*ViewModel` exposing one immutable
  `StateFlow<…UiState>` and `on*` intent handlers. Screens are stateless
  composables that take state + callbacks; the `*Route` composable connects them
  to Hilt and to navigation.
* **Features know nothing about navigation.** Routes take lambdas
  (`onOpenVideo`, `onFinished`, …); only `app/navigation/McNavHost.kt` wires them
  to destinations.
* **Onboarding gate.** The start destination is derived from real state
  (credentials configured → authorization ready → default channel exists), not
  from a "first launch" flag, so signing out returns the operator to onboarding.
* **App lock gate.** Composed above the NavHost: while locked, no catalog data is
  composed at all.
* **Adaptive layout.** `McWindowClass` (own implementation, no extra artifact)
  switches between a bottom navigation bar and a navigation rail, and drives the
  library grid column count from real available width.
* **Honesty rules in the UI.** Unknown values render `—`; status is always a word
  *and* an icon; destructive Telegram operations require a confirmation dialog
  with an explicit acknowledgement line; secrets are masked with a reveal toggle.
* **Accessibility.** 48 dp minimum touch targets, content descriptions on every
  icon action, reduced-motion support (system animator scale = 0 collapses
  animation), no colour-only signalling.

## 9. Errors

`AppError` is a sealed hierarchy (Telegram auth/connection/permission/rate
limit/file/message, upload failed/cancelled, storage, database, invalid media,
media unavailable, credential validation, channel not found, catalog
import/export, not found, validation). Each carries a technical `message` for the
diagnostics log and a `userMessage` for the UI. ViewModels map failures with
`(t as? AppError)?.userMessage ?: "<specific fallback>"`, so a raw SQLite or
TDLib exception is never shown to an operator.

## 10. Concurrency

* All IO runs on `Dispatchers.IO` inside repositories, workers and the SAF
  document store; ViewModels stay on the main dispatcher.
* Flows are collected in `viewModelScope`; `flatMapLatest` is used where a query
  change must cancel the previous stream (library filters).
* `CancellationException` is always rethrown.
* The upload queue is a **single** uniquely-named work chain
  (`APPEND_OR_REPLACE`), so wake-ups cannot pile up and a completion wake-up is
  never lost.
* `TransferRateEstimator` is confined to one collector (documented as
  not thread-safe).

## 11. Testing strategy

| Layer | What runs | Where |
| --- | --- | --- |
| `domain` | ID allocation/parsing, retry policy math, upload state transitions, credential validation, catalog backup serialization | `domain/src/test` (pure JVM) |
| `core:common` | Formatters (locale-fixed), relative dates, transfer-rate estimator incl. stall and rewind behaviour | `core/common/src/test` |
| `core:database` | Migration chain contiguity | `core/database/src/test` |
| All modules | Syntax verification with the real Kotlin front-end, structure/dependency/Hilt/policy checks | `tools/verify` |
| Android | Instrumented Room, integration and Compose UI tests | `src/androidTest` (Gradle, needs the SDK) |

Only tests may generate sample data. Production code contains no fixtures, no
demo catalogs and no synthetic progress.

## 12. Non-goals (v1)

* No video playback, no player UI, no casting.
* No Streamer app code — Master Control only produces the catalog it consumes.
* No proxying, CDN, transcoding server or any backend of our own.
* No automatic deletion of Telegram media; nothing is removed remotely without an
  acknowledged operator action.
* No multi-account and no per-channel credentials.
* No Telegram branding, no imitation of the official client.
