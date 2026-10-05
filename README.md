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

A URL whose host names something on *this* device or *its* network is not openable either:
`localhost` and `*.localhost`, `.local` and `.home.arpa` names, single-label intranet hosts
(`http://nas/`), the RFC 1918 / CGNAT / link-local / IETF-reserved IPv4 ranges — including the
cloud metadata endpoint `169.254.169.254` — and the IPv6 equivalents (`::1`, `fc00::/7`,
`fe80::/10`, and IPv4-mapped forms of any of those). Those addresses aim at the device or the
router, not at a website, and a reputation lookup would cheerfully call a router admin page
harmless. The payload is shown as text with its copy button instead, so you can still do whatever
you meant by hand. The rule lives in `PayloadPolicy` and is covered by `ClassifyPrivateHostTest`.

## Flavors

| Flavor | Scanner | Google runtime |
| --- | --- | --- |
| `fdroid` | ZXing only | None. No ML Kit, no Play Services, no Firebase. |
| `play` | ML Kit, **on-device barcode model bundled in the APK** | `play-services-tasks` only. **No Firebase. No ClearCut transport (datatransport).** |

### What the play flavor excludes, and why it is targeted

ML Kit's POM (`com.google.mlkit:common:18.11.0` and `com.google.mlkit:vision-common:17.3.0`)
declares compile-scope dependencies on `com.google.firebase:firebase-components`,
`firebase-encoders`, `firebase-encoders-json` and on `com.google.android.datatransport:transport-api`
and `transport-runtime`. **Those are SPI, not telemetry**: ML Kit links against them at runtime,
and excluding them produced exactly the failure the dependency checks cannot see — a
`NoClassDefFoundError` at `BarcodeScanning.getClient()` on a device, while the build stayed green.
An earlier version of this file excluded `com.google.firebase` wholesale; that trade has been
reversed.

What is excluded is one module: **`com.google.android.datatransport:transport-backend-cct`**, the
ClearCut uploader, its `AlarmManager`/`JobScheduler` wake-ups, and the `ACCESS_NETWORK_STATE`
permission it contributes to the merged manifest. With no backend registered, `transport-runtime`
has no uploader to hand a payload to, so ML Kit's logging has nowhere to go and is dropped.

`com.google.android.gms` is **not** excluded wholesale: the play decoder uses
`com.google.android.gms.tasks.Tasks`, and `play-services-tasks` is the Task API, not the Play
Services runtime. The merged `ACCESS_NETWORK_STATE` permission is removed from the merged
manifest rather than shipped.

### The guards, and what they actually check

* **`verifyPlayClasspath`** is an **allow-list**, not a deny-list. Every component on a play
  runtime classpath whose group is `com.google.firebase`, `com.google.android.datatransport` or
  `com.google.android.gms` must appear in `ALLOWED_PLAY_SPI` in `app/build.gradle.kts`, or the
  build fails. That is strictly stronger than the check it replaced: a deny-list only notices the
  modules it already knew to forbid, while this one fails on a new datatransport backend, a new
  Firebase SDK or an analytics client appearing in ML Kit's POM. Each entry carries a comment
  saying what it is; do not widen the list without one.
* **`verifyFdroidClasspath`** is the mirror image and fails if ML Kit, Play Services or Firebase
  appears on an fdroid runtime classpath.
* **`verifyReleaseTrustAnchors`** fails if the shipped `network_security_config.xml` ever gains a
  user certificate anchor or permits cleartext.

All three run on `check` and before the matching build tasks.

## F-Droid

**F-Droid builds `assembleFdroidRelease` only. Never the `play` flavor.** The `play` flavor
depends on proprietary ML Kit and on `play-services-tasks`, which is NonFreeDep; the `fdroid`
flavor is ZXing alone and is what the store listing describes.

* `./gradlew :app:assembleFdroidRelease` — the artifact to submit.
* `./gradlew :app:assemblePlayDebug` — a local smoke test of the ML Kit path. Do not submit it.
* Store metadata lives in [`fastlane/metadata/android/en-US/`](fastlane/metadata/android/en-US)
  (`title.txt`, `short_description.txt`, `full_description.txt`, and `changelogs/1.txt` keyed to
  `versionCode = 1`). F-Droid supplies the icon from the build; screenshots are not committed.
