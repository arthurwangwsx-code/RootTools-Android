# GitHub Android Release

## 1. Repository identity

The Android application is published from `RootTools-Android` so the repository name stays
unambiguous next to the iOS `RootTools` project.

The old generic Android repository can remain as historical compatibility; new Android releases
and tags belong to `RootTools-Android`.

## 2. Release artifact

GitHub Releases publish the complete signed suite plus one SHA-256 sidecar per APK:

```text
RootTools-Android-v<version>.apk
RootTools-Background-Server-v<version>.apk
RootTools-HyperOS-Credential-Fix-v<version>.apk
RootTools-NFC-Lab-v<version>.apk
<each APK>.sha256
```

NFC Lab remains optional and HyperOS Credential Fix remains device-specific. Publishing them with
the suite preserves their existing capabilities without implying that every device should install
every APK.

The release APK uses a persistent Android signing key. The keystore is never committed. A backup
is kept in the operator's private configuration directory and the CI copy is stored only in GitHub
Actions Secrets.

Required repository secrets:

```text
ROOTTOOLS_KEYSTORE_B64
ROOTTOOLS_KEYSTORE_PASSWORD
ROOTTOOLS_KEY_ALIAS
```

The current PKCS#12 keystore uses the store password as the private-key password, which avoids a
second credential that PKCS#12 does not portably support.

The root `build.gradle.kts` applies the signing contract to every Android application module and
reads signing values only from environment variables. Child modules may not define independent
release signing. Ordinary local debug builds therefore do not need release credentials.

## 3. Release workflow

`.github/workflows/release-android.yml` runs for every pushed `v*` tag. It:

1. checks out the exact tag;
2. restores the release key into the runner's temporary directory;
3. runs the repository quality and privileged-security guards;
4. runs the shared privilege Core, app, feature, and companion unit tests plus Android lint;
5. builds all four application modules with `assembleRelease`;
6. verifies every APK signature with `apksigner`;
7. writes a SHA-256 sidecar for every APK;
8. creates or updates the GitHub Release.

Tags containing `-`, for example `v0.4.0-beta.1`, are published as GitHub prereleases.

## 4. Versioning

The suite has one product version. `rootToolsVersionCode` and `rootToolsVersionName` live only in
the root `gradle.properties` and are applied to every Android application module.

Before a new release:

1. increment `versionCode` monotonically;
2. set the intended `versionName` in the same root file;
3. run the local quality/security/test/lint/assemble gate;
4. commit the release-ready source;
5. create a matching `v<versionName>` tag (CI rejects a mismatch);
6. push the commit and tag.

The first complete-suite prerelease uses `0.5.0-beta.1` / versionCode `5`.

## 5. Tester loop

The owner downloads the appropriate APKs from GitHub Releases and installs them over the previous
builds. The main APK is required; NFC Lab is optional; HyperOS Credential Fix is installed only on
matching Xiaomi/HyperOS environments. If a
privileged operation fails, collect the visible action result plus relevant RootTools audit/log
output. Do not delete `/data/adb/tailscale` before diagnosis because its bounded logs and preserved
Tailscale state are useful evidence.

For Root Tailscale specifically, useful evidence includes:

- Root Tailscale screen screenshot/state;
- current Android VPN owner;
- runtime version and mode;
- whether `tailscale0` exists;
- tailnet IPv4;
- route readiness;
- ADB `:5555` listener state;
- the most recent RootTools audit result.

No password, Tailscale credential, GitHub signing secret, or private key should be included in a
bug report.
