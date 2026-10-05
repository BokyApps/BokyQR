# BokyQR

A QR scanner for Android that treats a scanned code as data, never as an instruction.

Point the camera at a QR code, get the payload back, and read it. Only two things are ever
launched, and both are ones you tap deliberately. Everything else is shown to you as inert text
with a copy button.

## Bring your own key

**No key ships with this app.** Reputation lookups (VirusTotal, urlscan.io, URLhaus) are
performed with a key you supply yourself and store on the device. There is no built-in key, no
fallback key, no shared key, and no key in any build artifact. Without your own key, lookups
simply do not run; scanning still works, because scanning never needs the network.

The app talks to the network only when you tap a provider. It never fetches the scanned URL
itself, and it never logs a request body, a header or a response, because those carry your key
and the URL you scanned.

## What is opened

* An **http/https URL** that passes every check in `PayloadPolicy` (scheme, length, control
  characters, whitespace, backslash train, percent-encoded control characters, URI syntax,
  opaque URIs, userinfo, and a plausible DNS/IP host) is offered as "Open in browser". It becomes
  a plain `ACTION_VIEW` + `CATEGORY_BROWSABLE` implicit intent, resolved by the device to your
  default browser. Not a WebView, not Custom Tabs.
* A **passkey QR** (`FIDO:/` followed by 20 to 2048 digits, exactly) is handed to whatever
  already handles the `FIDO` scheme on the device. BokyQR does not implement CTAP, BLE or the
  hybrid tunnel; it just hands over the URI, or tells you what to do instead.

Nothing is ever launched automatically. Every launch requires a tap.

## What is never launched

`Handoff` is the only place in the app that builds an intent, and it enforces `PayloadPolicy`
itself rather than trusting its caller: it classifies the string again and returns `false`
without building an intent unless the decision is exactly the expected variant. Only the value
carried by the decision is opened, never the raw argument.

So none of these are ever launched: `intent:`, `javascript:`, `file:`, `data:`, `content:`,
`android-app:`, `market:`, `tel:`, `geo:`, `mailto:`, `sms:`, `WIFI:`, vCards, plain text, or
anything with a payload that is merely *close* to a URL. A URL carrying `user:password@` is
refused as well, because that form exists to confuse the person reading it. A host is also shown
in both its Unicode and punycode forms when they differ, because that pair is what you need in
order to spot `xn--80ak6aa92e.com` pretending to be something else.

There is no `Intent.parseUri`, no `setPackage`, no explicit component, no selector, and no
extras copied out of scanned content, anywhere in the app.

## Flavors

| Flavor | Scanner | Google runtime |
| --- | --- | --- |
| `fdroid` | ZXing only | None. No ML Kit, no Play Services, no Firebase. |
| `play` | ML Kit, **on-device barcode model bundled in the APK** | `play-services-tasks` only. **No Firebase. No ClearCut transport (datatransport).** |

The `play` flavor's ML Kit dependency is excluded from `com.google.firebase` and
`com.google.android.datatransport`, and the `verifyPlayClasspath` task fails the build if either
group ever reappears on a play runtime classpath. The merged `ACCESS_NETWORK_STATE` permission
that datatransport contributed is removed from the merged manifest rather than shipped.

`com.google.android.gms` is **not** excluded wholesale: the play decoder uses
`com.google.android.gms.tasks.Tasks`, and `play-services-tasks` is the Task API, not the Play
Services runtime.

The `fdroid` flavor has its own mirror-image check, `verifyFdroidClasspath`, which fails the
build if ML Kit, Play Services or Firebase appears on an fdroid runtime classpath.

## Certificates: what is trusted

* **Debug APKs trust user-installed CAs**, so that traffic can be inspected with a proxy during
  development. This makes them easy to intercept and easy to impersonate: **debug APKs must
  never be shipped, distributed or uploaded anywhere.**
* **Release builds trust system CAs only.** Cleartext traffic is refused entirely. There is no
  certificate pinning.

## Building

```
./gradlew :core:test :app:assembleFdroidDebug :app:assemblePlayDebug
```

with `ANDROID_HOME` set to an Android SDK that has at least one platform installed.

`:core` is pure Kotlin/JVM and holds the whole payload policy, so `:core:test` runs on a
workstation with no Android SDK at all. `:app` is only configured when an SDK with an installed
platform is actually found.

Note that an `sdk.dir` in `local.properties` is **ignored when that directory has no
`platforms`** subdirectory holding an installed platform. A blank, missing or container-only
`sdk.dir` is skipped, and the next candidate (`ANDROID_HOME`, then `ANDROID_SDK_ROOT`) is tried
instead. If `:app` is missing from the project, that is why.

## License

Apache-2.0. See [LICENSE](LICENSE). Copyright 2026 Sarel Myburgh.

BokyQR is not affiliated with or endorsed by Google, ML Kit, VirusTotal, urlscan.io or URLhaus.
There is no store listing; build it yourself.