* `verifyFdroidClasspath` runs on `check`, so a build that has picked up ML Kit by accident fails
  before it is ever offered for review.

### Anti-feature stance: `NonFreeNet`

The three reputation providers are optional HTTPS APIs that require the user to bring their own
key, and **the app makes no network request at all unless the user taps one of them**. There is no
analytics, no crash reporter, no tracking and no automatic network of scanned content.

The F-Droid reviewer may still want `AntiFeature:NonFreeNet` declared, because the app is capable
of network access to a proprietary third-party service. The honest position is that this is a
reviewer judgement rather than a property of the code: the network path is gated behind a
per-provider disclosure, requires a credential the developer does not have and does not want, and
never runs on its own. If the reviewer asks for it, add it — the anti-feature is metadata, and
declaring it costs nothing but a line in the listing. It is recorded here rather than baked into
`fastlane/metadata` so the decision stays visible and stays reversible.

`AntiFeature:Tracking` should **not** be declared: nothing in the app is used to track the user,
with or without a provider.

## Certificates: what is trusted

* **Debug APKs trust user-installed CAs**, so that traffic can be inspected with a proxy during
  development. This makes them easy to intercept and easy to impersonate: **debug APKs must
  never be shipped, distributed or uploaded anywhere.**
* **Release builds trust system CAs only.** Cleartext traffic is refused entirely. There is no
  certificate pinning.
* The debug override lives in `app/src/debug/res/xml/network_security_config.xml` and the shipped
  one in `app/src/main/res/xml/network_security_config.xml`. `verifyReleaseTrustAnchors` fails the
  build if the file that ships ever gains a user anchor or permits cleartext, so the debug
  behaviour cannot quietly leak into a release. If you ever see this task fail, someone edited the
  wrong file — do not add the user anchor to `src/main`.
* The settings screen and the result sheet set `FLAG_SECURE`, so API keys and scanned payloads
  stay out of screenshots, screen recordings and the recent-apps thumbnail.

## Building

```
./gradlew :core:test :app:assembleFdroidDebug :app:assemblePlayDebug
```

with `ANDROID_HOME` set to an Android SDK that has at least one platform installed. The project
compiles against **API 36** (`compileSdk`/`targetSdk = 36`), so the SDK needs `platforms/android-36`;
`minSdk` is 26 and is unchanged.

`:core` is pure Kotlin/JVM and holds the whole payload policy, so `:core:test` runs on a
workstation with no Android SDK at all. `:app` is only configured when an SDK with an installed
platform is actually found.

### If `:app` is missing from the project

`:app` is conditionally included. A candidate SDK only counts if its `platforms/` directory holds
at least one installed `android-*` directory, so a blank, missing or container-only `sdk.dir` is
skipped and the next candidate is tried. Candidates are probed in this order:

1. `sdk.dir` in `local.properties`
2. `ANDROID_HOME`
3. `ANDROID_SDK_ROOT`

When none of them qualifies, `:app` is omitted and every `./gradlew :app:...` invocation fails with
`Project with path ':app' could not be found`, which says nothing useful. The configuration prints
the real reason at configuration time instead — every path that was probed, and exactly why each
one was rejected:

```
  BokyQR: :app was NOT configured for this invocation.
  Reason: no Android SDK with an installed platform was found.

  Paths probed, in order:
    - local.properties sdk.dir: /opt/android-sdk -> exists, but /opt/android-sdk/platforms holds
      no installed platform (an SDK with no platforms/android-* cannot compile anything)
    - ANDROID_HOME: <unset> -> not set
    ...
```

Install an SDK with at least one `platforms/android-*` directory, set `sdk.dir` in
`local.properties` (or export `ANDROID_HOME`), and re-run. `local.properties` is gitignored and is
never committed.

`:core:test` still works with no SDK at all, which is the point of keeping `:core` separable.

## Releasing

Release signing reads its configuration from the environment first (CI) and `local.properties`
second (workstation). Both sources are gitignored, and so are `*.jks`, `*.keystore` and `*.p12`,
so no key material or password can reach the repository.

