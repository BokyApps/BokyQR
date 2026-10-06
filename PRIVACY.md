# BokyQR privacy policy

BokyQR is an offline QR scanner. This document is the plain-language statement of what it does
with your data.

**Canonical hosted URL:** https://bokyapps.github.io/privacy/

That page is the shared BokyApps privacy policy (covering all Boky apps, with a BokyQR-specific
section). Keep this file and the hosted page aligned. Use the URL above in Play Console and
F-Droid metadata.

Last updated: 5 October 2026.

## The short version

BokyQR scans, it does not browse. The camera is the only thing that happens by default, and
nothing it sees is stored, uploaded or logged. The only way any data leaves the device is if you
tap a reputation check, and each one tells you exactly what it is about to send before it sends
it.

## Camera frames stay on the device

* Camera frames are decoded by a barcode model running **on the device**. The `play` flavor ships
  that model inside the APK (bundled ML Kit); the `fdroid` flavor uses ZXing, which is pure
  computation.
* No frame, thumbnail, cropped code or decoded payload is written to disk, sent over the network,
  or handed to any analytics or crash-reporting service. **The app contains no analytics and no
  crash reporter of any kind.**
* Nothing is recorded in the provider logs either: `Http` never logs a request URL, header, body
  or response body.

## Photo picker

* If you turn on "Import QR from gallery", BokyQR opens the **Android system photo picker** and
  reads only the single image you pick.
* The app requests **no** photo, media or storage permission of any kind. It never sees the rest
  of your library and cannot browse it.
* The image is decoded in memory, used once, and released. It is not copied anywhere.
* Turn the setting off and the gallery button disappears; the app still cannot open the picker.

## Your API keys

* BokyQR **ships no key**: no VirusTotal key, no urlscan.io key, no URLhaus Auth-Key, no shared
  key, no proxy, no demo key. Reputation checks do not run without a key you supply yourself.
* Keys you paste in Settings are stored in `EncryptedSharedPreferences`, encrypted with an
  `AES256_GCM` master key held in the **Android Keystore**. The app cannot read them back without
  that key, and neither can anyone with a copy of the data directory.
* `android:allowBackup` is `false`, and the encrypted file is explicitly excluded from cloud
  backup and device-to-device transfer. **A key never leaves the device through a backup.**
* A key is written only to the one provider it belongs to, over HTTPS, at the moment you tap that
  provider. It is never sent to any other host, and never logged.
* Clearing the field clears the key. Uninstalling the app removes it.

## What happens when you tap a reputation check

None of this is automatic. A check does not run on scan, on app start, or on a schedule. It runs
when you tap its button, and the first time you do, a dialog states what is about to be sent.

When you tap one of the three providers, the following is transmitted:

| Provider | Sent | Your key used as | What it does with it |
| --- | --- | --- | --- |
| **VirusTotal** | the full scanned URL | `x-apikey` header | Looks the URL up in its report store; if unknown, submits it for analysis and polls for the verdict. |
| **urlscan.io** | the full scanned URL | `API-Key` header | Submits the URL to be fetched by **urlscan's own servers** in a sandbox, always with `visibility: unlisted`. |
| **URLhaus** | the full scanned URL | `Auth-Key` header | Asks whether this exact URL is already on the URLhaus malware blocklist. It is a lookup, not a scan. |

Consequences worth stating plainly:

* **The full URL leaves your phone.** If the code contains a token, an identifier or a
  share-link path, that goes with it.
* **Your provider key is sent to that provider**, and to nowhere else. The three are separate keys
  and are never cross-sent.
* **urlscan.io fetches the URL from its own infrastructure**, not from your phone, and keeps the
  result on its servers according to its own terms and retention policy. Scans are submitted as
  `unlisted`, never `public`, but unlisted scans are still visible to urlscan Pro researchers.
* **URLhaus and VirusTotal results are shared with their operators**, and a URL you submit to
  URLhaus becomes part of a blocklist others download.
* All three calls are **HTTPS**, so the URL and key are encrypted in transit.

### Things BokyQR never does with these checks

* It never fetches the scanned URL itself. `Http` only ever talks to the three provider API hosts.
* It never sends anything about another URL, your history, your device, or your account.
* It never sends anything automatically, in the background, on a timer, or at app start.
* It never stores the URL, a scan id, or any provider response. Provider results live in memory
  for as long as the result sheet is open and are gone when you dismiss it.

## What is stored on the device

| What | Where | Lifetime |
| --- | --- | --- |
| Your provider keys | `EncryptedSharedPreferences`, Keystore-encrypted | Until you clear the field or uninstall |
| "Gallery import on/off" | plain `SharedPreferences` | Until you change it |
| "I accepted the disclosure for provider X" | plain `SharedPreferences` | Until you uninstall |

The disclosure flags record *that* you accepted a dialog. They never record the URL that prompted
it, so re-opening the app cannot tell anyone what you scanned.

There is no database, no cache directory, no log file and no analytics store.

## Network and transport security

* Cleartext HTTP is refused outright (`android:usesCleartextTraffic="false"` plus a network
  security config that permits no cleartext).
* Release builds trust **system certificate authorities only**. No certificate pinning is used.
* **Debug builds additionally trust user-installed CAs** so a proxy can inspect traffic during
  development. Debug builds are for development only and are never distributed or uploaded. The
  shipped configuration is asserted at build time: a release build fails if the trusted-anchors
  file ever gains a user anchor or permits cleartext.

## Permissions

| Permission | Why | When |
| --- | --- | --- |
| `CAMERA` | To scan a QR code. | Requested once on first launch. Nothing else uses it. |
| `INTERNET` | To reach the three reputation APIs you tap. | Only used while a check you started is running. |

No photo, media, storage, location, contacts, microphone, IMEI or advertising identifier is
requested. No identifier of any kind is collected.

## Screen capture

The Settings screen and the result sheet set `FLAG_SECURE`, so API keys and scanned payloads do
not appear in screenshots, screen recordings or the recent-apps thumbnail.

## Children

BokyQR is not directed at children and collects nothing from anyone, of any age.

## Third parties

BokyQR has no advertising, no attribution, no analytics and no tracking SDKs. The only third
parties that can ever see anything are the three reputation providers above, and only after you
tap one. See also [DATA_SAFETY.md](DATA_SAFETY.md) for the matching Play Console declaration.

## Changes and contact

This document changes with the app. The source, and its full history, is in the repository; the
app's source is the authoritative statement of what it does. The canonical public copy is
https://bokyapps.github.io/privacy/.