# Telegram setup

Master Control uses **your own** Telegram application credentials and **your own**
channels. Nothing is shared, hard-coded or proxied. This document explains how to
obtain them and what the app does with them.

---

## 1. Register an application at my.telegram.org

1. Sign in to <https://my.telegram.org> with the phone number of the account that
   will administer the library.
2. Open **API development tools**.
3. Fill in the form:
   * **App title** — something descriptive and honest, e.g. `My Media Library
     Admin`. Do not use "Telegram" in a way that suggests an official client.
   * **Short name** — lowercase alphanumeric, e.g. `mymedialib`.
   * **Platform** — Android.
   * **Description** — optional; state that the app manages a private media
     library through TDLib.
4. Submit. You receive:
   * **App api_id** — an integer, e.g. `1234567`.
   * **App api_hash** — a 32-character hex string.

These two values are what Master Control asks for on first run.

### Rules that matter

* **Keep the api_hash secret.** Anyone holding `api_id` + `api_hash` can act as
  your application. Master Control stores the hash encrypted in the Android
  Keystore, shows it masked, and never logs or exports it.
* **Do not reuse another app's credentials** — in particular not the ones from
  the official Telegram Android client. That violates Telegram's terms and gets
  credentials revoked.
* **One api_id per application.** If you ship a modified build, register a new
  application rather than sharing credentials between unrelated apps.
* Rotating credentials is a config change in the app (Settings → Credentials); no
  rebuild is required. That is the entire point of runtime BYOK.

## 2. Prepare a storage channel

Master Control uploads media into a Telegram **channel** (or supergroup) that the
authenticated account administers.

1. In Telegram, create a **private channel** (recommended) or use an existing one.
   A private channel keeps the media out of public search; the app works with
   public channels too.
2. Add the account you authenticated in Master Control as an **administrator**.
3. Grant at least:
   * **Post messages** — required; without it Master Control refuses to add the
     channel as an upload target.
   * **Edit messages** — used when replacing media.
   * **Delete messages** — used when you explicitly delete a video from Telegram.
4. If the account created the channel it is the **owner**, which implies all
   rights.

Master Control verifies these rights live through TDLib (`VerifyChannelUseCase`)
and shows them per channel, including which rights are missing.

### Channel capacity and limits

* Telegram enforces its own limits on file size, storage and rate. Master Control
  reports what Telegram returns — it does not display a quota it cannot read.
* Very large libraries are better split across several channels: add multiple
  channels and choose the target per upload, or change the default.
* Broadcast channels, broadcast groups and supergroups are all supported
  (`ChannelKind`).

## 3. Authentication states you may meet

The onboarding flow handles every state TDLib reports, and says so honestly when
it cannot proceed:

| State | What Master Control does |
| --- | --- |
| Phone number required | Collects and validates the number, submits it |
| Code required | Collects the Telegram code (SMS/app), tells you where it was sent, offers resend |
| Password required (2FA) | Collects the cloud password for this attempt only |
| Registration required | Reported as unsupported in-app: complete registration in an official Telegram client first |
| Email address / email code required | Reported as unsupported in-app; complete it in an official client |
| Premium purchase required | Reported as unsupported in-app |
| Confirm on another device | Reported with the confirmation link |
| Ready | Proceeds to channel selection |

Codes, passwords and session material are never written to disk by Master Control
beyond TDLib's own encrypted session database.

## 4. Errors and what they mean

| Telegram error | Meaning | What to do |
| --- | --- | --- |
| `FLOOD_WAIT_X` | Rate limited for X seconds | Master Control waits and retries with bounded backoff; do not hammer |
| `CHANNEL_PRIVATE` | The account cannot access that channel | Add the account to the channel |
| `CHAT_ADMIN_REQUIRED` | Missing administrator rights | Grant posting rights |
| `CHAT_WRITE_FORBIDDEN` | Posting is forbidden (banned/muted) | Lift the restriction in Telegram |
| `MESSAGE_TOO_LONG` | Caption exceeds Telegram's limit | Shorten the title/description used in captions |
| `FILE_PART_*_MISSING` | A file part was lost mid-upload | Requeue the upload; the task restarts from the beginning |
| `AUTH_KEY_UNREGISTERED` | The session was terminated | Sign in again in onboarding |
| `PHONE_NUMBER_INVALID` | Number format rejected | Use full international format, e.g. `+491701234567` |
| `API_ID_INVALID` / `API_HASH_INVALID` | Credentials rejected by Telegram | Re-enter the values from my.telegram.org |

All of these surface as readable messages (`AppError.userMessage`); the raw
Telegram error string is kept in the local diagnostics log, never shown as-is.

## 5. What Master Control sends to Telegram

* Authentication requests for your account (phone, code, 2FA password).
* Channel/chat lookups and permission checks for channels you search.
* `uploadFile` requests for the video files you queue, with a caption that can
  include the permanent video ID (Settings → Uploads).
* Message verification, and message deletion **only** when you explicitly choose
  "delete from Telegram".
* Nothing else. There is no analytics, no crash reporting service, no third-party
  endpoint and no server operated by this project.

## 6. Account safety

* Enable 2FA on the Telegram account (Settings → Privacy in Telegram). Master
  Control supports it and asks for the password when Telegram does.
* Use a dedicated account for the library if the media is organizational: the
  session lives on the device running Master Control.
* Review active sessions in Telegram periodically and terminate ones you do not
  recognize.
* If a device is lost: **Settings → Clear every stored secret** removes the API
  credentials and app-lock secret locally, and Telegram session termination should
  be done from an official client.
* Never share your `api_hash`, and never commit `keystore.properties` or
  `local.properties`.

## 7. Terms

Using Telegram's API is governed by Telegram's Terms of Service and the API terms
of service. You are responsible for the content you store in your channels and for
complying with those terms and with the law in your jurisdiction. Master Control
is a tool; it does not license, host or moderate anything for you.