| Environment variable | `local.properties` key | Meaning |
| --- | --- | --- |
| `BOKYQR_STORE_FILE` | `storeFile` | Path to the keystore. Relative paths resolve against the repository root. |
| `BOKYQR_STORE_PASSWORD` | `storePassword` | Keystore password |
| `BOKYQR_KEY_ALIAS` | `keyAlias` | Key alias |
| `BOKYQR_KEY_PASSWORD` | `keyPassword` | Key password |

On a workstation, put them in `local.properties`:

```properties
sdk.dir=/path/to/android-sdk
storeFile=bokyqr-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

In CI, export the `BOKYQR_*` variables from your secret store instead.

```bash
./gradlew :app:bundlePlayRelease      # Google Play (ML Kit on-device barcode model)
./gradlew :app:bundleFdroidRelease    # not for submission; F-Droid runs assembleFdroidRelease
```

`buildTypes.release.signingConfig` is wired to the `release` signing config **only when the
keystore file exists and is readable**. With no keystore the release build is simply *unsigned*:
`assembleRelease` and `bundleRelease` still succeed and still exercise R8 and resource shrinking,
which makes them useful local smoke tests, but the resulting AAB is not uploadable and Play
Console will reject it. A build log line is not printed for this case, so check
`apksigner verify --print-certs app/build/outputs/bundle/*/*.aab` before assuming a build is
signed.

You also need **Play App Signing** enrolled: generate the upload keystore you keep in CI, and let
Google hold the app signing key. The keystore referenced above is the one Gradle signs with.

### 16 KB page size (Android 15+)

Play requires apps targeting API 35 and later to support **16 KB memory page sizes**, and the
`play` flavor ships ML Kit's prebuilt native libraries (`libbarhopper_v3.so`,
`libimage_processing_util_jni.so`, `libandroidx.graphics.path.so`). **This is not verified in CI
here**, because verifying it needs the resolved AARs and a device or emulator. Check it by hand
before every release.

```bash
# 1. Build the artifact you intend to upload.
./gradlew :app:bundlePlayRelease

# 2. Check the APK/AAB zip entry alignment (needs build-tools 35+).
$ANDROID_HOME/build-tools/35.0.0/zipalign -c -P 16 -v 4 <the .apk>
#    "Verification succesful" means every entry is 16 KB aligned.

# 3. Check the ELF segment alignment inside each .so. This is the part zipalign
#    does not cover, and it is the part that actually fails: an APK can be
#    perfectly zip-aligned while its native libraries are still 4 KB-aligned.
unzip -o <the .apk> 'lib/*/*.so' -d /tmp/bokyqr-so
for f in /tmp/bokyqr-so/lib/*/*.so; do
  echo "$f"; readelf -lW "$f" | awk '$1 == "LOAD" { print "  align:", $NF }'
done
# Every LOAD segment must report 0x4000 (16384), not 0x1000 (4096).

# Alternatively, in Android Studio: Build > Analyze APK, and the "16 KB alignment"
# row for each .so in the APK Analyzer output.
```

Zip entry alignment passes for the play debug APK. ELF LOAD alignment on **arm64-v8a** and **x86_64** `libbarhopper_v3.so` is 0x4000 (good). **armeabi-v7a** and **x86** barhopper are still 0x1000 — see the ELF steps below before uploading.

since it reports the alignment Play itself will enforce.

The `fdroid` flavor ships no native code of its own beyond what CameraX and Compose contribute, and
no ML Kit `.so` at all.



## Privacy and store metadata

* [`PRIVACY.md`](PRIVACY.md) — camera frames on-device, the photo picker, encrypted key storage,
  and exactly what is sent when a provider is tapped.
* [`DATA_SAFETY.md`](DATA_SAFETY.md) — the matching Play Console Data safety answers, in the order
  the form asks for them.
* [`fastlane/metadata/android/en-US/`](fastlane/metadata/android/en-US) — F-Droid listing.

Both privacy documents deliberately do **not** claim a published privacy-policy URL: no store
listing exists for this app. One must be hosted and linked from the listing before submission.

## License

Apache-2.0. See [LICENSE](LICENSE). Copyright 2026 Sarel Myburgh.

BokyQR is not affiliated with or endorsed by Google, ML Kit, ZXing, VirusTotal, urlscan.io or
URLhaus. There is no store listing yet; build it yourself.
