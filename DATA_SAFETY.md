# Play Console Data safety — paste note

This is the answer to the Play Console **Data safety** form for BokyQR
(`rocks.myburgh.bokyqr`, versionCode 1). It is a working note for whoever fills the form in, not
a public document: the form asks a fixed sequence of questions and the answers below are given in
that order, with the reasoning where the answer is not obvious.

The matching plain-language policy is [PRIVACY.md](PRIVACY.md).

**Before submitting:** Play requires a hosted privacy-policy URL in the listing. BokyQR does not
have one yet — see the note at the end. Do not enter a URL that does not resolve.

---

## 1. Does your app collect or share any of the required user data types?

**Yes.**

Not because BokyQR itself gathers anything, but because the three optional reputation lookups
transmit the scanned URL and the user's own API key to a third-party server. That is data
collection and data sharing under Play's definitions, even though it only happens after a tap and
even though the URL is not the user's account data — it is web-browsing content the user scanned.

Two answers are required per data type: *collected*, *shared*, or both; and *optional or required
for the app to function*.

## 2. Which data types?

| Data type | Collected? | Shared? | Optional or required? | Encrypted in transit? | Purpose |
| --- | --- | --- | --- | --- | --- |
| **Web browsing** (the scanned URL) | Yes | Yes | **Optional** — only if the user runs a reputation check | Yes | App functionality, security: a malware reputation verdict for a URL the user chose to check. Without it the app is a scanner and nothing else. |
| **Other personal info / credentials** (the user's own VirusTotal, urlscan.io or URLhaus API key) | No | Yes | **Optional** — only if the user pastes a key | Yes | App functionality: authenticating the user's request to that provider. |
| Photos and videos | No | No | — | — | Images picked through the photo picker are decoded on-device and never stored or transmitted. |
| Location, personal info, financial info, health, messages, contacts, files, app activity, device or app IDs, audio | No | No | — | — | Not collected, not shared. The app requests no identifier of any kind. |

### Why "web browsing" and not "other user content"

Play's *Web browsing* covers "the user's web browsing history or the URLs they visit". A QR code
that encodes a URL is exactly a URL the user is being asked to visit, and that URL is what is
transmitted. It is the closest and most conservative mapping; declaring it as something narrower
would misrepresent what leaves the device.

## 3. Are the data types encrypted in transit?

**Yes, for everything that leaves the device.**

All three provider calls are HTTPS, and the app refuses cleartext outright
(`android:usesCleartextTraffic="false"` plus a network security config with
`cleartextTrafficPermitted="false"`). Release builds trust system certificate authorities only and
additionally carry `FLAG_SECURE` on the two screens that show keys or payloads.

## 4. Do you provide a way for users to request that their data is deleted?

**Not applicable, but the answer if asked is yes and it is already automatic.**

BokyQR stores nothing on a server. The scanned URL, the provider responses and the scan ids exist
only in memory for as long as the result sheet is open and are discarded when it is dismissed.
Clearing a key in Settings deletes it; uninstalling the app removes all of its local storage. A
one-tap "delete" button would be a control over data the app does not have.

## 5. Do you collect or share data for any of the following purposes?

All **No**:

- Analytics
- Advertising or marketing
- Developer communications
- Personalisation
- Product personalisation
- Fraud prevention and security

Fraud prevention is worth a note: the URLhaus and VirusTotal verdicts the user asks for *are*
malware checks, but they are shown to the user on their screen, not used to build a risk model,
scored, or shared with anyone for prevention purposes.

## 6. Is all of the user data collected or shared by your app encrypted in transit?

**Yes** — see section 3.

## 7. Do you provide a way for users to delete their data, and can they request it?

Data is not retained, so there is nothing to request. See section 4.

## 8. Data types collected or shared that are not listed above

**None.**

## 9. Is this app a "news" app, a "health" app, a "finance" app or a "kids" app?

**No** to all four.

## 10. Summary declarations to paste

- Data collected: web browsing URLs; user-provided API credentials (shared, not collected)
- Data shared: web browsing URLs; user-provided API credentials
- Collection optional: **yes**, the app is fully functional with every reputation check disabled
- Encrypted in transit: **yes**
- Shared with third parties: **yes** — VirusTotal, urlscan.io and URLhaus, only after the user taps
  the corresponding button
- Purpose: **app functionality / security** (user-initiated malware reputation lookups)
- Analytics: **no**. Advertising: **no**. Tracking: **no**.

## 11. Accompanying disclosures and limitations

State these in the listing's Data safety section, or wherever Play asks for detail:

- Sharing happens **only on user action**. Nothing is sent on scan, on app start, or on a schedule.
- Each provider requires **its own** API key, supplied by the user. The app ships none and has no
  shared, fallback, or proxied key.
- urlscan.io **submits the URL to be fetched by urlscan's own servers**, always with
  `visibility: unlisted`. Unlisted scans remain visible to urlscan Pro researchers and are retained
  under urlscan's own policy. This is the single most important retention disclosure in this form.
- URLhaus lookups add the URL to a blocklist that others download.
- The app **never fetches the scanned URL itself** from the device. `Http` only ever connects to
  the three provider API hosts.
- `INTERNET` is the only network permission. `ACCESS_NETWORK_STATE`, which ML Kit's transport
  library would otherwise merge in, is explicitly removed from the manifest.

## 12. Before you submit

- [ ] PRIVACY.md is hosted at a public, stable URL and that URL is in the listing.
- [ ] The `play` flavor is uploaded, never `fdroid`. The F-Droid build is ZXing-only and free of
      Google libraries; the Play build bundles ML Kit's on-device barcode model.
- [ ] `android:hasFragileUserData="false"` is **not** claimed — the app has no such dependency.
- [ ] Nothing in this file needs updating for an SDK bump; re-read it if a provider flow changes.